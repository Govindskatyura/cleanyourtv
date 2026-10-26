package io.github.govindskatyura.cleanhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.DisplayMetrics;
import android.util.TypedValue;

/** Every size on the home screen, in pixels, scaled from an iPhone 15 (393 points wide). */
final class Metrics {
    static final int COLUMNS = 4;

    final float dp;
    final int iconSize;
    final int topPad;
    final int labelGap;
    final TextPaint labelPaint;
    final int labelHeight;
    final float labelBaseline;
    final int indicatorH;
    final int dockH;
    final int dockSide;
    final float sidePad;

    int width, height, insetTop, insetBottom;
    int rows = 6;
    float cellW, cellH, gridTop;
    int dockTop, indicatorTop;

    Metrics(Context c) {
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        dp = dm.density;
        float scale = Math.max(0.85f, Math.min(1.15f, dm.widthPixels / dp / 393f));
        iconSize = Math.round(60 * dp * scale);
        // Room above the tile for the edit-mode "minus" badge, which sits on the tile's corner.
        topPad = Math.round(8 * dp);
        labelGap = Math.round(6 * dp);
        labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12, dm));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        labelPaint.setShadowLayer(2 * dp, 0, 0.5f * dp, 0x59000000);
        Paint.FontMetricsInt fm = labelPaint.getFontMetricsInt();
        labelHeight = fm.descent - fm.ascent;
        labelBaseline = topPad + iconSize + labelGap - fm.ascent;
        indicatorH = Math.round(30 * dp);
        dockH = iconSize + Math.round(34 * dp);
        dockSide = Math.round(12 * dp);
        sidePad = 14 * dp;
    }

    int contentHeight(boolean label) {
        return topPad + iconSize + (label ? labelGap + labelHeight : 0);
    }

    /** Lays the screen out from the bottom up: dock, then the search pill, then as many rows as fit (iOS: 6). */
    void layout(int w, int h, int top, int bottom) {
        width = w;
        height = h;
        insetTop = top;
        insetBottom = bottom;
        cellW = (w - 2 * sidePad) / COLUMNS;
        dockTop = h - bottom - Math.round(10 * dp) - dockH;
        indicatorTop = dockTop - Math.round(8 * dp) - indicatorH;
        gridTop = top + 8 * dp;
        float gridH = indicatorTop - 4 * dp - gridTop;
        rows = Math.max(4, Math.min(6, (int) (gridH / (contentHeight(true) + 14 * dp))));
        cellH = gridH / rows;
    }

    int capacity() {
        return COLUMNS * rows;
    }

    float columnCenter(int col) {
        return sidePad + (col + 0.5f) * cellW;
    }
}
