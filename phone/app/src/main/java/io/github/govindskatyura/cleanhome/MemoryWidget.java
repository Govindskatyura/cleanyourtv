package io.github.govindskatyura.cleanhome;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Environment;
import android.os.StatFs;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.Locale;

/**
 * An iOS medium widget for memory: RAM in use and storage in use, each as a ring with the figures beside it.
 * It reads two system numbers every few seconds, and only while the home screen is in front.
 */
@SuppressLint("ViewConstructor")
final class MemoryWidget extends View {
    interface Host {
        boolean isEditing();

        void onWidgetLongPress();

        void onWidgetRemoveClick();
    }

    private static final long TICK_MS = 3000;
    private static final long DISK_MS = 30000;
    private static final int GREEN = 0xFF34C759;
    private static final int ORANGE = 0xFFFF9500;
    private static final int RED = 0xFFFF3B30;

    /** Room around the card for the edit-mode "minus" badge on its corner. */
    final int pad;
    private final Metrics m;
    private final Host host;
    private final ActivityManager am;
    private final ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
    private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint caption = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint percent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            postDelayed(this, TICK_MS);
        }
    };
    private final Runnable longPress;
    private final String title;
    private final String ramTitle;
    private final String diskTitle;
    private float frameW, frameH;
    private float ramUsed, diskUsed;
    private String ramValue = "", ramTotal = "", ramPercent = "", diskValue = "", diskTotal = "", diskPercent = "";
    private long diskReadAt;
    private float downX, downY;
    private boolean onBadge, moved;
    private ObjectAnimator wiggle;

    MemoryWidget(Context c, Metrics m, Host host) {
        super(c);
        this.m = m;
        this.host = host;
        am = c.getSystemService(ActivityManager.class);
        pad = Math.round(10 * m.dp);
        title = c.getString(R.string.memory);
        ramTitle = c.getString(R.string.ram).toUpperCase(Locale.getDefault());
        diskTitle = c.getString(R.string.storage).toUpperCase(Locale.getDefault());
        boolean night = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        int primary = night ? 0xFFFFFFFF : 0xFF000000;
        int secondary = night ? 0x99EBEBF5 : 0x993C3C43;
        card.setColor(night ? 0xF21C1C1E : 0xF5FFFFFF);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(night ? 0x33FFFFFF : 0x1F000000);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        caption.setColor(secondary);
        caption.setTextSize(sp(c, 12));
        caption.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        caption.setLetterSpacing(0.04f);
        big.setColor(primary);
        big.setTextSize(sp(c, 19));
        big.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        small.setColor(secondary);
        small.setTextSize(sp(c, 12));
        percent.setColor(primary);
        percent.setTextSize(sp(c, 13));
        percent.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        percent.setTextAlign(Paint.Align.CENTER);
        badge.setStrokeCap(Paint.Cap.ROUND);
        longPress = () -> {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            host.onWidgetLongPress();
        };
    }

    private static float sp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, c.getResources().getDisplayMetrics());
    }

    /** The card's size: from the first icon's left edge to the last one's right, from row one's icons to row two's. */
    void setFrame(float w, float h) {
        if (w == frameW && h == frameH) return;
        frameW = w;
        frameH = h;
        requestLayout();
    }

    int viewWidth() {
        return Math.round(frameW) + 2 * pad;
    }

    int viewHeight() {
        return m.topPad + Math.round(frameH) + m.labelGap + m.labelHeight;
    }

    void start() {
        removeCallbacks(tick);
        diskReadAt = 0;
        tick.run();
    }

    void stop() {
        removeCallbacks(tick);
    }

    private void refresh() {
        am.getMemoryInfo(info);
        long total = info.totalMem;
        long used = total - info.availMem;
        ramUsed = total > 0 ? used / (float) total : 0f;
        ramValue = Formatter.formatShortFileSize(getContext(), used);
        ramTotal = getContext().getString(R.string.of_total, Formatter.formatShortFileSize(getContext(), total));
        ramPercent = getContext().getString(R.string.percent, Math.round(ramUsed * 100));
        long now = System.currentTimeMillis();
        if (now - diskReadAt > DISK_MS) {
            diskReadAt = now;
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            long dTotal = fs.getTotalBytes();
            long dUsed = dTotal - fs.getAvailableBytes();
            diskUsed = dTotal > 0 ? dUsed / (float) dTotal : 0f;
            diskValue = Formatter.formatShortFileSize(getContext(), dUsed);
            diskTotal = getContext().getString(R.string.of_total, Formatter.formatShortFileSize(getContext(), dTotal));
            diskPercent = getContext().getString(R.string.percent, Math.round(diskUsed * 100));
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(viewWidth(), viewHeight());
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        setPivotX(w / 2f);
        setPivotY(m.topPad + frameH / 2f);
    }

    @Override
    protected void onDraw(Canvas c) {
        float left = pad;
        float top = m.topPad;
        float radius = 22 * m.dp * (m.iconSize / (60 * m.dp));
        rect.set(left, top, left + frameW, top + frameH);
        c.drawRoundRect(rect, radius, radius, card);
        float half = frameW / 2f;
        drawGauge(c, left, top, half, ramTitle, ramUsed, ramPercent, ramValue, ramTotal);
        drawGauge(c, left + half, top, half, diskTitle, diskUsed, diskPercent, diskValue, diskTotal);
        c.drawText(title, getWidth() / 2f, m.labelBaseline + frameH - m.iconSize, m.labelPaint);
        if (host.isEditing()) {
            float r = 11 * m.dp;
            float cx = left + 6 * m.dp;
            float cy = top + 6 * m.dp;
            badge.setStyle(Paint.Style.FILL);
            badge.setColor(0xF2D1D1D6);
            c.drawCircle(cx, cy, r, badge);
            badge.setColor(0xFF1C1C1E);
            badge.setStrokeWidth(2 * m.dp);
            c.drawLine(cx - 4.5f * m.dp, cy, cx + 4.5f * m.dp, cy, badge);
        }
    }

    /** One half: the caption on top, a ring with the percentage, and the amount used beside it. */
    private void drawGauge(Canvas c, float left, float top, float width, String name, float used, String pct,
            String value, String total) {
        float inset = 16 * m.dp;
        c.drawText(name, left + inset, top + inset - caption.ascent(), caption);
        float captionBottom = top + inset + caption.descent() - caption.ascent();
        float ringSpace = top + frameH - inset - captionBottom;
        float stroke = 7 * m.dp;
        float r = Math.min(ringSpace, width * 0.42f) / 2f - stroke / 2f;
        float cx = left + inset + stroke / 2f + r;
        float cy = captionBottom + ringSpace / 2f + 2 * m.dp;
        track.setStrokeWidth(stroke);
        arc.setStrokeWidth(stroke);
        arc.setColor(used < 0.6f ? GREEN : used < 0.85f ? ORANGE : RED);
        rect.set(cx - r, cy - r, cx + r, cy + r);
        c.drawOval(rect, track);
        c.drawArc(rect, -90, 360 * Math.max(0.01f, Math.min(1f, used)), false, arc);
        c.drawText(pct, cx, cy - (percent.ascent() + percent.descent()) / 2f, percent);
        float tx = cx + r + stroke / 2f + 10 * m.dp;
        c.drawText(value, tx, cy - 2 * m.dp, big);
        c.drawText(total, tx, cy + 2 * m.dp - small.ascent(), small);
    }

    private boolean inBadge(float x, float y) {
        float dx = x - (pad + 6 * m.dp);
        float dy = y - (m.topPad + 6 * m.dp);
        float r = 20 * m.dp;
        return dx * dx + dy * dy < r * r;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                moved = false;
                onBadge = host.isEditing() && inBadge(downX, downY);
                if (!host.isEditing()) postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                return true;
            case MotionEvent.ACTION_MOVE: {
                int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                if (Math.abs(e.getX() - downX) > slop || Math.abs(e.getY() - downY) > slop) {
                    moved = true;
                    removeCallbacks(longPress);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                removeCallbacks(longPress);
                if (onBadge && !moved && inBadge(e.getX(), e.getY())) performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPress);
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        host.onWidgetRemoveClick();
        return true;
    }

    void setWiggle(boolean on) {
        if (on == (wiggle != null)) {
            invalidate();
            return;
        }
        if (on) {
            wiggle = ObjectAnimator.ofFloat(this, View.ROTATION, -0.6f, 0.6f);
            wiggle.setDuration(140);
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
        stop();
        removeCallbacks(longPress);
    }
}
