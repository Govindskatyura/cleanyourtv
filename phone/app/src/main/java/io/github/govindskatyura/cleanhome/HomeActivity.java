package io.github.govindskatyura.cleanhome;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.SearchManager;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The home screen. Builds the views in code (no layouts to inflate) and keeps the layout model in step. */
public class HomeActivity extends Activity implements AppIconView.Host, Workspace.Listener,
        PageIndicator.Listener, AppMenu.Actions, SearchOverlay.Actions, DragLayer.Host {

    private static final int RECENT_MAX = 8;
    private static final int MATCH = FrameLayout.LayoutParams.MATCH_PARENT;
    private static final int WRAP = FrameLayout.LayoutParams.WRAP_CONTENT;

    private final Map<String, App> apps = new HashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private Metrics m;
    private SharedPreferences prefs;
    private HomeLayout layout;
    private AppRepository repo;
    private LauncherApps launcherApps;
    private DragController drag;
    private DragLayer root;
    private Workspace workspace;
    private Dock dock;
    private PageIndicator indicator;
    private TextView done;
    private SearchOverlay search;
    private AppMenu menu;
    private boolean editing, loading, reloadAgain, loaded;
    /** Apps that finished loading mid-drag; applied when the app is dropped. */
    private List<App> deferred;

    private final LauncherApps.Callback appsChanged = new LauncherApps.Callback() {
        @Override
        public void onPackageRemoved(String packageName, UserHandle user) {
            repo.forget(packageName);
            reload();
        }

        @Override
        public void onPackageAdded(String packageName, UserHandle user) {
            reload();
        }

        @Override
        public void onPackageChanged(String packageName, UserHandle user) {
            repo.forget(packageName);
            reload();
        }

        @Override
        public void onPackagesAvailable(String[] packageNames, UserHandle user, boolean replacing) {
            reload();
        }

        @Override
        public void onPackagesUnavailable(String[] packageNames, UserHandle user, boolean replacing) {
            reload();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        m = new Metrics(this);
        prefs = getSharedPreferences("home", MODE_PRIVATE);
        layout = new HomeLayout(prefs);
        repo = new AppRepository(this, m.iconSize);
        launcherApps = getSystemService(LauncherApps.class);
        drag = new DragController(this, m);

        root = new DragLayer(this, this);
        workspace = new Workspace(this, m, this);
        indicator = new PageIndicator(this, m, this);
        dock = new Dock(this, m);
        done = doneButton();
        search = new SearchOverlay(this, m, this);
        // Hidden until the first geometry pass knows where they go.
        indicator.setVisibility(View.INVISIBLE);
        dock.setVisibility(View.INVISIBLE);
        search.setVisibility(View.GONE);
        root.addView(workspace, new FrameLayout.LayoutParams(MATCH, MATCH));
        root.addView(indicator, new FrameLayout.LayoutParams(MATCH, m.indicatorH));
        root.addView(dock, new FrameLayout.LayoutParams(MATCH, m.dockH));
        root.addView(done, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.END));
        root.addView(search, new FrameLayout.LayoutParams(MATCH, MATCH));
        setContentView(root);

        applyWallpaperColors();
        launcherApps.registerCallback(appsChanged, main);
        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyWallpaperColors();
    }

    @Override
    protected void onPause() {
        super.onPause();
        closeMenu(false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        search.closeNow();
        setEditing(false);
    }

    @Override
    protected void onDestroy() {
        launcherApps.unregisterCallback(appsChanged);
        loader.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed on the home screen itself (not on the way back from an app): close what is open,
        // or go back to page one, as iOS does.
        boolean alreadyHome = hasWindowFocus()
                && (intent.getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT;
        if (menu != null) {
            closeMenu(true);
        } else if (search.isOpen()) {
            search.close();
        } else if (editing) {
            setEditing(false);
        } else if (alreadyHome) {
            workspace.snapToPage(0);
        }
    }

    @Override
    public void onBackPressed() {
        // Home has nowhere to go back to: Back only closes what is open on it.
        if (menu != null) {
            closeMenu(true);
        } else if (search.isOpen()) {
            search.close();
        } else if (editing) {
            setEditing(false);
        }
    }

    // ---- Geometry and loading ----

    @Override
    public void onGeometry(int width, int height, int insetTop, int insetBottom) {
        m.layout(width, height, insetTop, insetBottom);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) dock.getLayoutParams();
        lp.width = width - 2 * m.dockSide;
        lp.height = m.dockH;
        lp.leftMargin = m.dockSide;
        lp.topMargin = m.dockTop;
        dock.setLayoutParams(lp);
        lp = (FrameLayout.LayoutParams) indicator.getLayoutParams();
        lp.topMargin = m.indicatorTop;
        indicator.setLayoutParams(lp);
        lp = (FrameLayout.LayoutParams) done.getLayoutParams();
        lp.topMargin = insetTop + Math.round(6 * m.dp);
        lp.setMarginEnd(Math.round(14 * m.dp));
        done.setLayoutParams(lp);
        dock.setVisibility(View.VISIBLE);
        indicator.setVisibility(View.VISIBLE);
        workspace.requestLayout();
        if (loaded) {
            layout.normalize(m.capacity());
            syncViews(false);
        }
    }

    private void reload() {
        if (loading) {
            reloadAgain = true;
            return;
        }
        loading = true;
        loader.execute(() -> {
            List<App> list = repo.load();
            main.post(() -> onAppsLoaded(list));
        });
    }

    private void onAppsLoaded(List<App> list) {
        loading = false;
        if (isDestroyed()) return;
        if (drag.isDragging()) {
            deferred = list;
        } else {
            apply(list);
        }
        if (reloadAgain) {
            reloadAgain = false;
            reload();
        }
    }

    private void apply(List<App> list) {
        apps.clear();
        for (App a : list) apps.put(a.key, a);
        Collator collator = Collator.getInstance();
        List<App> sorted = new ArrayList<>(list);
        sorted.sort((a, b) -> collator.compare(a.label, b.label));
        List<String> alphabetical = new ArrayList<>();
        for (App a : sorted) alphabetical.add(a.key);
        int capacity = m.width > 0 ? m.capacity() : Metrics.COLUMNS * 6;
        if (layout.isEmpty()) {
            layout.seed(Defaults.dock(this, apps), Defaults.order(alphabetical), capacity);
        } else {
            layout.reconcile(apps.keySet(), alphabetical, capacity);
        }
        layout.save();
        loaded = true;
        syncViews(false);
    }

    void syncViews(boolean animate) {
        if (m.width == 0 || !loaded) return;
        workspace.sync(layout, apps, this, editing, animate);
        dock.sync(layout, apps, this, editing, animate);
        indicator.setPages(layout.pages.size());
        String held = drag.key();
        if (held != null) {
            AppIconView v = viewFor(held);
            if (v != null) v.setVisibility(View.INVISIBLE);
        }
    }

    // ---- Used by DragController ----

    DragLayer root() {
        return root;
    }

    HomeLayout layout() {
        return layout;
    }

    Workspace workspace() {
        return workspace;
    }

    Dock dock() {
        return dock;
    }

    AppIconView viewFor(String key) {
        AppIconView v = workspace.views.get(key);
        return v != null ? v : dock.views.get(key);
    }

    /** The tile's top-left corner in root coordinates: where it is now, or where it is gliding to. */
    float[] iconPosition(AppIconView v, boolean target) {
        boolean inDock = v.getParent() == dock;
        View parent = inDock ? dock : workspace;
        float x = target ? v.targetX : v.getTranslationX();
        float y = target ? v.targetY : v.getTranslationY();
        float scroll = inDock ? 0 : (target ? workspace.getCurrentPage() * m.width : workspace.getScrollX());
        return new float[] {parent.getLeft() + x - scroll + v.iconLeft(), parent.getTop() + y + m.topPad};
    }

    void onDragEnded() {
        if (deferred != null) {
            List<App> list = deferred;
            deferred = null;
            apply(list);
        }
    }

    // ---- Jiggle mode ----

    private void setEditing(boolean on) {
        if (editing == on) return;
        editing = on;
        if (!on) {
            layout.removeEmptyPages();
            layout.save();
        }
        syncViews(!on);
        indicator.setEditing(on);
        done.animate().cancel();
        if (on) {
            done.setVisibility(View.VISIBLE);
            done.setAlpha(0f);
            done.animate().alpha(1f).setDuration(180).start();
        } else {
            done.animate().alpha(0f).setDuration(150).withEndAction(() -> done.setVisibility(View.GONE)).start();
        }
    }

    private TextView doneButton() {
        TextView t = new TextView(this);
        t.setText(R.string.done);
        t.setTextColor(Color.BLACK);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        int h = Math.round(16 * m.dp);
        int v = Math.round(7 * m.dp);
        t.setPadding(h, v, h, v);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE6F2F2F7);
        bg.setCornerRadius(100 * m.dp);
        t.setBackground(bg);
        t.setVisibility(View.GONE);
        t.setOnClickListener(x -> setEditing(false));
        return t;
    }

    // ---- AppIconView.Host, DragLayer.Host, Workspace.Listener ----

    @Override
    public boolean isEditing() {
        return editing;
    }

    @Override
    public boolean isDragging() {
        return drag.isDragging();
    }

    @Override
    public void onAppClick(AppIconView v) {
        if (!editing) launch(v);
    }

    @Override
    public void onAppLongPress(AppIconView v) {
        if (!editing) openMenu(v);
    }

    @Override
    public void onRemoveClick(AppIconView v) {
        uninstall(v.app);
    }

    @Override
    public void onDragStart(AppIconView v, MotionEvent e) {
        closeMenu(false);
        setEditing(true);
        drag.start(v, e);
    }

    @Override
    public void onDragTouch(MotionEvent e) {
        drag.onTouch(e);
    }

    @Override
    public void onPageScroll(float position) {
        indicator.setPosition(position);
    }

    @Override
    public void onPageSettled(int page) {
        indicator.settle();
    }

    @Override
    public void onSwipeDown() {
        openSearch();
    }

    @Override
    public void onEmptyTap() {
        if (editing) setEditing(false);
    }

    @Override
    public void onEmptyLongPress() {
        if (!search.isOpen()) setEditing(true);
    }

    // ---- Launching ----

    private void launch(AppIconView v) {
        App app = v.app;
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        int left = loc[0] + Math.round(v.iconLeft());
        int top = loc[1] + m.topPad;
        Rect bounds = new Rect(left, top, left + m.iconSize, top + m.iconSize);
        // The app grows out of its tile, as on iOS.
        Bundle opts = ActivityOptions.makeScaleUpAnimation(v, Math.round(v.iconLeft()), m.topPad,
                m.iconSize, m.iconSize).toBundle();
        try {
            launcherApps.startMainActivity(app.component, app.user, bounds, opts);
            remember(app.key);
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show();
        }
    }

    private void uninstall(App app) {
        Intent i = new Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.component.getPackageName(), null));
        try {
            startActivity(i);
        } catch (ActivityNotFoundException ignored) {
            // No uninstaller on this build: App Info still has the button.
        }
    }

    private void remember(String key) {
        List<String> recent = recent();
        recent.remove(key);
        recent.add(0, key);
        while (recent.size() > RECENT_MAX) recent.remove(recent.size() - 1);
        prefs.edit().putString("recent", TextUtils.join("\n", recent)).apply();
    }

    private List<String> recent() {
        List<String> out = new ArrayList<>();
        for (String k : prefs.getString("recent", "").split("\n")) {
            if (!k.isEmpty()) out.add(k);
        }
        return out;
    }

    // ---- Long-press menu ----

    private void openMenu(AppIconView v) {
        closeMenu(false);
        if (search.isOpen()) return;
        menu = new AppMenu(this, m, v, iconPosition(v, false), shortcutsFor(v.app), launcherApps, this);
        root.addView(menu, new FrameLayout.LayoutParams(MATCH, MATCH));
        menu.open();
        v.setVisibility(View.INVISIBLE);
    }

    private void closeMenu(boolean animate) {
        if (menu == null) return;
        AppMenu closing = menu;
        menu = null;
        closing.close(animate);
    }

    /** The app's own shortcuts, fixed ones first, at most four, as iOS shows Quick Actions. */
    private List<ShortcutInfo> shortcutsFor(App app) {
        List<ShortcutInfo> out = new ArrayList<>();
        try {
            if (!launcherApps.hasShortcutHostPermission()) return out;
            LauncherApps.ShortcutQuery q = new LauncherApps.ShortcutQuery();
            q.setPackage(app.component.getPackageName());
            q.setActivity(app.component);
            q.setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                    | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST);
            List<ShortcutInfo> found = launcherApps.getShortcuts(q, app.user);
            if (found == null) return out;
            List<ShortcutInfo> sorted = new ArrayList<>(found);
            sorted.sort((a, b) -> a.isDeclaredInManifest() != b.isDeclaredInManifest()
                    ? (a.isDeclaredInManifest() ? -1 : 1)
                    : Integer.compare(a.getRank(), b.getRank()));
            for (ShortcutInfo s : sorted) {
                if (s.isEnabled() && out.size() < 4) out.add(s);
            }
        } catch (RuntimeException ignored) {
            // Not the default Home yet, or the app has just gone: the menu simply has no shortcuts.
        }
        return out;
    }

    @Override
    public void onEditHome() {
        closeMenu(true);
        setEditing(true);
    }

    @Override
    public void onAppInfo(App app) {
        closeMenu(false);
        try {
            launcherApps.startAppDetailsActivity(app.component, app.user, null, null);
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRemove(App app) {
        closeMenu(false);
        uninstall(app);
    }

    @Override
    public void onShortcut(ShortcutInfo shortcut) {
        closeMenu(false);
        try {
            launcherApps.startShortcut(shortcut, null, null);
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onMenuClosed(AppIconView source) {
        if (!source.app.key.equals(drag.key())) source.setVisibility(View.VISIBLE);
    }

    // ---- Search ----

    @Override
    public void onSearch() {
        openSearch();
    }

    private void openSearch() {
        if (editing) return;
        closeMenu(false);
        search.open();
    }

    @Override
    public Collection<App> allApps() {
        return apps.values();
    }

    @Override
    public List<App> suggestions() {
        List<App> out = new ArrayList<>();
        for (String k : recent()) {
            App a = apps.get(k);
            if (a != null) out.add(a);
        }
        // Until there is a history, page one stands in.
        if (!layout.pages.isEmpty()) {
            for (String k : layout.pages.get(0)) {
                if (out.size() >= RECENT_MAX) break;
                App a = apps.get(k);
                if (a != null && !out.contains(a)) out.add(a);
            }
        }
        return out;
    }

    @Override
    public void launchFromSearch(AppIconView v) {
        launch(v);
        search.closeNow();
    }

    @Override
    public void searchWeb(String query) {
        try {
            startActivity(new Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query));
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))));
            } catch (ActivityNotFoundException ignored) {
                Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show();
            }
        }
        search.closeNow();
    }

    // ---- Wallpaper ----

    /** Tints the dock and the search pill from the wallpaper, and picks status bar icons that show up on it. */
    private void applyWallpaperColors() {
        int base = 0xFF8E8E93;
        boolean light = false;
        if (Build.VERSION.SDK_INT >= 27) {
            WallpaperColors colors = WallpaperManager.getInstance(this).getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            if (colors != null) {
                base = colors.getPrimaryColor().toArgb();
                light = Color.luminance(base) > 0.6f;
            }
        }
        // Frosted glass without a blur: the wallpaper's own colour lifted towards white, mostly see-through.
        int r = (Color.red(base) * 4 + 255 * 6) / 10;
        int g = (Color.green(base) * 4 + 255 * 6) / 10;
        int b = (Color.blue(base) * 4 + 255 * 6) / 10;
        int glass = Color.argb(light ? 0x66 : 0x4D, r, g, b);
        dock.setTint(glass);
        indicator.setTint(glass);
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (light) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }
}
