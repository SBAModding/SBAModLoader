package dev.segually.sbamodloader;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import androidx.fragment.app.Fragment;

import dev.segually.sbamodloader.tools.CrashDetector;
import dev.segually.sbamodloader.ui.compose.NavDockHost;
import dev.segually.sbamodloader.ui.compose.NavState;
import dev.segually.sbamodloader.ui.pages.BrowseFragment;
import dev.segually.sbamodloader.ui.pages.DashboardFragment;
import dev.segually.sbamodloader.ui.pages.ModsFragment;
import dev.segually.sbamodloader.ui.pages.SettingsFragment;

public class MainActivity extends AppCompatActivity {
    private static final String KEY_PAGE = "page";
    private int page = 0;
    private final androidx.compose.runtime.MutableState<NavState> navState =
            androidx.compose.runtime.SnapshotStateKt.mutableStateOf(new NavState(0, false, 0), androidx.compose.runtime.SnapshotStateKt.structuralEqualityPolicy());
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

        androidx.compose.ui.platform.ComposeView navHost = findViewById(R.id.nav_host);
        NavDockHost.setContent(navHost, navState, this::showPage);

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_root), (v, insets) -> {
            // The pages are Compose and use up the insets, so the dock is told the bottom bar's height from here.
            int bottom = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).bottom;
            NavState now = navState.getValue();
            if (now.getBottomInset() != bottom) navState.setValue(new NavState(now.getSelected(), now.getShowAllLabels(), bottom));
            return insets;
        });

        applyLayoutMode();

        if (savedInstanceState != null) {
            page = savedInstanceState.getInt(KEY_PAGE, 0);
            selectNav(page);
        } else {
            // Debug builds: --ez sba_developer true|false turns developer features on or off before the page is built.
            dev.segually.sbamodloader.ui.compose.SbaDebug.collapse = BuildConfig.DEBUG && getIntent().getBooleanExtra("sba_collapse", false);
            if (BuildConfig.DEBUG && getIntent().hasExtra("sba_developer")) {
                SBASettings.setDeveloperFeatures(this, getIntent().getBooleanExtra("sba_developer", false));
            }
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

    private final java.util.concurrent.atomic.AtomicBoolean checkingGame = new java.util.concurrent.atomic.AtomicBoolean();

    @Override
    protected void onResume() {
        super.onResume();
        checkGameUpdate();
    }

    /**
     * When the game was updated (or mods were switched off for the old version and now fit), deals with the mods and
     * tells the player once. Needs the network only when something changed.
     */
    private void checkGameUpdate() {
        if (!checkingGame.compareAndSet(false, true)) return;
        android.content.Context app = getApplicationContext();
        new Thread(() -> {
            try {
                dev.segually.sbamodloader.browser.GameUpdate.reconcile(app);
            } catch (RuntimeException e) {
                android.util.Log.w("MainActivity", "The game update check failed", e);
            } finally {
                checkingGame.set(false);
            }
            var report = dev.segually.sbamodloader.browser.GameUpdate.takePending(app);
            if (report != null) runOnUiThread(() -> showGameUpdate(report));
        }, "game-update").start();
    }

    private void showGameUpdate(dev.segually.sbamodloader.browser.GameUpdate.Report r) {
        if (isFinishing() || isDestroyed()) return;
        StringBuilder text = new StringBuilder();
        if (r.gameChanged) {
            text.append(getString(R.string.game_update_intro, r.from, r.to)).append("\n\n");
            text.append(getString(R.string.game_update_data)).append("\n\n");
        }
        appendList(text, R.string.game_update_updated, r.updated);
        appendList(text, R.string.game_update_disabled, r.disabled);
        appendList(text, R.string.game_update_failed, r.failed);
        appendList(text, R.string.game_update_reenabled, r.reenabled);
        appendList(text, R.string.game_update_unchecked, r.unchecked);
        var dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(r.gameChanged ? R.string.game_update_title : R.string.game_update_title_back)
                .setMessage(text.toString().trim())
                .setPositiveButton(android.R.string.ok, null);
        if (!r.disabledIds.isEmpty()) {
            android.content.Context app = getApplicationContext();
            dialog.setNeutralButton(R.string.game_update_keep_on, (d, w) -> new Thread(() ->
                    dev.segually.sbamodloader.browser.GameUpdate.keepOn(app, r.disabledIds), "game-update-keep").start());
        }
        dialog.show();
    }

    private void appendList(StringBuilder into, int format, java.util.List<String> names) {
        if (names.isEmpty()) return;
        into.append(getString(format, String.join(", ", names))).append("\n\n");
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
        // Pretend the last game version seen was another one, to see the game update flow (--es sba_lastgame 13.0.7:1).
        if (i.hasExtra("sba_lastgame")) dev.segually.sbamodloader.browser.GameUpdate.debugSetLast(this, i.getStringExtra("sba_lastgame"));
        // What the dialog's "Turn them back on" does (--es sba_keepon gu-a,gu-b).
        if (i.hasExtra("sba_keepon")) {
            final java.util.List<String> ids = java.util.Arrays.asList(i.getStringExtra("sba_keepon").split(","));
            final android.content.Context app = getApplicationContext();
            new Thread(() -> dev.segually.sbamodloader.browser.GameUpdate.keepOn(app, ids), "debug-keepon").start();
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
        if (i.hasExtra("sba_install")) {
            // Debug builds: install "<id>@<version>" through the same planner and installer the store page uses.
            final String spec = i.getStringExtra("sba_install");
            final android.content.Context app = getApplicationContext();
            new Thread(() -> {
                try {
                    String[] parts = spec.split("@");
                    dev.segually.sbamodloader.browser.Repositories.Result read = dev.segually.sbamodloader.browser.Repositories.load(app, true);
                    java.util.Map<String, dev.segually.sbamodloader.browser.BrowserModels.Entry> index = new java.util.HashMap<>();
                    for (dev.segually.sbamodloader.browser.BrowserModels.Entry e : read.entries) index.put(e.id, e);
                    dev.segually.sbamodloader.browser.BrowserModels.Entry entry = index.get(parts[0]);
                    dev.segually.sbamodloader.browser.BrowserModels.Version version = entry.version(parts[1]);
                    dev.segually.sbamodloader.browser.InstallPlanner.Plan plan = dev.segually.sbamodloader.browser.InstallPlanner.plan(
                            entry, version, index, dev.segually.sbamodloader.browser.ModInstaller.installedVersions(app, index.values()), null);
                    dev.segually.sbamodloader.browser.ModInstaller.install(app, plan, (mod, done, total) -> { });
                    android.util.Log.i("SBA.Test", "sba_install done: " + spec + " steps=" + plan.steps.size());
                } catch (Exception e) {
                    android.util.Log.e("SBA.Test", "sba_install failed: " + spec, e);
                }
            }, "debug-install").start();
        }
        if (i.hasExtra("sba_export")) {
            final String path = i.getStringExtra("sba_export");
            final android.content.Context app = getApplicationContext();
            new Thread(() -> {
                try {
                    dev.segually.sbamodloader.tools.InteropExport.export(app, android.net.Uri.fromFile(new java.io.File(path)));
                    android.util.Log.i("SBA.Test", "sba_export done: " + path);
                } catch (Exception e) {
                    android.util.Log.e("SBA.Test", "sba_export failed", e);
                }
            }, "debug-export").start();
        }
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

    /** The dock is the same on every device; on a desktop it has room to show every label, not just the chosen one's. */
    private void applyLayoutMode() {
        boolean now = dev.segually.sbamodloader.ui.LayoutMode.isDesktop(this);
        dev.segually.sbamodloader.ui.LayoutMode.current = now;
        desktop = now;
        navState.setValue(new NavState(navState.getValue().getSelected(), desktop, navState.getValue().getBottomInset()));
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

    private void selectNav(int index) {
        NavState now = navState.getValue();
        if (now.getSelected() != index) navState.setValue(new NavState(index, now.getShowAllLabels(), now.getBottomInset()));
    }

    private void showPage(int index) {
        page = index;
        selectNav(index);
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
