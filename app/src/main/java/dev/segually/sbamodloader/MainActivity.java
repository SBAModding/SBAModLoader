package dev.segually.sbamodloader;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.color.DynamicColors;
import androidx.fragment.app.Fragment;

import dev.segually.sbamodloader.tools.CrashDetector;
import dev.segually.sbamodloader.ui.GlassBar;
import dev.segually.sbamodloader.ui.pages.BrowseFragment;
import dev.segually.sbamodloader.ui.pages.DashboardFragment;
import dev.segually.sbamodloader.ui.pages.ModsFragment;
import dev.segually.sbamodloader.ui.pages.SettingsFragment;

public class MainActivity extends AppCompatActivity {
    private static final String KEY_PAGE = "page";
    private int page = 0;
    private NavigationBarView nav;
    /** Set by {@link #openBrowse}: the store page the Browse tab should open on when it is created. */
    private String pendingBrowseMod;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        nav = findViewById(R.id.nav_bar);
        GlassBar.apply(nav);
        // Material pads the bar by the system bar insets. The activity already positions the bar clear of them
        // (see below), so the padding would be counted twice, which is very visible on tablets with a taskbar.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(nav, (v, insets) -> insets);
        nav.setPadding(0, 0, 0, 0);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            showPage(id == R.id.nav_browse ? 1 : id == R.id.nav_mods ? 2 : id == R.id.nav_settings ? 3 : 0);
            return true;
        });

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_root), (v, insets) -> {
            float density = getResources().getDisplayMetrics().density;
            var bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            var lp = (android.widget.FrameLayout.LayoutParams) nav.getLayoutParams();
            lp.bottomMargin = bars.bottom + Math.round(16 * density);
            lp.width = Math.min(Math.round(400 * density), getResources().getDisplayMetrics().widthPixels - Math.round(32 * density));
            nav.setLayoutParams(lp);
            var fade = findViewById(R.id.edge_fade).getLayoutParams();
            fade.height = bars.bottom + Math.round(140 * density);
            findViewById(R.id.edge_fade).setLayoutParams(fade);
            return insets;
        });

        if (savedInstanceState != null) {
            page = savedInstanceState.getInt(KEY_PAGE, 0);
        } else {
            showPage(0);
        }

        CrashDetector.init(this);
        if (savedInstanceState == null) checkForUpdate();
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

    /** Switches to the Browse tab, on the store page of {@code modId} when it is not null. */
    public void openBrowse(String modId) {
        pendingBrowseMod = modId;
        if (nav.getSelectedItemId() == R.id.nav_browse) {
            showPage(1); // already selected: the listener would not fire
        } else {
            nav.setSelectedItemId(R.id.nav_browse);
        }
    }

    private void showPage(int index) {
        page = index;
        Fragment f = switch (index) {
            case 1 -> {
                BrowseFragment browse = new BrowseFragment();
                if (pendingBrowseMod != null) {
                    Bundle args = new Bundle();
                    args.putString(BrowseFragment.ARG_MOD_ID, pendingBrowseMod);
                    browse.setArguments(args);
                    pendingBrowseMod = null;
                }
                yield browse;
            }
            case 2 -> new ModsFragment();
            case 3 -> new SettingsFragment();
            default -> new DashboardFragment();
        };
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.page_container, f)
                .commit();
    }
}
