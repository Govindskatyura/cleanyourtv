package io.github.govindskatyura.cleanhome;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;

/** One app: its tile and, on the pages, its name underneath. Draws itself; no child views. */
@SuppressLint("ViewConstructor")
final class AppIconView extends View {
    interface Host {
        boolean isEditing();

        void onAppClick(AppIconView v);

        void onAppLongPress(AppIconView v);

        void onRemoveClick(AppIconView v);

        void onDragStart(AppIconView v, MotionEvent e);
    }

    final boolean showLabel;
    App app;
    /** Where the container is moving this view to, in the container's unscrolled coordinates. */
    float targetX, targetY;
    boolean placed;
    boolean longPressEnabled = true;

    private final Metrics m;
    private final Host host;
    private final int touchSlop;
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint pressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF iconRect = new RectF();
    private final Runnable longPress;
    private CharSequence shownLabel = "";
    private boolean pressedLook, longPressed, moved, dragging, onBadge;
    private float downX, downY;
    private ObjectAnimator wiggle;

    AppIconView(Context c, Metrics m, Host host, boolean showLabel) {
        super(c);
        this.m = m;
        this.host = host;
        this.showLabel = showLabel;
        touchSlop = ViewConfiguration.get(c).getScaledTouchSlop();
        // iOS darkens a tile while your finger is on it.
        pressedPaint.setColorFilter(new PorterDuffColorFilter(0x59000000, PorterDuff.Mode.SRC_ATOP));
        badgePaint.setStrokeCap(Paint.Cap.ROUND);
        longPress = () -> {
            longPressed = true;
            setPressedLook(false);
            ViewParent parent = getParent();
            if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            host.onAppLongPress(this);
        };
    }

    void setApp(App a) {
        app = a;
        setContentDescription(a.label);
        updateLabel();
        invalidate();
    }

    float iconLeft() {
        float w = getWidth() > 0 ? getWidth() : m.cellW;
        return (w - m.iconSize) / 2f;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), m.contentHeight(showLabel));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        setPivotX(w / 2f);
        setPivotY(m.topPad + m.iconSize / 2f);
        updateLabel();
    }

    private void updateLabel() {
        if (app == null || !showLabel) return;
        float avail = (getWidth() > 0 ? getWidth() : m.cellW) - 4 * m.dp;
        shownLabel = TextUtils.ellipsize(app.label, m.labelPaint, avail, TextUtils.TruncateAt.END);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (app == null) return;
        float left = iconLeft();
        iconRect.set(left, m.topPad, left + m.iconSize, m.topPad + m.iconSize);
        c.drawBitmap(app.icon, null, iconRect, pressedLook ? pressedPaint : iconPaint);
        if (showLabel) {
            c.drawText(shownLabel, 0, shownLabel.length(), getWidth() / 2f, m.labelBaseline, m.labelPaint);
        }
        // Every app can leave the Home Screen (it stays in the App Library), so every app gets the badge.
        if (host.isEditing()) drawBadge(c, left);
    }

    /** iOS 16's grey "minus" on the tile's top-left corner. */
    private void drawBadge(Canvas c, float left) {
        float r = 11 * m.dp;
        float cx = left + 3 * m.dp;
        float cy = m.topPad + 3 * m.dp;
        badgePaint.setStyle(Paint.Style.FILL);
        badgePaint.setColor(0xF2D1D1D6);
        c.drawCircle(cx, cy, r, badgePaint);
        badgePaint.setColor(0xFF1C1C1E);
        badgePaint.setStrokeWidth(2 * m.dp);
        c.drawLine(cx - 4.5f * m.dp, cy, cx + 4.5f * m.dp, cy, badgePaint);
    }

    private boolean inBadge(float x, float y) {
        float dx = x - (iconLeft() + 3 * m.dp);
        float dy = y - (m.topPad + 3 * m.dp);
        float r = 20 * m.dp;
        return dx * dx + dy * dy < r * r;
    }

    private void setPressedLook(boolean on) {
        if (pressedLook == on) return;
        pressedLook = on;
        invalidate();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (app == null) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                longPressed = false;
                moved = false;
                dragging = false;
                onBadge = host.isEditing() && inBadge(downX, downY);
                if (host.isEditing()) {
                    // In jiggle mode a swipe that starts on an app moves the app, not the page.
                    getParent().requestDisallowInterceptTouchEvent(true);
                } else {
                    setPressedLook(true);
                    if (longPressEnabled) postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                }
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - downX;
                float dy = e.getY() - downY;
                if (!moved && dx * dx + dy * dy > touchSlop * touchSlop) {
                    moved = true;
                    removeCallbacks(longPress);
                    setPressedLook(false);
                    if ((longPressed || host.isEditing()) && !onBadge) {
                        // Hold, then move: iOS closes the menu and starts jiggle mode with this app in hand.
                        dragging = true;
                        getParent().requestDisallowInterceptTouchEvent(false);
                        host.onDragStart(this, e);
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                removeCallbacks(longPress);
                setPressedLook(false);
                if (!moved && !longPressed && !dragging) {
                    if (onBadge) {
                        host.onRemoveClick(this);
                    } else if (!host.isEditing()) {
                        performClick();
                    }
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPress);
                setPressedLook(false);
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        host.onAppClick(this);
        return true;
    }

    /** Jiggle mode: each tile rocks about its centre, out of step with its neighbours. */
    void setWiggle(boolean on) {
        if (on == (wiggle != null)) {
            invalidate();
            return;
        }
        if (on) {
            int h = app == null ? 0 : app.key.hashCode();
            float a = (h & 1) == 0 ? 1.4f : -1.4f;
            wiggle = ObjectAnimator.ofFloat(this, View.ROTATION, -a, a);
            wiggle.setDuration(125 + ((h >>> 1) % 30));
            wiggle.setRepeatMode(ValueAnimator.REVERSE);
            wiggle.setRepeatCount(ValueAnimator.INFINITE);
            wiggle.start();
        } else {
            wiggle.cancel();
            wiggle = null;
            setRotation(0);
        }
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(longPress);
        if (wiggle != null) {
            wiggle.cancel();
            wiggle = null;
            setRotation(0);
        }
    }
}
