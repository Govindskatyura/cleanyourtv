package io.github.govindskatyura.cleanhome;

import android.content.ComponentName;
import android.graphics.Bitmap;
import android.os.UserHandle;

/** One launchable activity: a tile on a page, in the dock, or in the App Library. */
final class App {
    final ComponentName component;
    final UserHandle user;
    final String key;
    final String label;
    final Bitmap icon;
    /** Installed by the user, so it can be deleted (system apps can only leave the Home Screen). */
    final boolean removable;
    /** ApplicationInfo.category, for the App Library's groups. */
    final int category;
    final long installed;

    App(ComponentName component, UserHandle user, String label, Bitmap icon, boolean removable, int category,
            long installed) {
        this.component = component;
        this.user = user;
        this.key = component.flattenToShortString();
        this.label = label;
        this.icon = icon;
        this.removable = removable;
        this.category = category;
        this.installed = installed;
    }
}
