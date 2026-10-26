package io.github.govindskatyura.cleanhome;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.os.UserHandle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Lists the launchable apps and renders each icon once, off the main thread. */
final class AppRepository {
    private final Context context;
    private final LauncherApps launcherApps;
    private final int iconSize;
    private final int iconDensity;
    private final Map<String, Bitmap> icons = new HashMap<>();

    AppRepository(Context context, int iconSize) {
        this.context = context.getApplicationContext();
        this.launcherApps = context.getSystemService(LauncherApps.class);
        this.iconSize = iconSize;
        this.iconDensity = context.getSystemService(ActivityManager.class).getLauncherLargeIconDensity();
    }

    /** Runs on the loader thread. */
    List<App> load() {
        List<App> apps = new ArrayList<>();
        UserHandle me = Process.myUserHandle();
        for (LauncherActivityInfo info : launcherApps.getActivityList(null, me)) {
            ComponentName cn = info.getComponentName();
            if (cn.getPackageName().equals(context.getPackageName())) continue;
            String key = cn.flattenToShortString();
            Bitmap icon;
            synchronized (icons) {
                icon = icons.get(key);
            }
            if (icon == null) {
                Drawable d = info.getIcon(iconDensity);
                if (d == null) d = context.getPackageManager().getDefaultActivityIcon();
                icon = IconShape.render(d, iconSize);
                synchronized (icons) {
                    icons.put(key, icon);
                }
            }
            boolean removable = (info.getApplicationInfo().flags & ApplicationInfo.FLAG_SYSTEM) == 0;
            apps.add(new App(cn, me, info.getLabel().toString(), icon, removable));
        }
        return apps;
    }

    /** Drops a package's icons so an update shows its new ones. */
    void forget(String packageName) {
        String prefix = packageName + "/";
        synchronized (icons) {
            for (Iterator<String> it = icons.keySet().iterator(); it.hasNext(); ) {
                if (it.next().startsWith(prefix)) it.remove();
            }
        }
    }
}
