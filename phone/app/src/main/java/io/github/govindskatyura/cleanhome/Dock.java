package io.github.govindskatyura.cleanhome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** The rounded glass band at the bottom: up to four apps, no names, lined up with the grid's columns. */
@SuppressLint("ViewConstructor")
final class Dock extends ViewGroup {
    final Map<String, AppIconView> views = new HashMap<>();
    private final Metrics m;
    private final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    Dock(Context c, Metrics m) {
        super(c);
        this.m = m;
        glass.setColor(0x4DFFFFFF);
        setWillNotDraw(false);
    }

    void setTint(int color) {
        glass.setColor(color);
        invalidate();
    }

    void sync(HomeLayout layout, Map<String, App> apps, AppIconView.Host host, boolean editing, boolean animate) {
        for (Iterator<Map.Entry<String, AppIconView>> it = views.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, AppIconView> en = it.next();
            if (!layout.dock.contains(en.getKey()) || !apps.containsKey(en.getKey())) {
                removeView(en.getValue());
                it.remove();
            }
        }
        int n = layout.dock.size();
        float y = m.dockH / 2f - m.topPad - m.iconSize / 2f;
        for (int i = 0; i < n; i++) {
            App app = apps.get(layout.dock.get(i));
            if (app == null) continue;
            AppIconView v = views.get(app.key);
            if (v == null) {
                v = new AppIconView(getContext(), m, host, false);
                views.put(app.key, v);
                addView(v);
            }
            if (v.app != app) v.setApp(app);
            v.setWiggle(editing);
            Workspace.place(v, slotCenter(i, n) - m.cellW / 2f, y, animate);
        }
    }

    /** Four apps sit under the grid's four columns, as on iOS; fewer are centred at the same spacing. */
    float slotCenter(int i, int n) {
        if (n >= Metrics.COLUMNS) return m.columnCenter(i) - m.dockSide;
        float start = (m.width - 2 * m.dockSide - n * m.cellW) / 2f;
        return start + (i + 0.5f) * m.cellW;
    }

    /** The slot nearest x (dock coordinates) when the dock holds n apps. */
    int slotAt(float x, int n) {
        int best = 0;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            float d = Math.abs(slotCenter(i, n) - x);
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), MeasureSpec.getSize(heightSpec));
        int cw = MeasureSpec.makeMeasureSpec(Math.round(m.cellW), MeasureSpec.EXACTLY);
        int ch = MeasureSpec.makeMeasureSpec(m.contentHeight(false), MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).measure(cw, ch);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            v.layout(0, 0, v.getMeasuredWidth(), v.getMeasuredHeight());
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        rect.set(0, 0, getWidth(), getHeight());
        float radius = getHeight() * 0.34f;
        c.drawRoundRect(rect, radius, radius, glass);
    }
}
