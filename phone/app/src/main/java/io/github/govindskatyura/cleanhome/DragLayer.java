package io.github.govindskatyura.cleanhome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.MotionEvent;
import android.view.WindowInsets;
import android.widget.FrameLayout;

/** The root view: reports the screen size and system bar insets, and takes over the touch while an app is dragged. */
@SuppressLint("ViewConstructor")
final class DragLayer extends FrameLayout {
    interface Host {
        void onGeometry(int width, int height, int insetTop, int insetBottom);

        boolean isDragging();

        void onDragTouch(MotionEvent e);
    }

    private final Host host;
    private final Runnable report;
    private int insetTop, insetBottom;

    DragLayer(Context c, Host host) {
        super(c);
        this.host = host;
        // Posted: geometry changes lay views out again, which must not happen inside a layout pass.
        report = () -> {
            if (getWidth() > 0) host.onGeometry(getWidth(), getHeight(), insetTop, insetBottom);
        };
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        insetTop = insets.getSystemWindowInsetTop();
        insetBottom = insets.getSystemWindowInsetBottom();
        removeCallbacks(report);
        post(report);
        return insets;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        removeCallbacks(report);
        post(report);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        return host.isDragging();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!host.isDragging()) return false;
        host.onDragTouch(e);
        return true;
    }
}
