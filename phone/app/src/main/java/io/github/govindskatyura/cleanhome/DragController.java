package io.github.govindskatyura.cleanhome;

import android.graphics.Outline;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Moves an app in jiggle mode. The app keeps a (hidden) place in the layout the whole time, so the
 * other apps flow around the gap as it travels: across the grid, onto other pages at the screen
 * edges, and into or out of the dock.
 */
final class DragController {
    private final HomeActivity home;
    private final Metrics m;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable reorder = this::applyTarget;
    private final Runnable flip = this::flipPage;
    private String key;
    private ImageView floating;
    private float rootX, rootY, offsetX, offsetY, lastX, lastY;
    private int targetPage, targetIndex, flipDir;

    DragController(HomeActivity home, Metrics m) {
        this.home = home;
        this.m = m;
    }

    boolean isDragging() {
        return key != null;
    }

    String key() {
        return key;
    }

    void start(AppIconView v, MotionEvent e) {
        key = v.app.key;
        DragLayer root = home.root();
        int[] loc = new int[2];
        root.getLocationOnScreen(loc);
        rootX = loc[0];
        rootY = loc[1];
        float[] at = home.iconPosition(v, false);
        lastX = e.getRawX() - rootX;
        lastY = e.getRawY() - rootY;
        offsetX = lastX - at[0];
        offsetY = lastY - at[1];

        floating = new ImageView(v.getContext());
        floating.setImageBitmap(v.app.icon);
        floating.setElevation(10 * m.dp);
        floating.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), view.getWidth() * 0.225f);
            }
        });
        root.addView(floating, new FrameLayout.LayoutParams(m.iconSize, m.iconSize));
        floating.setX(at[0]);
        floating.setY(at[1]);
        floating.animate().scaleX(1.12f).scaleY(1.12f).setDuration(150).start();
        v.setVisibility(View.INVISIBLE);

        int[] place = home.layout().find(key);
        targetPage = place == null ? 0 : place[0];
        targetIndex = place == null ? 0 : place[1];
    }

    void onTouch(MotionEvent e) {
        if (key == null) return;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                lastX = e.getRawX() - rootX;
                lastY = e.getRawY() - rootY;
                floating.setX(lastX - offsetX);
                floating.setY(lastY - offsetY);
                findTarget(lastX, lastY);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                drop();
                break;
            default:
                break;
        }
    }

    private void findTarget(float x, float y) {
        HomeLayout layout = home.layout();
        boolean inDock = layout.dock.contains(key);
        int page;
        int index;
        if (y > m.dockTop - 8 * m.dp && (inDock || layout.dock.size() < HomeLayout.DOCK_MAX)) {
            page = HomeLayout.DOCK;
            index = home.dock().slotAt(x - m.dockSide, inDock ? layout.dock.size() : layout.dock.size() + 1);
            cancelFlip();
        } else {
            Workspace ws = home.workspace();
            page = ws.getCurrentPage();
            List<String> items = page < layout.pages.size() ? layout.pages.get(page) : Collections.emptyList();
            int last = items.contains(key) ? items.size() - 1 : items.size();
            index = Math.min(ws.cellAt(x, y), Math.max(0, last));
            float edge = 22 * m.dp;
            if (x < edge) {
                scheduleFlip(-1);
            } else if (x > m.width - edge) {
                scheduleFlip(1);
            } else {
                cancelFlip();
            }
        }
        if (page != targetPage || index != targetIndex) {
            targetPage = page;
            targetIndex = index;
            // A short wait, so the apps don't shuffle under a finger that is only passing over them.
            handler.removeCallbacks(reorder);
            handler.postDelayed(reorder, 110);
        }
    }

    private void applyTarget() {
        if (key == null) return;
        HomeLayout layout = home.layout();
        int[] at = layout.find(key);
        if (at != null && at[0] == targetPage && at[1] == targetIndex) return;
        layout.remove(key);
        if (targetPage == HomeLayout.DOCK) {
            layout.dock.add(Math.min(targetIndex, layout.dock.size()), key);
        } else {
            while (layout.pages.size() <= targetPage) layout.pages.add(new ArrayList<>());
            List<String> p = layout.pages.get(targetPage);
            p.add(Math.min(targetIndex, p.size()), key);
            layout.normalize(m.capacity());
        }
        home.syncViews(true);
    }

    private void scheduleFlip(int dir) {
        if (flipDir == dir) return;
        flipDir = dir;
        handler.removeCallbacks(flip);
        handler.postDelayed(flip, 650);
    }

    private void cancelFlip() {
        flipDir = 0;
        handler.removeCallbacks(flip);
    }

    /** Held at a screen edge: turn the page, adding a blank one after the last, as iOS does. */
    private void flipPage() {
        if (key == null) return;
        Workspace ws = home.workspace();
        HomeLayout layout = home.layout();
        int current = ws.getCurrentPage();
        int target = current + flipDir;
        flipDir = 0; // still at the edge on the next move: flip again
        if (target < 0) return;
        if (target >= layout.pages.size()) {
            List<String> here = layout.pages.get(current);
            if (here.isEmpty() || (here.size() == 1 && here.contains(key))) return;
            layout.pages.add(new ArrayList<>());
            home.syncViews(true);
        }
        ws.snapToPage(target);
        findTarget(lastX, lastY);
    }

    private void drop() {
        handler.removeCallbacks(reorder);
        cancelFlip();
        applyTarget();
        String k = key;
        key = null;
        home.layout().save();
        ImageView f = floating;
        floating = null;
        AppIconView v = home.viewFor(k);
        if (v == null) {
            home.root().removeView(f);
        } else {
            v.setVisibility(View.INVISIBLE);
            float[] to = home.iconPosition(v, true);
            f.animate().x(to[0]).y(to[1]).scaleX(1f).scaleY(1f).setDuration(200)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> {
                        v.setVisibility(View.VISIBLE);
                        home.root().removeView(f);
                    })
                    .start();
        }
        home.onDragEnded();
    }
}
