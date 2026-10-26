package io.github.govindskatyura.cleanhome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.OverScroller;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The home screen pages, side by side in one scrolling view. Every app view lives here directly and is
 * placed with translation, so a reorder is a glide and a drag can cross pages without changing parent.
 */
@SuppressLint("ViewConstructor")
final class Workspace extends ViewGroup {
    interface Listener {
        boolean isEditing();

        boolean isDragging();

        void onPageScroll(float position);

        void onPageSettled(int page);

        void onSwipeDown();

        void onEmptyTap();

        void onEmptyLongPress();
    }

    static final Interpolator EASE = new DecelerateInterpolator(1.6f);

    final Map<String, AppIconView> views = new HashMap<>();
    private MemoryWidget widget;
    private View library;
    private final Metrics m;
    private final Listener listener;
    private final OverScroller scroller;
    private final int touchSlop, minFling, maxFling;
    private final Runnable emptyLongPress;
    private VelocityTracker velocity;
    private int pageCount = 1;
    private int currentPage;
    private float downX, downY, lastX;
    private boolean paging, swiped, settling;

    Workspace(Context c, Metrics m, Listener listener) {
        super(c);
        this.m = m;
        this.listener = listener;
        scroller = new OverScroller(c, new DecelerateInterpolator(2.2f));
        ViewConfiguration vc = ViewConfiguration.get(c);
        touchSlop = vc.getScaledTouchSlop();
        minFling = vc.getScaledMinimumFlingVelocity();
        maxFling = vc.getScaledMaximumFlingVelocity();
        emptyLongPress = () -> {
            swiped = true; // this gesture is spent: no tap when the finger lifts
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            listener.onEmptyLongPress();
        };
    }

    /** The memory widget (top of page one) and the App Library (the page after the last), added once. */
    void attach(MemoryWidget widget, View library) {
        this.widget = widget;
        this.library = library;
        addView(widget);
        addView(library);
    }

    /** Places every app the layout puts on a page. New views appear in place; moved ones glide. */
    void sync(HomeLayout layout, Map<String, App> apps, AppIconView.Host host, boolean editing, boolean animate) {
        Set<String> wanted = new HashSet<>();
        for (List<String> p : layout.pages) wanted.addAll(p);
        for (Iterator<Map.Entry<String, AppIconView>> it = views.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, AppIconView> en = it.next();
            if (!wanted.contains(en.getKey()) || !apps.containsKey(en.getKey())) {
                removeView(en.getValue());
                it.remove();
            }
        }
        float contentH = m.contentHeight(true);
        for (int p = 0; p < layout.pages.size(); p++) {
            List<String> page = layout.pages.get(p);
            for (int i = 0; i < page.size(); i++) {
                App app = apps.get(page.get(i));
                if (app == null) continue;
                AppIconView v = views.get(app.key);
                if (v == null) {
                    v = new AppIconView(getContext(), m, host, true);
                    views.put(app.key, v);
                    addView(v);
                }
                if (v.app != app) v.setApp(app);
                v.setWiggle(editing);
                int cell = i + layout.offset(p);
                int col = cell % Metrics.COLUMNS;
                int row = Math.min(cell / Metrics.COLUMNS, m.rows - 1);
                float x = p * m.width + m.sidePad + col * m.cellW;
                float y = m.gridTop + row * m.cellH + (m.cellH - contentH) / 2f;
                place(v, x, y, animate);
            }
        }
        if (widget != null) {
            widget.setVisibility(layout.widget ? VISIBLE : GONE);
            widget.setWiggle(layout.widget && editing);
            // iOS sizes a widget to the icon grid: from the first icon's left edge to the last one's right,
            // and from the top of row one's icons to the bottom of row two's.
            widget.setFrame((Metrics.COLUMNS - 1) * m.cellW + m.iconSize, m.cellH + m.iconSize);
            widget.setTranslationX(m.sidePad + (m.cellW - m.iconSize) / 2f - widget.pad);
            widget.setTranslationY(m.gridTop + (m.cellH - contentH) / 2f);
        }
        if (library != null) library.setTranslationX(layout.pages.size() * m.width);
        pageCount = Math.max(1, layout.pages.size()) + 1;
        if (currentPage >= pageCount) snapToPage(pageCount - 1);
    }

    /** On the App Library, the page after the last. */
    boolean onLibrary() {
        return currentPage == pageCount - 1;
    }

    static void place(AppIconView v, float x, float y, boolean animate) {
        v.targetX = x;
        v.targetY = y;
        if (animate && v.placed) {
            v.animate().translationX(x).translationY(y).setDuration(260).setInterpolator(EASE).start();
        } else {
            v.animate().cancel();
            v.setTranslationX(x);
            v.setTranslationY(y);
            v.placed = true;
        }
    }

    int getCurrentPage() {
        return currentPage;
    }

    /** The grid cell under a point, as an index on the current page. */
    int cellAt(float x, float y) {
        int col = Math.max(0, Math.min(Metrics.COLUMNS - 1, (int) ((x - m.sidePad) / m.cellW)));
        int row = Math.max(0, Math.min(m.rows - 1, (int) ((y - m.gridTop) / m.cellH)));
        return row * Metrics.COLUMNS + col;
    }

    void snapToPage(int page) {
        page = Math.max(0, Math.min(pageCount - 1, page));
        currentPage = page;
        int dx = page * getWidth() - getScrollX();
        scroller.forceFinished(true);
        scroller.startScroll(getScrollX(), 0, dx, 0, Math.min(450, 220 + Math.abs(dx) / 4));
        settling = true;
        postInvalidateOnAnimation();
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.getCurrX(), 0);
            listener.onPageScroll(getScrollX() / (float) Math.max(1, getWidth()));
            postInvalidateOnAnimation();
        } else if (settling) {
            settling = false;
            listener.onPageSettled(currentPage);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        scroller.forceFinished(true);
        scrollTo(currentPage * w, 0);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), MeasureSpec.getSize(heightSpec));
        int cw = MeasureSpec.makeMeasureSpec(Math.round(m.cellW), MeasureSpec.EXACTLY);
        int ch = MeasureSpec.makeMeasureSpec(m.contentHeight(true), MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v == widget) {
                v.measure(MeasureSpec.makeMeasureSpec(widget.viewWidth(), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(widget.viewHeight(), MeasureSpec.EXACTLY));
            } else if (v == library) {
                v.measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
            } else {
                v.measure(cw, ch);
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            v.layout(0, 0, v.getMeasuredWidth(), v.getMeasuredHeight());
        }
    }

    private void track(MotionEvent e, boolean reset) {
        if (velocity == null) {
            velocity = VelocityTracker.obtain();
        } else if (reset) {
            velocity.clear();
        }
        velocity.addMovement(e);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (listener.isDragging()) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = e.getY();
                paging = false;
                swiped = false;
                track(e, true);
                if (!scroller.isFinished()) {
                    // Catch a page that is still sliding.
                    scroller.forceFinished(true);
                    settling = false;
                    paging = true;
                    return true;
                }
                return false;
            case MotionEvent.ACTION_MOVE:
                track(e, false);
                return beginGesture(e);
            default:
                return false;
        }
    }

    /** Sideways pages; a clear pull down opens search, as on iOS. */
    private boolean beginGesture(MotionEvent e) {
        float dx = e.getX() - downX;
        float dy = e.getY() - downY;
        if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
            removeCallbacks(emptyLongPress);
            paging = true;
            lastX = e.getX();
            return true;
        }
        // Not on the App Library, which scrolls up and down itself.
        if (!listener.isEditing() && !onLibrary() && dy > touchSlop * 2 && dy > Math.abs(dx) * 1.5f) {
            removeCallbacks(emptyLongPress);
            swiped = true;
            listener.onSwipeDown();
            return true;
        }
        if (Math.abs(dy) > touchSlop) removeCallbacks(emptyLongPress);
        return false;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (listener.isDragging()) return false;
        int action = e.getActionMasked();
        track(e, action == MotionEvent.ACTION_DOWN);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = e.getY();
                if (!paging) {
                    swiped = false;
                    postDelayed(emptyLongPress, ViewConfiguration.getLongPressTimeout());
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!paging && !swiped) beginGesture(e);
                if (paging) {
                    float dx = lastX - e.getX();
                    lastX = e.getX();
                    int x = getScrollX();
                    int max = (pageCount - 1) * getWidth();
                    if (x < 0 || x > max) dx /= 3f; // rubber band past the first and last page
                    scrollTo(Math.round(x + dx), 0);
                    listener.onPageScroll(getScrollX() / (float) Math.max(1, getWidth()));
                }
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(emptyLongPress);
                if (paging) {
                    velocity.computeCurrentVelocity(1000, maxFling);
                    float vx = velocity.getXVelocity();
                    float pos = getScrollX() / (float) Math.max(1, getWidth());
                    int page;
                    if (vx < -minFling * 3) {
                        page = (int) Math.floor(pos) + 1;
                    } else if (vx > minFling * 3) {
                        page = (int) Math.ceil(pos) - 1;
                    } else {
                        page = Math.round(pos);
                    }
                    snapToPage(page);
                } else if (!swiped && Math.abs(e.getX() - downX) < touchSlop && Math.abs(e.getY() - downY) < touchSlop) {
                    performClick();
                }
                paging = false;
                swiped = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(emptyLongPress);
                if (paging) snapToPage(Math.round(getScrollX() / (float) Math.max(1, getWidth())));
                paging = false;
                swiped = false;
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        listener.onEmptyTap();
        return true;
    }
}
