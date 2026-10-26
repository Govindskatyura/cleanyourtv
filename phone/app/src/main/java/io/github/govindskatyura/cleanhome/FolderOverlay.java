package io.github.govindskatyura.cleanhome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import java.util.List;

/** An App Library group opened out: its name, and every app in it on a grid over a dark veil. */
@SuppressLint("ViewConstructor")
final class FolderOverlay extends FrameLayout {
    private final LinearLayout column;
    private final Runnable onClosed;
    private boolean closing;

    FolderOverlay(Context c, Metrics m, String title, List<App> apps, AppIconView.Host host, Runnable onClosed) {
        super(c);
        this.onClosed = onClosed;
        setBackgroundColor(0xD9000000);

        ScrollView scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        int side = Math.round(8 * m.dp);
        column.setPadding(side, m.insetTop + Math.round(48 * m.dp), side, m.insetBottom + Math.round(24 * m.dp));
        // A tap anywhere that is not an app closes the group, as on iOS.
        column.setOnClickListener(v -> close(true));
        scroll.addView(column, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView name = new TextView(c);
        name.setText(title);
        name.setTextColor(Color.WHITE);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, 0, 0, Math.round(28 * m.dp));
        column.addView(name);

        LinearLayout row = null;
        for (int i = 0; i < apps.size(); i++) {
            if (i % Metrics.COLUMNS == 0) {
                row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                column.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        m.contentHeight(true) + Math.round(16 * m.dp)));
            }
            AppIconView v = new AppIconView(c, m, host, true);
            v.setApp(apps.get(i));
            row.addView(v, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        int rest = (Metrics.COLUMNS - apps.size() % Metrics.COLUMNS) % Metrics.COLUMNS;
        for (int i = 0; i < rest && row != null; i++) {
            row.addView(new Space(c), new LinearLayout.LayoutParams(0, 1, 1f));
        }
    }

    void open() {
        setAlpha(0f);
        animate().alpha(1f).setDuration(180).start();
        column.setScaleX(0.9f);
        column.setScaleY(0.9f);
        column.animate().scaleX(1f).scaleY(1f).setDuration(220).start();
    }

    void close(boolean animate) {
        if (closing) return;
        closing = true;
        Runnable done = () -> {
            ViewGroup parent = (ViewGroup) getParent();
            if (parent != null) parent.removeView(this);
            if (onClosed != null) onClosed.run();
        };
        if (animate) {
            animate().alpha(0f).setDuration(150).withEndAction(done).start();
        } else {
            done.run();
        }
    }
}
