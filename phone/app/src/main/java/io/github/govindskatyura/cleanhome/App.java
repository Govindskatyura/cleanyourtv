package io.github.govindskatyura.cleanhome;

import android.content.ComponentName;
import android.graphics.Bitmap;
import android.os.UserHandle;

/** One launchable activity: a tile on a page or in the dock. */
final class App {
    final ComponentName component;
    final UserHandle user;
    final String key;
    final String label;
    final Bitmap icon;
    final boolean removable;

    App(ComponentName component, UserHandle user, String label, Bitmap icon, boolean removable) {
        this.component = component;
        this.user = user;
        this.key = component.flattenToShortString();
        this.label = label;
        this.icon = icon;
        this.removable = removable;
    }
}
