package io.github.govindskatyura.cleanhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The first-run layout: an iOS-like dock, the everyday tools on page one, then everything A to Z. */
final class Defaults {
    /** Page one, in this order, for whichever of these the phone has. */
    private static final String[] FIRST_PAGE = {
            "com.miui.gallery", "com.google.android.apps.photos",
            "com.xiaomi.calendar", "com.google.android.calendar", "com.android.calendar",
            "com.android.deskclock", "com.google.android.deskclock",
            "com.miui.notes", "com.google.android.keep",
            "com.google.android.apps.maps",
            "com.google.android.youtube",
            "com.google.android.gm",
            "com.android.vending",
            "com.android.contacts",
            "com.mi.android.globalFileexplorer", "com.google.android.apps.nbu.files",
            "com.miui.calculator", "com.google.android.calculator",
            "com.miui.securitycenter",
            "com.android.settings",
    };

    /** Fallbacks for a dock slot when no default app is set for that job. */
    private static final String[] DOCK_FALLBACK = {"com.android.chrome", "com.android.camera"};

    private Defaults() {
    }

    /** Phone, Messages, the browser and the camera: the jobs iOS gives its dock. */
    static List<String> dock(Context c, Map<String, App> apps) {
        List<String> out = new ArrayList<>();
        addFor(c, apps, new Intent(Intent.ACTION_DIAL), out);
        addFor(c, apps, new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")), out);
        addFor(c, apps, new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
                .addCategory(Intent.CATEGORY_BROWSABLE), out);
        addFor(c, apps, new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), out);
        for (String pkg : DOCK_FALLBACK) {
            if (out.size() >= HomeLayout.DOCK_MAX) break;
            String key = firstOf(apps, pkg, null);
            if (key != null && !out.contains(key)) out.add(key);
        }
        return out;
    }

    /** Page one's apps first, then the rest as given (alphabetical). */
    static List<String> order(List<String> alphabetical) {
        List<String> out = new ArrayList<>();
        for (String pkg : FIRST_PAGE) {
            for (String k : alphabetical) {
                if (k.startsWith(pkg + "/") && !out.contains(k)) out.add(k);
            }
        }
        for (String k : alphabetical) {
            if (!out.contains(k)) out.add(k);
        }
        return out;
    }

    private static void addFor(Context c, Map<String, App> apps, Intent intent, List<String> out) {
        PackageManager pm = c.getPackageManager();
        ResolveInfo ri = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (ri == null || ri.activityInfo == null) return;
        String pkg = ri.activityInfo.packageName;
        String exact = new ComponentName(pkg, ri.activityInfo.name).flattenToShortString();
        String key = apps.containsKey(exact) ? exact : firstOf(apps, pkg, ri.loadLabel(pm));
        if (key != null && !out.contains(key)) out.add(key);
    }

    /** The package's launcher entry with this label (one package can hold both Phone and Contacts), or its first. */
    private static String firstOf(Map<String, App> apps, String pkg, CharSequence label) {
        String first = null;
        for (App a : apps.values()) {
            if (!a.component.getPackageName().equals(pkg)) continue;
            if (label != null && a.label.contentEquals(label)) return a.key;
            if (first == null || a.key.compareTo(first) < 0) first = a.key;
        }
        return first;
    }
}
