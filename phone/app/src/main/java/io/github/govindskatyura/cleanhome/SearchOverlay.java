package io.github.govindskatyura.cleanhome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Two iOS searches over a dark veil. Spotlight (pull down on a page, or the Search pill): suggestions,
 * then matching apps as a grid and a row that hands the words to the browser. App Library search (its
 * field at the top): every app A to Z as a list, narrowed as you type. No index, no network of its own.
 */
@SuppressLint("ViewConstructor")
final class SearchOverlay extends FrameLayout {
    interface Actions {
        Collection<App> allApps();

        List<App> suggestions();

        void launchFromSearch(AppIconView v);

        void launchApp(App app, View from);

        void appMenu(App app, View from);

        void searchWeb(String query);
    }

    private static final int MAX_RESULTS = 8;

    private final Metrics m;
    private final Actions actions;
    private final LinearLayout column;
    private final LinearLayout bar;
    private final EditText field;
    private final LinearLayout results;
    private final AppIconView.Host iconHost;
    private boolean library;
    private AppIconView firstHit;
    private App firstApp;
    private View firstRowIcon;

    SearchOverlay(Context c, Metrics m, Actions actions) {
        super(c);
        this.m = m;
        this.actions = actions;
        // iOS blurs the home screen here; a dark veil reads the same and costs nothing.
        setBackgroundColor(0xD9000000);
        setClickable(true);

        column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        addView(column, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        column.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                Math.round(40 * m.dp)));

        field = new EditText(c);
        field.setHintTextColor(0x99FFFFFF);
        field.setTextColor(Color.WHITE);
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        GradientDrawable fieldBg = new GradientDrawable();
        fieldBg.setColor(0x3D767680);
        fieldBg.setCornerRadius(12 * m.dp);
        field.setBackground(fieldBg);
        int pad = Math.round(10 * m.dp);
        field.setPadding(pad, 0, pad, 0);
        Drawable glass = glyph(0x99FFFFFF);
        if (glass != null) {
            field.setCompoundDrawablesRelative(glass, null, null, null);
            field.setCompoundDrawablePadding(Math.round(6 * m.dp));
        }
        if (Build.VERSION.SDK_INT >= 29) field.setTextCursorDrawable(null); // a cursor in the text colour
        bar.addView(field, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        TextView cancel = new TextView(c);
        cancel.setText(R.string.cancel);
        cancel.setTextColor(Color.WHITE);
        cancel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        cancel.setGravity(Gravity.CENTER_VERTICAL);
        cancel.setPadding(Math.round(12 * m.dp), 0, Math.round(4 * m.dp), 0);
        cancel.setOnClickListener(v -> close());
        bar.addView(cancel, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        ScrollView scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        column.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        results = new LinearLayout(c);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setPadding(0, 0, 0, Math.round(24 * m.dp));
        // A tap on empty space closes search; apps and rows take their own taps.
        results.setOnClickListener(v -> close());
        scroll.addView(results, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        iconHost = new AppIconView.Host() {
            @Override
            public boolean isEditing() {
                return false;
            }

            @Override
            public void onAppClick(AppIconView v) {
                actions.launchFromSearch(v);
            }

            @Override
            public void onAppLongPress(AppIconView v) {
            }

            @Override
            public void onRemoveClick(AppIconView v) {
            }

            @Override
            public void onDragStart(AppIconView v, MotionEvent e) {
            }
        };

        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refresh();
            }
        });
        field.setOnEditorActionListener((v, id, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (id != EditorInfo.IME_ACTION_GO && !enter) return false;
            String q = query();
            if (firstHit != null) {
                actions.launchFromSearch(firstHit);
            } else if (firstApp != null && firstRowIcon != null) {
                actions.launchApp(firstApp, firstRowIcon);
            } else if (!q.isEmpty() && !library) {
                actions.searchWeb(q);
            }
            return true;
        });
    }

    private Drawable glyph(int color) {
        Drawable d = getContext().getDrawable(R.drawable.ic_search);
        if (d == null) return null;
        d = d.mutate();
        d.setTint(color);
        int size = Math.round(18 * m.dp);
        d.setBounds(0, 0, size, size);
        return d;
    }

    boolean isOpen() {
        return getVisibility() == VISIBLE;
    }

    /** @param appLibrary the App Library's A-to-Z list rather than Spotlight */
    void open(boolean appLibrary) {
        library = appLibrary;
        field.setHint(appLibrary ? R.string.app_library : R.string.search);
        column.setPadding(Math.round(16 * m.dp), m.insetTop + Math.round(8 * m.dp), Math.round(16 * m.dp), 0);
        field.setText("");
        refresh();
        animate().cancel();
        setVisibility(VISIBLE);
        setAlpha(0f);
        animate().alpha(1f).setDuration(180).start();
        bar.setTranslationY(-24 * m.dp);
        bar.animate().translationY(0f).setDuration(240).start();
        field.requestFocus();
        post(() -> keyboard().showSoftInput(field, InputMethodManager.SHOW_IMPLICIT));
    }

    void close() {
        if (!isOpen()) return;
        keyboard().hideSoftInputFromWindow(field.getWindowToken(), 0);
        field.clearFocus();
        animate().cancel();
        animate().alpha(0f).setDuration(150).withEndAction(() -> setVisibility(GONE)).start();
    }

    /** For leaving the home screen: no animation, nothing left half-faded for next time. */
    void closeNow() {
        keyboard().hideSoftInputFromWindow(field.getWindowToken(), 0);
        field.clearFocus();
        animate().cancel();
        setVisibility(GONE);
    }

    private InputMethodManager keyboard() {
        return getContext().getSystemService(InputMethodManager.class);
    }

    private String query() {
        return field.getText().toString().trim();
    }

    private void refresh() {
        String q = query();
        results.removeAllViews();
        firstHit = null;
        firstApp = null;
        firstRowIcon = null;
        if (library) {
            List<App> list = q.isEmpty() ? alphabetical(actions.allApps()) : match(q, Integer.MAX_VALUE);
            if (!list.isEmpty()) firstApp = list.get(0);
            for (App a : list) results.addView(row(a));
            return;
        }
        List<App> list = q.isEmpty() ? actions.suggestions() : match(q, MAX_RESULTS);
        if (!list.isEmpty()) {
            results.addView(heading(q.isEmpty() ? R.string.suggestions : R.string.apps));
            LinearLayout row = null;
            for (int i = 0; i < list.size(); i++) {
                if (i % Metrics.COLUMNS == 0) {
                    row = new LinearLayout(getContext());
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    results.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                            m.contentHeight(true) + Math.round(8 * m.dp)));
                }
                AppIconView v = new AppIconView(getContext(), m, iconHost, true);
                v.longPressEnabled = false;
                v.setApp(list.get(i));
                if (firstHit == null) firstHit = v;
                row.addView(v, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            }
            int rest = (Metrics.COLUMNS - list.size() % Metrics.COLUMNS) % Metrics.COLUMNS;
            for (int i = 0; i < rest && row != null; i++) {
                row.addView(new Space(getContext()), new LinearLayout.LayoutParams(0, 1, 1f));
            }
        }
        if (!q.isEmpty()) results.addView(webRow(q));
    }

    private static List<App> alphabetical(Collection<App> apps) {
        Collator collator = Collator.getInstance();
        List<App> out = new ArrayList<>(apps);
        out.sort((a, b) -> collator.compare(a.label, b.label));
        return out;
    }

    /** Names that start with the words first, then a later word that does, then anything containing them. */
    private List<App> match(String q, int max) {
        String lq = q.toLowerCase(Locale.ROOT);
        List<App> starts = new ArrayList<>();
        List<App> words = new ArrayList<>();
        List<App> inside = new ArrayList<>();
        for (App a : alphabetical(actions.allApps())) {
            String l = a.label.toLowerCase(Locale.ROOT);
            if (l.startsWith(lq)) {
                starts.add(a);
            } else if (l.contains(" " + lq)) {
                words.add(a);
            } else if (l.contains(lq)) {
                inside.add(a);
            }
        }
        List<App> out = new ArrayList<>(starts);
        out.addAll(words);
        out.addAll(inside);
        return out.size() > max ? out.subList(0, max) : out;
    }

    /** One App Library row: the icon and the name. Long-press for Add to Home Screen, App Info, Delete App. */
    private View row(App a) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Math.round(4 * m.dp), 0, Math.round(4 * m.dp), 0);
        ImageView icon = new ImageView(getContext());
        icon.setImageBitmap(a.icon);
        icon.setContentDescription(a.label);
        int size = Math.round(38 * m.dp);
        row.addView(icon, new LinearLayout.LayoutParams(size, size));
        TextView name = new TextView(getContext());
        name.setText(a.label);
        name.setTextColor(Color.WHITE);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(Math.round(14 * m.dp));
        row.addView(name, lp);
        StateListDrawable pressed = new StateListDrawable();
        pressed.addState(new int[] {android.R.attr.state_pressed}, new ColorDrawable(0x1FFFFFFF));
        row.setBackground(pressed);
        row.setOnClickListener(v -> actions.launchApp(a, icon));
        row.setOnLongClickListener(v -> {
            actions.appMenu(a, icon);
            return true;
        });
        row.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                Math.round(54 * m.dp)));
        if (firstRowIcon == null) firstRowIcon = icon;
        return row;
    }

    private TextView heading(int text) {
        TextView t = new TextView(getContext());
        t.setText(text);
        t.setTextColor(0x99FFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setPadding(Math.round(4 * m.dp), Math.round(22 * m.dp), 0, Math.round(6 * m.dp));
        return t;
    }

    private TextView webRow(String q) {
        TextView t = new TextView(getContext());
        t.setText(getContext().getString(R.string.search_web, q));
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Math.round(14 * m.dp);
        t.setPadding(pad, 0, pad, 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x3D767680);
        bg.setCornerRadius(12 * m.dp);
        t.setBackground(bg);
        Drawable glass = glyph(Color.WHITE);
        if (glass != null) {
            t.setCompoundDrawablesRelative(glass, null, null, null);
            t.setCompoundDrawablePadding(Math.round(10 * m.dp));
        }
        t.setOnClickListener(v -> actions.searchWeb(q));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                Math.round(48 * m.dp));
        lp.topMargin = Math.round(18 * m.dp);
        t.setLayoutParams(lp);
        return t;
    }
}
