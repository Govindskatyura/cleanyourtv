package io.github.govindskatyura.cleanhome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * What iOS's "+" opens in jiggle mode: a sheet from the bottom with the widget's name, a line about it,
 * a live preview, and Add Widget. There is one widget, so one page.
 */
@SuppressLint("ViewConstructor")
final class WidgetGallery extends FrameLayout {
    private final View dim;
    private final LinearLayout sheet;
    private final MemoryWidget preview;
    private final Runnable onClosed;
    private boolean closing;

    WidgetGallery(Context c, Metrics m, boolean added, Runnable onAdd, Runnable onClosed) {
        super(c);
        this.onClosed = onClosed;
        boolean night = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;

        dim = new View(c);
        dim.setBackgroundColor(0x66000000);
        dim.setOnClickListener(v -> close(null));
        addView(dim, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        sheet = new LinearLayout(c);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setGravity(Gravity.CENTER_HORIZONTAL);
        sheet.setClickable(true); // taps on the sheet itself don't fall through to the dim and close it
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(night ? 0xFF1C1C1E : 0xFFF2F2F7);
        float r = 14 * m.dp;
        bg.setCornerRadii(new float[] {r, r, r, r, 0, 0, 0, 0});
        sheet.setBackground(bg);
        sheet.setElevation(20 * m.dp);
        int side = Math.round(20 * m.dp);
        sheet.setPadding(side, Math.round(10 * m.dp), side, m.insetBottom + Math.round(24 * m.dp));

        View grabber = new View(c);
        GradientDrawable g = new GradientDrawable();
        g.setColor(night ? 0x66EBEBF5 : 0x4D3C3C43);
        g.setCornerRadius(3 * m.dp);
        grabber.setBackground(g);
        sheet.addView(grabber, new LinearLayout.LayoutParams(Math.round(36 * m.dp), Math.round(5 * m.dp)));

        TextView name = new TextView(c);
        name.setText(R.string.memory);
        name.setTextColor(night ? Color.WHITE : Color.BLACK);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setPadding(0, Math.round(18 * m.dp), 0, Math.round(4 * m.dp));
        sheet.addView(name);

        TextView about = new TextView(c);
        about.setText(R.string.memory_about);
        about.setTextColor(night ? 0x99EBEBF5 : 0x993C3C43);
        about.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        about.setGravity(Gravity.CENTER);
        about.setPadding(0, 0, 0, Math.round(20 * m.dp));
        sheet.addView(about);

        // The real widget, reading real numbers, on a strip of wallpaper-dark so its white card shows.
        FrameLayout stage = new FrameLayout(c);
        GradientDrawable stageBg = new GradientDrawable();
        stageBg.setColor(0xFF3A4A58);
        stageBg.setCornerRadius(22 * m.dp);
        stage.setBackground(stageBg);
        preview = new MemoryWidget(c, m, new MemoryWidget.Host() {
            @Override
            public boolean isEditing() {
                return false;
            }

            @Override
            public void onWidgetLongPress() {
            }

            @Override
            public void onWidgetRemoveClick() {
            }
        });
        preview.setFrame((Metrics.COLUMNS - 1) * m.cellW + m.iconSize, m.cellH + m.iconSize);
        stage.addView(preview, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        int stagePad = Math.round(6 * m.dp);
        stage.setPadding(0, stagePad, 0, stagePad);
        sheet.addView(stage, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView button = new TextView(c);
        button.setText(added ? R.string.widget_added : R.string.add_widget_button);
        button.setTextColor(added ? (night ? 0x99EBEBF5 : 0x993C3C43) : Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setGravity(Gravity.CENTER);
        GradientDrawable buttonBg = new GradientDrawable();
        buttonBg.setColor(added ? (night ? 0xFF2C2C2E : 0xFFE5E5EA) : Alert.BLUE);
        buttonBg.setCornerRadius(100 * m.dp);
        button.setBackground(buttonBg);
        button.setEnabled(!added);
        button.setOnClickListener(v -> close(onAdd));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Math.round(220 * m.dp), Math.round(50 * m.dp));
        blp.topMargin = Math.round(24 * m.dp);
        sheet.addView(button, blp);

        addView(sheet, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
    }

    void open() {
        dim.setAlpha(0f);
        dim.animate().alpha(1f).setDuration(200).start();
        sheet.setTranslationY(getResources().getDisplayMetrics().heightPixels);
        sheet.animate().translationY(0f).setDuration(280).start();
        preview.start();
    }

    /** Slides away, then runs {@code then} (Add Widget) if given. */
    void close(Runnable then) {
        if (closing) return;
        closing = true;
        preview.stop();
        sheet.animate().translationY(sheet.getHeight()).setDuration(200).start();
        dim.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            ViewGroup parent = (ViewGroup) getParent();
            if (parent != null) parent.removeView(this);
            if (onClosed != null) onClosed.run();
            if (then != null) then.run();
        }).start();
    }

    /** Swallows touches so nothing on the page behind reacts while the sheet is up. */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        return true;
    }
}
