package io.github.govindskatyura.cleanhome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** An iOS alert: a small rounded card in the middle with a title, a line of text, and stacked buttons. */
@SuppressLint("ViewConstructor")
final class Alert extends FrameLayout {
    static final int BLUE = 0xFF0A84FF;
    static final int RED = 0xFFFF3B30;

    static final class Button {
        final CharSequence label;
        final int color;
        final boolean bold;
        final Runnable action;

        Button(CharSequence label, int color, boolean bold, Runnable action) {
            this.label = label;
            this.color = color;
            this.bold = bold;
            this.action = action;
        }
    }

    private final LinearLayout card;
    private final View dim;
    private final Runnable onClosed;
    private boolean closing;

    Alert(Context c, Metrics m, CharSequence title, CharSequence message, List<Button> buttons, Runnable onClosed) {
        super(c);
        this.onClosed = onClosed;
        boolean night = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        dim = new View(c);
        dim.setBackgroundColor(0x66000000);
        dim.setClickable(true); // modal, as on iOS: a tap outside does nothing
        addView(dim, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(night ? 0xF22C2C2E : 0xF5F2F2F7);
        bg.setCornerRadius(14 * m.dp);
        card.setBackground(bg);
        card.setClipToOutline(true);
        card.setElevation(20 * m.dp);
        int side = Math.round(16 * m.dp);

        TextView t = new TextView(c);
        t.setText(title);
        t.setTextColor(night ? 0xFFFFFFFF : 0xFF000000);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setGravity(Gravity.CENTER);
        t.setPadding(side, Math.round(19 * m.dp), side, Math.round(2 * m.dp));
        card.addView(t);

        TextView msg = new TextView(c);
        msg.setText(message);
        msg.setTextColor(night ? 0xFFEBEBF5 : 0xFF3C3C43);
        msg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(side, Math.round(2 * m.dp), side, Math.round(19 * m.dp));
        card.addView(msg);

        int line = Math.max(1, Math.round(0.5f * m.dp));
        for (Button b : buttons) {
            View sep = new View(c);
            sep.setBackgroundColor(night ? 0x33FFFFFF : 0x29000000);
            card.addView(sep, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, line));
            TextView row = new TextView(c);
            row.setText(b.label);
            row.setTextColor(b.color);
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            if (b.bold) row.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            row.setGravity(Gravity.CENTER);
            StateListDrawable pressed = new StateListDrawable();
            pressed.addState(new int[] {android.R.attr.state_pressed},
                    new ColorDrawable(night ? 0x26FFFFFF : 0x1A000000));
            row.setBackground(pressed);
            row.setOnClickListener(v -> close(b.action));
            card.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    Math.round(44 * m.dp)));
        }
        addView(card, new LayoutParams(Math.round(270 * m.dp), LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    }

    void open() {
        dim.setAlpha(0f);
        dim.animate().alpha(1f).setDuration(180).start();
        card.setAlpha(0f);
        card.setScaleX(1.15f);
        card.setScaleY(1.15f);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start();
    }

    /** Fades out, then runs the chosen button's action (none for Cancel). */
    void close(Runnable then) {
        if (closing) return;
        closing = true;
        card.animate().alpha(0f).setDuration(140).start();
        dim.animate().alpha(0f).setDuration(140).withEndAction(() -> {
            ViewGroup parent = (ViewGroup) getParent();
            if (parent != null) parent.removeView(this);
            if (onClosed != null) onClosed.run();
            if (then != null) then.run();
        }).start();
    }
}
