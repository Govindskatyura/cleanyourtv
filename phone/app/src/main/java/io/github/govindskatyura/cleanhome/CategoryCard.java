package io.github.govindskatyura.cleanhome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;

/**
 * One App Library group, as iOS draws it: a glass square holding three apps you can open straight away,
 * and in the fourth corner a tiny grid of the rest that opens the whole group. The group's name sits underneath.
 */
@SuppressLint("ViewConstructor")
final class CategoryCard extends View {
    interface Listener {
        void onCardApp(App app, View card, Rect slot);

        void onCardAppLongPress(App app, View card, Rect slot);

        void onCardOpen(String title, List<App> apps);
    }

    final String title;
    private final List<App> apps;
    private final Metrics m;
    private final Listener listener;
    private final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint pressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final Rect[] slots = {new Rect(), new Rect(), new Rect(), new Rect()};
    private final Rect tiny = new Rect();
    private final GestureDetector gestures;
    private CharSequence shownTitle = "";
    private int pressed = -1;

    CategoryCard(Context c, Metrics m, String title, List<App> apps, int tint, Listener listener) {
        super(c);
        this.m = m;
        this.title = title;
        this.apps = apps;
        this.listener = listener;
        glass.setColor(tint);
        pressedPaint.setColorFilter(new PorterDuffColorFilter(0x59000000, PorterDuff.Mode.SRC_ATOP));
        setContentDescription(title);
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public void onShowPress(MotionEvent e) {
                pressed = slotAt(e.getX(), e.getY());
                invalidate();
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                int s = slotAt(e.getX(), e.getY());
                if (s < 0) return false;
                if (s == 3 && apps.size() > 4) {
                    listener.onCardOpen(title, apps);
                } else if (s < apps.size()) {
                    listener.onCardApp(apps.get(s), CategoryCard.this, slots[s]);
                }
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                int s = slotAt(e.getX(), e.getY());
                if (s >= 0 && s < bigCount()) {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    listener.onCardAppLongPress(apps.get(s), CategoryCard.this, slots[s]);
                }
                clearPressed();
            }
        });
    }

    void setTint(int color) {
        glass.setColor(color);
        invalidate();
    }

    /** Apps drawn full size: all of them up to four, else three and the tiny grid. */
    private int bigCount() {
        return apps.size() > 4 ? 3 : apps.size();
    }

    private int slotAt(float x, float y) {
        for (int i = 0; i < 4; i++) {
            if (slots[i].contains((int) x, (int) y)) return i;
        }
        return -1;
    }

    private void clearPressed() {
        if (pressed != -1) {
            pressed = -1;
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(w, w + m.labelGap + m.labelHeight);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        float pad = w * 0.085f;
        float gap = w * 0.07f;
        float s = (w - 2 * pad - gap) / 2f;
        for (int i = 0; i < 4; i++) {
            int left = Math.round(pad + (i % 2) * (s + gap));
            int top = Math.round(pad + (i / 2) * (s + gap));
            slots[i].set(left, top, Math.round(left + s), Math.round(top + s));
        }
        shownTitle = TextUtils.ellipsize(title, m.labelPaint, w, TextUtils.TruncateAt.END);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        rect.set(0, 0, w, w);
        c.drawRoundRect(rect, w * 0.2f, w * 0.2f, glass);
        int big = bigCount();
        for (int i = 0; i < big; i++) {
            c.drawBitmap(apps.get(i).icon, null, slots[i], i == pressed ? pressedPaint : iconPaint);
        }
        if (apps.size() > 4) {
            Rect s = slots[3];
            int gap = Math.round(s.width() * 0.1f);
            int t = (s.width() - gap) / 2;
            for (int i = 0; i < 4 && 3 + i < apps.size(); i++) {
                int left = s.left + (i % 2) * (t + gap);
                int top = s.top + (i / 2) * (t + gap);
                tiny.set(left, top, left + t, top + t);
                c.drawBitmap(apps.get(3 + i).icon, null, tiny, pressed == 3 ? pressedPaint : iconPaint);
            }
        }
        c.drawText(shownTitle, 0, shownTitle.length(), w / 2f, w + m.labelGap - m.labelPaint.ascent(), m.labelPaint);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        boolean handled = gestures.onTouchEvent(e);
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) clearPressed();
        return handled || action == MotionEvent.ACTION_DOWN;
    }
}
