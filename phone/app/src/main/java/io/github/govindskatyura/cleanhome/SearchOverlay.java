package io.github.govindskatyura.cleanhome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * iOS Spotlight, for apps: a search field over a dark veil, suggestions while it is empty, matching apps
 * as you type, and a last row that hands the words to the web browser. No index, no network of its own.
 */
@SuppressLint("ViewConstructor")
final class SearchOverlay extends FrameLayout {
    interface Actions {
        Collection<App> allApps();

        List<App> suggestions();

        void launchFromSearch(AppIconView v);

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
    private AppIconView firstHit;

    SearchOverlay(Context c, Metrics m, Actions actions) {
        super(c);
        this.m = m;
        this.actions = actions;
        // iOS blurs the home screen here; a dark veil reads the same and costs nothing.
        setBackgroundColor(0xC7000000);
        setClickable(true);

        column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        addView(column, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        column.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                Math.round(40 * m.dp)));

        field = new EditText(c);
        field.setHint(R.string.search);
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
        Drawable glass = c.getDrawable(R.drawable.ic_search);
        if (glass != null) {
            glass = glass.mutate();
            glass.setTint(0x99FFFFFF);
            int size = Math.round(18 * m.dp);
            glass.setBounds(0, 0, size, size);
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

        results = new LinearLayout(c);
        results.setOrientation(LinearLayout.VERTICAL);
        column.addView(results, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

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
            } else if (!q.isEmpty()) {
                actions.searchWeb(q);
            }
            return true;
        });
    }

    boolean isOpen() {
        return getVisibility() == VISIBLE;
    }

    void open() {
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
        List<App> list = q.isEmpty() ? actions.suggestions() : match(q);
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
            for (int i = 0; i < rest; i++) {
                row.addView(new Space(getContext()), new LinearLayout.LayoutParams(0, 1, 1f));
            }
        }
        if (!q.isEmpty()) results.addView(webRow(q));
    }

    /** Names that start with the words first, then a later word that does, then anything containing them. */
    private List<App> match(String q) {
        String lq = q.toLowerCase(Locale.ROOT);
        List<App> starts = new ArrayList<>();
        List<App> words = new ArrayList<>();
        List<App> inside = new ArrayList<>();
        for (App a : actions.allApps()) {
            String l = a.label.toLowerCase(Locale.ROOT);
            if (l.startsWith(lq)) {
                starts.add(a);
            } else if (l.contains(" " + lq)) {
                words.add(a);
            } else if (l.contains(lq)) {
                inside.add(a);
            }
        }
        Collator collator = Collator.getInstance();
        starts.sort((a, b) -> collator.compare(a.label, b.label));
        words.sort((a, b) -> collator.compare(a.label, b.label));
        inside.sort((a, b) -> collator.compare(a.label, b.label));
        List<App> out = new ArrayList<>(starts);
        out.addAll(words);
        out.addAll(inside);
        return out.size() > MAX_RESULTS ? out.subList(0, MAX_RESULTS) : out;
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
        Drawable glass = getContext().getDrawable(R.drawable.ic_search);
        if (glass != null) {
            glass = glass.mutate();
            glass.setTint(Color.WHITE);
            int size = Math.round(18 * m.dp);
            glass.setBounds(0, 0, size, size);
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

    /** Swallows every touch so nothing behind opens; a tap below the results closes search. */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_UP && e.getY() > column.getBottom()) close();
        return true;
    }
}
