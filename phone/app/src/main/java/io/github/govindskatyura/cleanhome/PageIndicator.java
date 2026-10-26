package io.github.govindskatyura.cleanhome;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

/**
 * iOS 16's pill above the dock: it reads "Search" at rest and turns into page dots while you swipe
 * (and for the whole of jiggle mode). Tapping it opens search.
 */
@SuppressLint("ViewConstructor")
final class PageIndicator extends View {
    interface Listener {
        void onSearch();
    }

    private final Metrics m;
    private final Listener listener;
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final String label;
    private final float labelWidth;
    private final Runnable hideDots = () -> fadeDots(0f);
    private int pages = 1;
    private float position;
    private float dots; // 0 shows "Search", 1 shows the dots
    private boolean editing;
    private ValueAnimator fade;

    PageIndicator(Context c, Metrics m, Listener listener) {
        super(c);
        this.m = m;
        this.listener = listener;
        label = c.getString(R.string.search);
        pill.setColor(0x4DFFFFFF);
        dot.setColor(Color.WHITE);
        text.setColor(Color.WHITE);
        text.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13, c.getResources().getDisplayMetrics()));
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        labelWidth = text.measureText(label);
        glyph.setColor(Color.WHITE);
        glyph.setStyle(Paint.Style.STROKE);
        glyph.setStrokeWidth(1.6f * m.dp);
        glyph.setStrokeCap(Paint.Cap.ROUND);
    }

    void setTint(int color) {
        pill.setColor(color);
        invalidate();
    }

    void setPages(int n) {
        pages = Math.max(1, n);
        invalidate();
    }

    void setPosition(float p) {
        position = p;
        if (pages > 1) {
            removeCallbacks(hideDots);
            fadeDots(1f);
        }
        invalidate();
    }

    void settle() {
        removeCallbacks(hideDots);
        if (!editing) postDelayed(hideDots, 1200);
    }

    void setEditing(boolean on) {
        editing = on;
        removeCallbacks(hideDots);
        fadeDots(on ? 1f : 0f);
    }

    private void fadeDots(float to) {
        if (fade != null) fade.cancel();
        if (dots == to) return;
        fade = ValueAnimator.ofFloat(dots, to);
        fade.setDuration(220);
        fade.addUpdateListener(a -> {
            dots = (float) a.getAnimatedValue();
            invalidate();
        });
        fade.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float h = m.indicatorH;
        float step = 14 * m.dp;
        float dotR = 3.5f * m.dp;
        float searchW = labelWidth + 44 * m.dp;
        float dotsW = (pages - 1) * step + 2 * dotR + 24 * m.dp;
        float w = searchW + (dotsW - searchW) * dots;
        bounds.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
        c.drawRoundRect(bounds, h / 2, h / 2, pill);

        int searchAlpha = Math.round(255 * (1 - dots));
        if (searchAlpha > 0) {
            float left = cx - (labelWidth + 18 * m.dp) / 2f;
            glyph.setAlpha(searchAlpha);
            float r = 4.5f * m.dp;
            float gx = left + r;
            float gy = cy - 1 * m.dp;
            c.drawCircle(gx, gy, r, glyph);
            c.drawLine(gx + r * 0.72f, gy + r * 0.72f, gx + r * 1.45f, gy + r * 1.45f, glyph);
            text.setAlpha(searchAlpha);
            c.drawText(label, left + 18 * m.dp, cy - (text.ascent() + text.descent()) / 2f, text);
        }
        if (dots > 0) {
            float start = cx - (pages - 1) * step / 2f;
            for (int i = 0; i < pages; i++) {
                float near = Math.max(0f, 1f - Math.abs(i - position));
                dot.setAlpha(Math.round(255 * dots * (0.45f + 0.55f * near)));
                c.drawCircle(start + i * step, cy, dotR, dot);
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        boolean inside = bounds.contains(e.getX(), e.getY());
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Outside the pill, or in jiggle mode, let the touch fall through to the page behind.
                return inside && !editing;
            case MotionEvent.ACTION_UP:
                if (inside) performClick();
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        listener.onSearch();
        return true;
    }
}
