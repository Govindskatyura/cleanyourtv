package io.github.govindskatyura.cleanhome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * The iOS long-press menu: the page dims, the app's tile lifts, and a rounded card opens beside it with
 * the app's own shortcuts (Android's Quick Actions), then Edit Home Screen, App Info and Remove App.
 */
@SuppressLint("ViewConstructor")
final class AppMenu extends FrameLayout {
    interface Actions {
        void onEditHome();

        void onAppInfo(App app);

        void onRemove(App app);

        void onShortcut(ShortcutInfo shortcut);

        void onMenuClosed(AppIconView source);
    }

    private static final String SECTION = "section";

    private final Metrics m;
    private final AppIconView source;
    private final Actions actions;
    private final View dim;
    private final ImageView lifted;
    private final LinearLayout card;
    private final boolean night;
    private boolean closing;

    AppMenu(Context c, Metrics m, AppIconView source, float[] iconAt, List<ShortcutInfo> shortcuts,
            LauncherApps launcherApps, Actions actions) {
        super(c);
        this.m = m;
        this.source = source;
        this.actions = actions;
        night = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        int textColor = night ? Color.WHITE : Color.BLACK;

        dim = new View(c);
        dim.setBackgroundColor(0x66000000);
        dim.setOnClickListener(v -> close(true));
        addView(dim, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        lifted = new ImageView(c);
        lifted.setImageBitmap(source.app.icon);
        addView(lifted, new LayoutParams(m.iconSize, m.iconSize));
        lifted.setX(iconAt[0]);
        lifted.setY(iconAt[1]);

        card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(night ? 0xF22C2C2E : 0xF5F2F2F7);
        bg.setCornerRadius(14 * m.dp);
        card.setBackground(bg);
        card.setClipToOutline(true);
        card.setElevation(16 * m.dp);

        int density = c.getResources().getDisplayMetrics().densityDpi;
        for (ShortcutInfo s : shortcuts) {
            Drawable icon = null;
            try {
                icon = launcherApps.getShortcutIconDrawable(s, density);
            } catch (RuntimeException ignored) {
                // A shortcut whose icon can't be read still works without one.
            }
            CharSequence label = s.getShortLabel() != null ? s.getShortLabel() : s.getLongLabel();
            addRow(label, icon, textColor, v -> actions.onShortcut(s));
        }
        if (!shortcuts.isEmpty()) addSection();
        addRow(c.getString(R.string.edit_home), tinted(R.drawable.ic_menu_edit, textColor), textColor,
                v -> actions.onEditHome());
        addRow(c.getString(R.string.app_info), tinted(R.drawable.ic_menu_info, textColor), textColor,
                v -> actions.onAppInfo(source.app));
        if (source.app.removable) {
            int red = 0xFFFF3B30;
            addRow(c.getString(R.string.remove_app), tinted(R.drawable.ic_menu_remove, red), red,
                    v -> actions.onRemove(source.app));
        }

        int width = Math.round(250 * m.dp);
        card.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int height = card.getMeasuredHeight();
        addView(card, new LayoutParams(width, LayoutParams.WRAP_CONTENT));

        // Beside the icon, on the side with room: under it if it fits, else above.
        float margin = 12 * m.dp;
        boolean leftHalf = iconAt[0] + m.iconSize / 2f < m.width / 2f;
        float x = leftHalf ? iconAt[0] : iconAt[0] + m.iconSize - width;
        x = Math.max(margin, Math.min(m.width - width - margin, x));
        float below = iconAt[1] + m.iconSize + margin;
        boolean fitsBelow = below + height < m.height - m.insetBottom - margin;
        float y = fitsBelow ? below : iconAt[1] - margin - height;
        y = Math.max(m.insetTop + margin, y);
        card.setX(x);
        card.setY(y);
        card.setPivotX(leftHalf ? 0 : width);
        card.setPivotY(fitsBelow ? 0 : height);
    }

    void open() {
        dim.setAlpha(0f);
        dim.animate().alpha(1f).setDuration(200).start();
        lifted.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).start();
        card.setAlpha(0f);
        card.setScaleX(0.5f);
        card.setScaleY(0.5f);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280)
                .setInterpolator(new OvershootInterpolator(1.2f)).start();
    }

    void close(boolean animate) {
        if (closing) return;
        closing = true;
        Runnable done = () -> {
            ViewGroup parent = (ViewGroup) getParent();
            if (parent != null) parent.removeView(this);
            actions.onMenuClosed(source);
        };
        if (!animate) {
            done.run();
            return;
        }
        card.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(150).start();
        lifted.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
        dim.animate().alpha(0f).setDuration(150).withEndAction(done).start();
    }

    private Drawable tinted(int res, int color) {
        Drawable d = getContext().getDrawable(res);
        if (d == null) return null;
        d = d.mutate();
        d.setTint(color);
        return d;
    }

    private void addRow(CharSequence label, Drawable icon, int color, OnClickListener click) {
        int count = card.getChildCount();
        if (count > 0 && !SECTION.equals(card.getChildAt(count - 1).getTag())) {
            View line = new View(getContext());
            line.setBackgroundColor(night ? 0x33FFFFFF : 0x29000000);
            card.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    Math.max(1, Math.round(0.5f * m.dp))));
        }
        TextView row = new TextView(getContext());
        row.setText(label);
        row.setTextColor(color);
        row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        row.setSingleLine(true);
        row.setEllipsize(TextUtils.TruncateAt.END);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Math.round(16 * m.dp);
        row.setPadding(pad, 0, pad, 0);
        if (icon != null) {
            int size = Math.round(21 * m.dp);
            icon.setBounds(0, 0, size, size);
            row.setCompoundDrawablesRelative(null, null, icon, null);
            row.setCompoundDrawablePadding(Math.round(12 * m.dp));
        }
        StateListDrawable pressed = new StateListDrawable();
        pressed.addState(new int[] {android.R.attr.state_pressed},
                new ColorDrawable(night ? 0x26FFFFFF : 0x1A000000));
        row.setBackground(pressed);
        row.setOnClickListener(click);
        card.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                Math.round(44 * m.dp)));
    }

    /** iOS's thick gap between the app's own actions and the system ones. */
    private void addSection() {
        View gap = new View(getContext());
        gap.setTag(SECTION);
        gap.setBackgroundColor(night ? 0x66000000 : 0x14000000);
        card.addView(gap, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                Math.round(8 * m.dp)));
    }
}
