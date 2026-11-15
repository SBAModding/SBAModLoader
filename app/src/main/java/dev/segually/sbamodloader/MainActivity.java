package dev.segually.sbamodloader;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import androidx.fragment.app.Fragment;

import dev.segually.sbamodloader.tools.CrashDetector;
import dev.segually.sbamodloader.ui.FloatingNav;
import dev.segually.sbamodloader.ui.pages.BrowseFragment;
import dev.segually.sbamodloader.ui.pages.DashboardFragment;
import dev.segually.sbamodloader.ui.pages.ModsFragment;
import dev.segually.sbamodloader.ui.pages.SettingsFragment;

public class MainActivity extends AppCompatActivity {
    private static final String KEY_PAGE = "page";
    private int page = 0;
    private android.view.View nav;
    private FloatingNav navigation;
    private com.google.android.material.navigationrail.NavigationRailView rail;
    private boolean desktop;
    /** Set by {@link #openBrowse}: the store page the Browse tab should open on when it is created. */
    private String pendingBrowseMod;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (BuildConfig.DEBUG) debugAppearance();
        dev.segually.sbamodloader.ui.Theming.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        dev.segually.sbamodloader.ui.Theming.applySystemBars(this);

        // The fade under the floating bar goes from the page colour to the same colour, transparent: a plain
        // "to transparent black" gradient leaves a grey band on a light theme.
        int pageColor = com.google.android.material.color.MaterialColors.getColor(findViewById(R.id.main_root),
                android.R.attr.colorBackground);
        findViewById(R.id.edge_fade).setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{pageColor, (pageColor & 0x00FFFFFF)}));

        nav = findViewById(R.id.nav_bar);
        navigation = new FloatingNav(findViewById(R.id.nav_items),
                new int[]{R.drawable.ic_nav_dashboard, R.drawable.ic_nav_browse, R.drawable.ic_nav_mods, R.drawable.ic_nav_settings},
                new int[]{R.string.nav_home, R.string.nav_browse, R.string.nav_mods, R.string.nav_settings},
                this::showPage);
        navigation.select(0, false);

        rail = findViewById(R.id.nav_rail);
        rail.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            int index = id == R.id.nav_browse ? 1 : id == R.id.nav_mods ? 2 : id == R.id.nav_settings ? 3 : 0;
            if (index != page) {
                navigation.select(index, false);
                showPage(index);
            }
            return true;
        });
        // The rail clears the status bar.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rail, (v, insets) -> {
            var bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()
                    | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, 0, bars.bottom);
            return insets;
        });
        applyLayoutMode();

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_root), (v, insets) -> {
            float density = getResources().getDisplayMetrics().density;
            var bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            var lp = (android.widget.FrameLayout.LayoutParams) nav.getLayoutParams();
            lp.bottomMargin = bars.bottom + Math.round(16 * density);
            nav.setLayoutParams(lp);
            var fade = findViewById(R.id.edge_fade).getLayoutParams();
            fade.height = bars.bottom + Math.round(140 * density);
            findViewById(R.id.edge_fade).setLayoutParams(fade);
            return insets;
        });

        if (savedInstanceState != null) {
            page = savedInstanceState.getInt(KEY_PAGE, 0);
            navigation.select(page, false);
        } else {
            showPage(0);
            // Debug builds can open on a given page (adb shell am start ... --ei sba_page 2), for layout checks.
            int start = BuildConfig.DEBUG ? getIntent().getIntExtra("sba_page", 0) : 0;
            if (start >= 1 && start <= 3) goTo(start);
            // ... and on a store page (--es sba_mod super-cars-adventure).
            String mod = BuildConfig.DEBUG ? getIntent().getStringExtra("sba_mod") : null;
            if (mod != null) openBrowse(mod);
        }

        CrashDetector.init(this);
        if (savedInstanceState == null) checkForUpdate();
    }

    /** Debug builds can set the appearance from the launch intent, for layout checks (--ez sba_you false --ei sba_accent 0xFFE65100). */
    private void debugAppearance() {
        android.content.Intent i = getIntent();
        if (i == null) return;
        if (i.hasExtra("sba_you")) SBASettings.setMaterialYou(this, i.getBooleanExtra("sba_you", true));
        if (i.hasExtra("sba_accent")) SBASettings.setAccentColor(this, i.getIntExtra("sba_accent", 0));
        if (i.hasExtra("sba_index")) {
            String url = i.getStringExtra("sba_index");
            SBASettings.setDebugIndexUrl(this, "official".equals(url) ? "" : url); // "official" goes back to the real index
        }
        if (i.hasExtra("sba_fake")) dev.segually.sbamodloader.browser.ModHealth.debugFake = i.getBooleanExtra("sba_fake", false);
        if (i.hasExtra("sba_addrepo")) {
            // Debug builds: add a repository by address, as the Settings dialog would (--es sba_addrepo http://127.0.0.1:8765/other/index.json).
            final String url = i.getStringExtra("sba_addrepo");
            final android.content.Context app = getApplicationContext();
            new Thread(() -> {
                try {
                    dev.segually.sbamodloader.browser.RepoSource source = dev.segually.sbamodloader.browser.RemoteModRepository.probe(url);
                    java.util.List<dev.segually.sbamodloader.browser.RepoSource> now = SBASettings.getExtraRepos(app);
                    boolean known = false;
                    for (dev.segually.sbamodloader.browser.RepoSource r : now) known |= r.url.equals(url);
                    if (!known) now.add(source);
                    SBASettings.setExtraRepos(app, now);
                } catch (java.io.IOException e) {
                    android.util.Log.e("MainActivity", "sba_addrepo failed", e);
                }
            }, "debug-addrepo").start();
        }
        if (i.getBooleanExtra("sba_clearrepos", false)) SBASettings.setExtraRepos(this, new java.util.ArrayList<>());
        if (i.hasExtra("sba_layout")) SBASettings.setLayoutMode(this, i.getIntExtra("sba_layout", 0));
        if (i.hasExtra("sba_theme")) SBASettings.setThemeMode(this, i.getIntExtra("sba_theme", 0));
    }

    private static final long UPDATE_CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000;

    /** Looks for a newer version in the background (at most every few hours) and offers it. */
    private void checkForUpdate() {
        android.content.Context app = getApplicationContext();
        if (!SBASettings.getAutoUpdate(app)) return;
        long now = System.currentTimeMillis();
        if (now - SBASettings.getLastUpdateCheck(app) < UPDATE_CHECK_INTERVAL_MS) return;
        boolean prereleases = SBASettings.getUpdatePrereleases(app);
        new Thread(() -> {
            try {
                var release = dev.segually.sbamodloader.tools.AppUpdater.findNewer(prereleases);
                SBASettings.setLastUpdateCheck(app, System.currentTimeMillis());
                if (release != null && !release.version.equals(SBASettings.getUpdateLater(app))) {
                    runOnUiThread(() -> dev.segually.sbamodloader.ui.UpdatePrompt.offer(this, release));
                }
            } catch (java.io.IOException ignored) {
                // Offline or GitHub is busy; the next launch tries again.
            }
        }, "update-check").start();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_PAGE, page);
    }

    /** Phone layout: the floating toolbar at the bottom. Desktop layout: a navigation rail at the side. */
    private void applyLayoutMode() {
        boolean now = dev.segually.sbamodloader.ui.LayoutMode.isDesktop(this);
        dev.segually.sbamodloader.ui.LayoutMode.current = now;
        desktop = now;
        nav.setVisibility(desktop ? android.view.View.GONE : android.view.View.VISIBLE);
        findViewById(R.id.edge_fade).setVisibility(desktop ? android.view.View.GONE : android.view.View.VISIBLE);
        rail.setVisibility(desktop ? android.view.View.VISIBLE : android.view.View.GONE);
        androidx.core.view.ViewCompat.requestApplyInsets(rail);
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config);
        boolean was = desktop;
        applyLayoutMode();
        // Pages are built for one layout; a change (a window resized into or out of desktop size) rebuilds the current one.
        if (was != desktop) showPage(page);
    }

    /** Esc goes back, like on a desktop. */
    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_ESCAPE && event.getRepeatCount() == 0) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /** Goes to a page (0 Home, 1 Browse, 2 Mods, 3 Settings) with whichever navigation is showing. */
    private void goTo(int index) {
        navigation.select(index, false);
        rail.getMenu().getItem(index).setChecked(true);
        showPage(index);
    }

    /** Switches to the Mods tab. */
    public void openMods() {
        goTo(2);
    }

    /** Switches to the Browse tab, on the store page of {@code modId} when it is not null. */
    public void openBrowse(String modId) {
        pendingBrowseMod = modId;
        goTo(1);
    }

    private void showPage(int index) {
        page = index;
        if (rail != null && rail.getMenu().size() > index) rail.getMenu().getItem(index).setChecked(true);
        if (navigation != null && navigation.selectedIndex() != index) navigation.select(index, false);
        Fragment f = switch (index) {
            case 1 -> {
                BrowseFragment browse = new BrowseFragment();
                if (pendingBrowseMod != null) {
                    Bundle args = new Bundle();
                    args.putString(BrowseFragment.ARG_MOD_ID, pendingBrowseMod);
                    args.putBoolean("debug_click", BuildConfig.DEBUG && getIntent().getBooleanExtra("sba_click", false));
                    browse.setArguments(args);
                    pendingBrowseMod = null;
                }
                yield browse;
            }
            case 2 -> new ModsFragment();
            case 3 -> new SettingsFragment();
            default -> new DashboardFragment();
        };
        // Pages fade through each other (the old one fades out, then the new one fades and scales in).
        f.setEnterTransition(new com.google.android.material.transition.MaterialFadeThrough());
        f.setExitTransition(new com.google.android.material.transition.MaterialFadeThrough());
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.page_container, f)
                .commit();
    }
}
