package io.github.govindskatyura.cleanhome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * iOS's App Library, the page after the last one: every app, sorted into groups two to a row, with
 * Suggestions and Recently Added first. Its search field opens an A-to-Z list of everything.
 */
@SuppressLint("ViewConstructor")
final class AppLibrary extends ScrollView {
    interface Actions extends CategoryCard.Listener {
        void onLibrarySearch();
    }

    static final class Category {
        final String title;
        final List<App> apps;

        Category(String title, List<App> apps) {
            this.title = title;
            this.apps = apps;
        }
    }

    /** Words that place an app the Play Store didn't categorise, checked against its package name and label. */
    private static final int[] RULE_GROUPS = {
            R.string.cat_finance, R.string.cat_shopping, R.string.cat_health, R.string.cat_social,
            R.string.cat_travel, R.string.cat_entertainment,
    };
    private static final String[][] RULE_WORDS = {
            {"pay", "bank", "wallet", "upi", "finance", "money", "splitwise", "paisa", "invest", "loan"},
            {"food", "swiggy", "zomato", "zepto", "blinkit", "bistro", "faasos", "eatsure", "meesho", "amazon",
                    "flipkart", "shop", "grocery", "delight", "myntra"},
            {"fit", "health", "workout", "yoga", "diet"},
            {"hinge", "bumble", "whatsapp", "telegram", "instagram", "facebook", "snapchat", "twitter",
                    "linkedin", "dating"},
            {"blablacar", "comuto", "uber", "rapido", "irctc", "flight", "travel"},
            {"music", "video", "movie", "netflix", "hotstar", "spotify", "guitar", "radio"},
    };

    private final Metrics m;
    private final Actions actions;
    private final LinearLayout column;
    private final GradientDrawable searchBg = new GradientDrawable();
    private final List<CategoryCard> cards = new ArrayList<>();
    private int tint = 0x4DFFFFFF;

    AppLibrary(Context c, Metrics m, Actions actions) {
        super(c);
        this.m = m;
        this.actions = actions;
        setVerticalScrollBarEnabled(false);
        setClipToPadding(false);

        column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        addView(column, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView search = new TextView(c);
        search.setText(R.string.app_library);
        search.setTextColor(0xCCFFFFFF);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        search.setGravity(Gravity.CENTER);
        searchBg.setCornerRadius(12 * m.dp);
        searchBg.setColor(tint);
        search.setBackground(searchBg);
        Drawable glass = c.getDrawable(R.drawable.ic_search);
        if (glass != null) {
            glass = glass.mutate();
            glass.setTint(0xCCFFFFFF);
            int size = Math.round(18 * m.dp);
            glass.setBounds(0, 0, size, size);
            search.setCompoundDrawablesRelative(glass, null, null, null);
            search.setCompoundDrawablePadding(Math.round(6 * m.dp));
            int pad = Math.round(12 * m.dp);
            search.setPadding(pad, 0, pad, 0);
        }
        search.setOnClickListener(v -> actions.onLibrarySearch());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                Math.round(40 * m.dp));
        lp.bottomMargin = Math.round(22 * m.dp);
        column.addView(search, lp);
    }

    void setTint(int color) {
        tint = color;
        searchBg.setColor(color);
        for (CategoryCard card : cards) card.setTint(color);
    }

    void setInsets(int top, int bottom) {
        int side = Math.round(20 * m.dp);
        column.setPadding(side, top + Math.round(10 * m.dp), side, bottom + Math.round(28 * m.dp));
    }

    void bind(List<Category> categories) {
        while (column.getChildCount() > 1) column.removeViewAt(1);
        cards.clear();
        int gap = Math.round(20 * m.dp);
        for (int i = 0; i < categories.size(); i += 2) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = 0; j < 2; j++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (j == 0) {
                    lp.setMarginEnd(gap / 2);
                } else {
                    lp.setMarginStart(gap / 2);
                }
                if (i + j < categories.size()) {
                    Category cat = categories.get(i + j);
                    CategoryCard card = new CategoryCard(getContext(), m, cat.title, cat.apps, tint, actions);
                    cards.add(card);
                    row.addView(card, lp);
                } else {
                    row.addView(new Space(getContext()), lp);
                }
            }
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = Math.round(16 * m.dp);
            column.addView(row, rowLp);
        }
    }

    /** Suggestions, Recently Added, then the groups, biggest first, with Utilities and Other at the end. */
    static List<Category> categorize(Context c, Collection<App> all, List<App> suggestions) {
        Collator collator = Collator.getInstance();
        List<App> sorted = new ArrayList<>(all);
        sorted.sort((a, b) -> collator.compare(a.label, b.label));
        List<Category> out = new ArrayList<>();
        if (!suggestions.isEmpty()) out.add(new Category(c.getString(R.string.suggestions), suggestions));

        List<App> recent = new ArrayList<>();
        for (App a : sorted) {
            if (a.removable) recent.add(a);
        }
        recent.sort((a, b) -> Long.compare(b.installed, a.installed));
        if (!recent.isEmpty()) {
            out.add(new Category(c.getString(R.string.recently_added),
                    new ArrayList<>(recent.subList(0, Math.min(8, recent.size())))));
        }

        Map<Integer, List<App>> groups = new LinkedHashMap<>();
        for (App a : sorted) {
            int g = groupOf(a);
            List<App> list = groups.get(g);
            if (list == null) {
                list = new ArrayList<>();
                groups.put(g, list);
            }
            list.add(a);
        }
        List<Integer> keys = new ArrayList<>(groups.keySet());
        keys.sort((x, y) -> {
            int tx = tail(x);
            int ty = tail(y);
            if (tx != ty) return Integer.compare(tx, ty);
            return Integer.compare(groups.get(y).size(), groups.get(x).size());
        });
        for (int k : keys) out.add(new Category(c.getString(k), groups.get(k)));
        return out;
    }

    private static int tail(int group) {
        if (group == R.string.cat_other) return 2;
        if (group == R.string.cat_utilities) return 1;
        return 0;
    }

    /** The app's Play Store category when it declares one, else a word in its name, else Utilities or Other. */
    static int groupOf(App a) {
        switch (a.category) {
            case ApplicationInfo.CATEGORY_GAME:
                return R.string.cat_games;
            case ApplicationInfo.CATEGORY_AUDIO:
            case ApplicationInfo.CATEGORY_VIDEO:
                return R.string.cat_entertainment;
            case ApplicationInfo.CATEGORY_IMAGE:
                return R.string.cat_photo;
            case ApplicationInfo.CATEGORY_SOCIAL:
                return R.string.cat_social;
            case ApplicationInfo.CATEGORY_NEWS:
                return R.string.cat_reading;
            case ApplicationInfo.CATEGORY_MAPS:
                return R.string.cat_travel;
            case ApplicationInfo.CATEGORY_PRODUCTIVITY:
                return R.string.cat_productivity;
            default:
                break;
        }
        String s = (a.component.getPackageName() + " " + a.label).toLowerCase(Locale.ROOT);
        for (int i = 0; i < RULE_GROUPS.length; i++) {
            for (String word : RULE_WORDS[i]) {
                if (s.contains(word)) return RULE_GROUPS[i];
            }
        }
        return a.removable ? R.string.cat_other : R.string.cat_utilities;
    }
}
