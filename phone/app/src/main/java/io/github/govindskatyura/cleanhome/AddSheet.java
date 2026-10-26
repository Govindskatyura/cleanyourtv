package io.github.govindskatyura.cleanhome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/**
 * What iOS's "+" opens in jiggle mode: a tall sheet with a search field, the widget's live preview, and
 * every app A to Z. Tap the preview to add the widget; tap Add beside an app to put it on the Home Screen,
 * or tap an app already there to be taken to it.
 */
@SuppressLint("ViewConstructor")
final class AddSheet extends FrameLayout {
    interface Actions {
        boolean isOnHome(App app);

        void onAddWidget();

        void onAddApp(App app);

        void onShowApp(App app);
    }

    private final Metrics m;
    private final Actions actions;
    private final List<App> apps;
    private final boolean night;
    private final int primary;
    private final int secondary;
    private final View dim;
    private final LinearLayout sheet;
    private final EditText field;
    private final View widgetSection;
    private final TextView appsHeading;
    private final LinearLayout list;
    private final MemoryWidget preview;
    private final Runnable onClosed;
    private boolean closing;

    /** @param apps every app, A to Z */
    AddSheet(Context c, Metrics m, boolean widgetAdded, List<App> apps, Actions actions, Runnable onClosed) {
        super(c);
        this.m = m;
        this.actions = actions;
        this.apps = apps;
        this.onClosed = onClosed;
        night = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        primary = night ? Color.WHITE : Color.BLACK;
        secondary = night ? 0x99EBEBF5 : 0x993C3C43;

        dim = new View(c);
        dim.setBackgroundColor(0x66000000);
        dim.setOnClickListener(v -> close(null));
        addView(dim, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        sheet = new LinearLayout(c);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setClickable(true); // taps on the sheet itself don't reach the dim and close it
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(night ? 0xFF1C1C1E : 0xFFF2F2F7);
        float r = 14 * m.dp;
        bg.setCornerRadii(new float[] {r, r, r, r, 0, 0, 0, 0});
        sheet.setBackground(bg);
        sheet.setElevation(20 * m.dp);
        int side = Math.round(16 * m.dp);
        sheet.setPadding(side, Math.round(8 * m.dp), side, 0);

        View grabber = new View(c);
        GradientDrawable g = new GradientDrawable();
        g.setColor(night ? 0x66EBEBF5 : 0x4D3C3C43);
        g.setCornerRadius(3 * m.dp);
        grabber.setBackground(g);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(Math.round(36 * m.dp), Math.round(5 * m.dp));
        glp.gravity = Gravity.CENTER_HORIZONTAL;
        glp.bottomMargin = Math.round(14 * m.dp);
        sheet.addView(grabber, glp);

        field = new EditText(c);
        field.setHint(R.string.search_widgets_apps);
        field.setHintTextColor(secondary);
        field.setTextColor(primary);
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        GradientDrawable fieldBg = new GradientDrawable();
        fieldBg.setColor(night ? 0x3D767680 : 0x1F767680);
        fieldBg.setCornerRadius(12 * m.dp);
        field.setBackground(fieldBg);
        int pad = Math.round(10 * m.dp);
        field.setPadding(pad, 0, pad, 0);
        Drawable glass = c.getDrawable(R.drawable.ic_search);
        if (glass != null) {
            glass = glass.mutate();
            glass.setTint(secondary);
            int size = Math.round(18 * m.dp);
            glass.setBounds(0, 0, size, size);
            field.setCompoundDrawablesRelative(glass, null, null, null);
            field.setCompoundDrawablePadding(Math.round(6 * m.dp));
        }
        sheet.addView(field, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                Math.round(40 * m.dp)));

        ScrollView scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        sheet.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, Math.round(18 * m.dp), 0, m.insetBottom + Math.round(24 * m.dp));
        scroll.addView(content, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        // The widget, live, on a strip of dark so its white card shows; the whole strip is the button.
        LinearLayout section = new LinearLayout(c);
        section.setOrientation(LinearLayout.VERTICAL);
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
        stage.addView(preview, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        View tap = new View(c);
        if (!widgetAdded) tap.setOnClickListener(v -> close(actions::onAddWidget));
        stage.addView(tap, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        int stagePad = Math.round(6 * m.dp);
        stage.setPadding(0, stagePad, 0, stagePad);
        section.addView(stage, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView caption = new TextView(c);
        caption.setText(widgetAdded ? R.string.widget_added : R.string.memory_about);
        caption.setTextColor(secondary);
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        caption.setGravity(Gravity.CENTER);
        caption.setPadding(0, Math.round(8 * m.dp), 0, Math.round(26 * m.dp));
        section.addView(caption);
        widgetSection = section;
        content.addView(section);

        appsHeading = new TextView(c);
        appsHeading.setText(R.string.apps);
        appsHeading.setTextColor(primary);
        appsHeading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        appsHeading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        appsHeading.setPadding(0, 0, 0, Math.round(6 * m.dp));
        content.addView(appsHeading);

        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);

        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refresh(s.toString().trim().toLowerCase(Locale.ROOT));
            }
        });

        int top = m.insetTop + Math.round(28 * m.dp);
        addView(sheet, new LayoutParams(LayoutParams.MATCH_PARENT, m.height - top, Gravity.BOTTOM));
        refresh("");
    }

    /** The widget when the words fit it, and the apps whose names contain them. */
    private void refresh(String q) {
        String about = (getContext().getString(R.string.memory) + " "
                + getContext().getString(R.string.memory_about)).toLowerCase(Locale.ROOT);
        widgetSection.setVisibility(q.isEmpty() || about.contains(q) ? VISIBLE : GONE);
        list.removeAllViews();
        for (App a : apps) {
            if (q.isEmpty() || a.label.toLowerCase(Locale.ROOT).contains(q)) list.addView(row(a));
        }
        appsHeading.setVisibility(list.getChildCount() > 0 ? VISIBLE : GONE);
    }

    /** iOS's list row: the icon, the name, and Add (or a quiet "On Home Screen"), with a hairline under the name. */
    private View row(App a) {
        boolean onHome = actions.isOnHome(a);
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(getContext());
        icon.setImageBitmap(a.icon);
        int size = Math.round(44 * m.dp);
        row.addView(icon, new LinearLayout.LayoutParams(size, size));

        LinearLayout right = new LinearLayout(getContext());
        right.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        rlp.setMarginStart(Math.round(14 * m.dp));
        row.addView(right, rlp);

        LinearLayout line = new LinearLayout(getContext());
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        right.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        TextView name = new TextView(getContext());
        name.setText(a.label);
        name.setTextColor(primary);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        line.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView action = new TextView(getContext());
        action.setTextSize(TypedValue.COMPLEX_UNIT_SP, onHome ? 13 : 15);
        action.setGravity(Gravity.CENTER);
        if (onHome) {
            action.setText(R.string.widget_added);
            action.setTextColor(secondary);
        } else {
            // iOS's GET-style button: a light grey capsule with bold blue text.
            action.setText(R.string.add);
            action.setTextColor(Alert.BLUE);
            action.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(night ? 0xFF2C2C2E : 0xFFE3E3E8);
            pill.setCornerRadius(100 * m.dp);
            action.setBackground(pill);
            int h = Math.round(16 * m.dp);
            int v = Math.round(5 * m.dp);
            action.setPadding(h, v, h, v);
        }
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        alp.setMarginStart(Math.round(10 * m.dp));
        line.addView(action, alp);

        View hairline = new View(getContext());
        hairline.setBackgroundColor(night ? 0x33FFFFFF : 0x29000000);
        right.addView(hairline, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, Math.round(0.5f * m.dp))));

        StateListDrawable pressed = new StateListDrawable();
        pressed.addState(new int[] {android.R.attr.state_pressed},
                new ColorDrawable(night ? 0x1FFFFFFF : 0x14000000));
        row.setBackground(pressed);
        row.setOnClickListener(v -> close(() -> {
            if (onHome) {
                actions.onShowApp(a);
            } else {
                actions.onAddApp(a);
            }
        }));
        row.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                Math.round(64 * m.dp)));
        return row;
    }

    void open() {
        dim.setAlpha(0f);
        dim.animate().alpha(1f).setDuration(200).start();
        sheet.setTranslationY(m.height);
        sheet.animate().translationY(0f).setDuration(300).start();
        preview.start();
    }

    /** Slides away, then runs {@code then} (adding something) if given. */
    void close(Runnable then) {
        if (closing) return;
        closing = true;
        preview.stop();
        getContext().getSystemService(InputMethodManager.class).hideSoftInputFromWindow(field.getWindowToken(), 0);
        sheet.animate().translationY(sheet.getHeight()).setDuration(220).start();
        dim.animate().alpha(0f).setDuration(220).withEndAction(() -> {
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
