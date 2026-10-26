package io.github.govindskatyura.cleanhome;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Which app sits where: the dock, then the pages in order. Kept as JSON in the app's preferences. */
final class HomeLayout {
    static final int DOCK_MAX = 4;
    /** The "page" number find() reports for the dock. */
    static final int DOCK = -1;

    final List<String> dock = new ArrayList<>();
    final List<List<String>> pages = new ArrayList<>();
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
        } catch (JSONException e) {
            dock.clear();
            pages.clear();
        }
    }

    boolean isEmpty() {
        return dock.isEmpty() && pages.isEmpty();
    }

    void save() {
        JSONArray ps = new JSONArray();
        for (List<String> p : pages) ps.put(new JSONArray(p));
        try {
            JSONObject o = new JSONObject().put("dock", new JSONArray(dock)).put("pages", ps);
            prefs.edit().putString("layout", o.toString()).apply();
        } catch (JSONException ignored) {
            // put() only throws for NaN and infinite numbers, and there are none here.
        }
    }

    /** First run: the dock, then everything else in the given order, page after page. */
    void seed(List<String> dockKeys, List<String> order, int capacity) {
        dock.clear();
        pages.clear();
        for (String k : dockKeys) {
            if (dock.size() < DOCK_MAX && !dock.contains(k)) dock.add(k);
        }
        for (String k : order) {
            if (!dock.contains(k)) append(k, capacity);
        }
        if (pages.isEmpty()) pages.add(new ArrayList<>());
    }

    /** Drops apps that were removed and puts new ones at the end, as iOS does. */
    void reconcile(Collection<String> installed, List<String> order, int capacity) {
        Set<String> keep = new HashSet<>(installed);
        Set<String> seen = new HashSet<>();
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
            if (seen.add(k)) append(k, capacity);
        }
        normalize(capacity);
        removeEmptyPages();
    }

    private void append(String key, int capacity) {
        if (pages.isEmpty() || pages.get(pages.size() - 1).size() >= capacity) pages.add(new ArrayList<>());
        pages.get(pages.size() - 1).add(key);
    }

    /** A full page pushes its last app onto the start of the next page, and so on down the line. */
    void normalize(int capacity) {
        for (int i = 0; i < pages.size(); i++) {
            List<String> p = pages.get(i);
            while (p.size() > capacity) {
                if (i + 1 == pages.size()) pages.add(new ArrayList<>());
                pages.get(i + 1).add(0, p.remove(p.size() - 1));
            }
        }
    }

    void removeEmptyPages() {
        for (Iterator<List<String>> it = pages.iterator(); it.hasNext(); ) {
            if (it.next().isEmpty()) it.remove();
        }
        if (pages.isEmpty()) pages.add(new ArrayList<>());
    }

    /** {DOCK, index} or {page, index}; null when the app has no place. */
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
}
