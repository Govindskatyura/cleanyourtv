package io.github.govindskatyura.cleanhome;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Which app sits where: the dock, then the pages in order, the memory widget at the top of page one,
 * and the apps taken off the Home Screen (still in the App Library). Kept as JSON in the app's preferences.
 */
final class HomeLayout {
    static final int DOCK_MAX = 4;
    /** The "page" number find() reports for the dock. */
    static final int DOCK = -1;
    /** The memory widget is iOS's medium size: the full width, two rows tall. */
    static final int WIDGET_ROWS = 2;

    final List<String> dock = new ArrayList<>();
    final List<List<String>> pages = new ArrayList<>();
    final Set<String> hidden = new LinkedHashSet<>();
    boolean widget = true;
    private final SharedPreferences prefs;

    HomeLayout(SharedPreferences prefs) {
        this.prefs = prefs;
        String json = prefs.getString("layout", null);
        if (json == null) return;
        try {
            JSONObject o = new JSONObject(json);
            JSONArray d = o.getJSONArray("dock");
            for (int i = 0; i < d.length(); i++) dock.add(d.getString(i));
            JSONArray ps = o.getJSONArray("pages");
            for (int i = 0; i < ps.length(); i++) {
                JSONArray p = ps.getJSONArray(i);
                List<String> page = new ArrayList<>();
                for (int j = 0; j < p.length(); j++) page.add(p.getString(j));
                pages.add(page);
            }
            JSONArray h = o.optJSONArray("hidden");
            if (h != null) {
                for (int i = 0; i < h.length(); i++) hidden.add(h.getString(i));
            }
            widget = o.optBoolean("widget", true);
        } catch (JSONException e) {
            dock.clear();
            pages.clear();
            hidden.clear();
            widget = true;
        }
    }

    boolean isEmpty() {
        return dock.isEmpty() && pages.isEmpty();
    }

    void save() {
        JSONArray ps = new JSONArray();
        for (List<String> p : pages) ps.put(new JSONArray(p));
        try {
            JSONObject o = new JSONObject()
                    .put("dock", new JSONArray(dock))
                    .put("pages", ps)
                    .put("hidden", new JSONArray(hidden))
                    .put("widget", widget);
            prefs.edit().putString("layout", o.toString()).apply();
        } catch (JSONException ignored) {
            // put() only throws for NaN and infinite numbers, and there are none here.
        }
    }

    /** Cells the widget takes at the start of a page. */
    int offset(int page) {
        return page == 0 && widget ? WIDGET_ROWS * Metrics.COLUMNS : 0;
    }

    /** How many apps fit on a page, given how many cells a page has. */
    int capacity(int page, int cells) {
        return Math.max(0, cells - offset(page));
    }

    /** First run: the dock, then everything else in the given order, page after page. */
    void seed(List<String> dockKeys, List<String> order, int cells) {
        dock.clear();
        pages.clear();
        hidden.clear();
        widget = true;
        for (String k : dockKeys) {
            if (dock.size() < DOCK_MAX && !dock.contains(k)) dock.add(k);
        }
        for (String k : order) {
            if (!dock.contains(k)) append(k, cells);
        }
        if (pages.isEmpty()) pages.add(new ArrayList<>());
    }

    /** Drops apps that were uninstalled and puts new ones at the end, as iOS does. */
    void reconcile(Collection<String> installed, List<String> order, int cells) {
        Set<String> keep = new HashSet<>(installed);
        hidden.retainAll(keep);
        Set<String> seen = new HashSet<>(hidden);
        for (Iterator<String> it = dock.iterator(); it.hasNext(); ) {
            String k = it.next();
            if (!keep.contains(k) || !seen.add(k)) it.remove();
        }
        for (List<String> p : pages) {
            for (Iterator<String> it = p.iterator(); it.hasNext(); ) {
                String k = it.next();
                if (!keep.contains(k) || !seen.add(k)) it.remove();
            }
        }
        for (String k : order) {
            if (seen.add(k)) append(k, cells);
        }
        normalize(cells);
        removeEmptyPages();
    }

    private void append(String key, int cells) {
        int last = pages.size() - 1;
        if (last < 0 || pages.get(last).size() >= capacity(last, cells)) pages.add(new ArrayList<>());
        pages.get(pages.size() - 1).add(key);
    }

    /** A full page pushes its last app onto the start of the next page, and so on down the line. */
    void normalize(int cells) {
        for (int i = 0; i < pages.size(); i++) {
            List<String> p = pages.get(i);
            int cap = capacity(i, cells);
            while (p.size() > cap) {
                if (i + 1 == pages.size()) pages.add(new ArrayList<>());
                pages.get(i + 1).add(0, p.remove(p.size() - 1));
            }
        }
    }

    void removeEmptyPages() {
        for (int i = pages.size() - 1; i >= 0; i--) {
            // Page one stays while it holds the widget.
            if (pages.get(i).isEmpty() && !(i == 0 && widget)) pages.remove(i);
        }
        if (pages.isEmpty()) pages.add(new ArrayList<>());
    }

    /** {DOCK, index} or {page, index}; null when the app is not on the Home Screen. */
    int[] find(String key) {
        int i = dock.indexOf(key);
        if (i >= 0) return new int[] {DOCK, i};
        for (int p = 0; p < pages.size(); p++) {
            i = pages.get(p).indexOf(key);
            if (i >= 0) return new int[] {p, i};
        }
        return null;
    }

    void remove(String key) {
        dock.remove(key);
        for (List<String> p : pages) p.remove(key);
    }

    /** "Remove from Home Screen": off the pages and the dock, still in the App Library. */
    void hide(String key) {
        remove(key);
        hidden.add(key);
    }

    /** "Add to Home Screen": back at the first free spot from the end, as iOS does. */
    void show(String key, int cells) {
        hidden.remove(key);
        if (find(key) == null) append(key, cells);
    }
}
