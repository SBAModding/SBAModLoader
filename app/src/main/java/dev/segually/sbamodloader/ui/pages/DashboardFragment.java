package dev.segually.sbamodloader.ui.pages;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

import dev.segually.sbamodloader.BootstrapActivity;
import dev.segually.sbamodloader.tools.DeviceSupport;
import dev.segually.sbamodloader.ui.FusionWarning;
import dev.segually.sbamodloader.SBASettings;
import dev.segually.sbamodloader.BuildConfig;
import dev.segually.sbamodloader.MainActivity;
import dev.segually.sbamodloader.R;
import dev.segually.sbamodloader.TargetGame;
import dev.segually.sbamodloader.tools.PatchManager;
import dev.segually.sbamodloader.tools.AppUpdater;
import dev.segually.sbamodloader.ui.UpdatePrompt;
import dev.segually.sbamodloader.ui.Insets;

public class DashboardFragment extends Fragment {
    private enum State { OK, ERROR, PENDING }

    private View stepGame, stepVersion, stepPatches;
    private TextView setupTitle;
    private TextView heroSub;
    private View ringCheck;
    private com.google.android.material.progressindicator.CircularProgressIndicator ringView;
    private MaterialButton btnRetry, btnLaunch;
    /** A newer version of the loader, or null when there is none (or it could not be checked). */
    private AppUpdater.Release newerVersion;

    /** Null while the patch source is being checked. */
    private PatchManager.SyncResult patchResult;
    private boolean patchSyncing;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View v = inflater.inflate(R.layout.fragment_dashboard, container, false);
        Insets.padForChrome(v.findViewById(R.id.page_scroll), Insets.navClearance(requireContext()), 760, false);
        stepGame = v.findViewById(R.id.step_game);
        stepVersion = v.findViewById(R.id.step_version);
        stepPatches = v.findViewById(R.id.step_patches);
        setupTitle = v.findViewById(R.id.setup_title);
        heroSub = v.findViewById(R.id.hero_sub);
        ringView = v.findViewById(R.id.setup_ring);
        ringCheck = v.findViewById(R.id.setup_ring_check);
        dev.segually.sbamodloader.ui.Motion.pressable(v.findViewById(R.id.btn_launch));
        android.view.ViewGroup content = v.findViewById(R.id.dashboard_content);
        for (int i = 0; i < content.getChildCount(); i++) dev.segually.sbamodloader.ui.Motion.appear(content.getChildAt(i), i);
        btnRetry = v.findViewById(R.id.btn_retry_patches);
        btnLaunch = v.findViewById(R.id.btn_launch);

        btnRetry.setOnClickListener(x -> syncPatches());
        btnLaunch.setOnClickListener(x -> startBootstrap());

        boolean prereleases = SBASettings.getUpdatePrereleases(requireContext());
        new Thread(() -> {
            AppUpdater.Release newer = null;
            try {
                newer = AppUpdater.findNewer(prereleases);
            } catch (java.io.IOException ignored) {
                // Offline: say nothing is outdated rather than raise a false alarm.
            }
            AppUpdater.Release r = newer;
            if (isAdded()) requireActivity().runOnUiThread(() -> {
                newerVersion = r;
                render();
            });
        }, "update-check").start();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        syncPatches();
        checkMods();
    }

    /** Shows a card when installed mods have updates. */
    private void checkMods() {
        android.content.Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            dev.segually.sbamodloader.browser.ModHealth h = dev.segually.sbamodloader.browser.ModHealth.check(app, true);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                View root = getView();
                if (root == null) return;
                View card = root.findViewById(R.id.card_mod_updates);
                int n = h.updates.size();
                card.setVisibility(n == 0 ? View.GONE : View.VISIBLE);
                ((TextView) root.findViewById(R.id.mod_updates_text)).setText(n == 1
                        ? getString(R.string.dashboard_mod_updates_one) : getString(R.string.dashboard_mod_updates, n));
                card.setOnClickListener(x -> ((dev.segually.sbamodloader.MainActivity) requireActivity()).openMods());
            });
        }, "dashboard-mods").start();
    }

    private String installedGameVersion() {
        try {
            return requireContext().getPackageManager().getPackageInfo(TargetGame.PACKAGE_NAME, 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private boolean gameInstalled() {
        try {
            requireContext().getPackageManager().getPackageInfo(TargetGame.PACKAGE_NAME, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** Checks the patch source and installs the matching patch. Hashing and downloading are off the main thread. */
    private void syncPatches() {
        if (!isAdded() || patchSyncing) return;
        if (!gameInstalled()) {
            patchResult = null;
            render();
            return;
        }
        patchSyncing = true;
        patchResult = null;
        render();
        android.content.Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            PatchManager.SyncResult r = PatchManager.sync(app);
            if (isAdded()) requireActivity().runOnUiThread(() -> {
                patchSyncing = false;
                patchResult = r;
                render();
            });
            else patchSyncing = false;
        }, "patch-sync").start();
    }

    private void render() {
        if (!isAdded()) return;
        boolean game = gameInstalled();
        boolean latest = newerVersion == null;
        bind(stepGame, game ? State.OK : State.ERROR, getString(game ? R.string.step_game_ok : R.string.step_game_missing));
        stepVersion.setOnClickListener(latest ? null : x -> UpdatePrompt.start(requireActivity(), newerVersion));
        bind(stepVersion, latest ? State.OK : State.ERROR, getString(latest ? R.string.step_version_ok : R.string.step_version_outdated));

        boolean patchesOk = false;
        boolean failed = false;
        if (!game) {
            bind(stepPatches, State.PENDING, getString(R.string.step_patches_no_source));
        } else if (patchResult == null) {
            bind(stepPatches, State.PENDING, getString(R.string.step_patches_checking));
        } else {
            switch (patchResult.state) {
                case INSTALLED -> {
                    patchesOk = true;
                    bind(stepPatches, State.OK, getString(R.string.step_patches_ok));
                }
                case NOT_AVAILABLE -> bind(stepPatches, State.PENDING, getString(R.string.step_patches_not_available));
                case OFFLINE -> bind(stepPatches, State.PENDING, getString(R.string.step_patches_offline));
                case FAILED -> {
                    failed = true;
                    String detail = patchResult.message == null || patchResult.message.isEmpty() ? "" : "\n" + patchResult.message;
                    bind(stepPatches, State.ERROR, getString(R.string.step_patches_failed) + detail);
                }
            }
        }

        int done = (game ? 1 : 0) + (latest ? 1 : 0) + (patchesOk ? 1 : 0);
        setupTitle.setText(getString(R.string.setup_progress, done, 3));
        ringView.setProgressCompat(done, true);
        ringCheck.setVisibility(done == 3 ? View.VISIBLE : View.GONE);
        String gameVersion = installedGameVersion();
        heroSub.setText(gameVersion == null ? getString(R.string.step_game_missing)
                : getString(R.string.hero_game_version, gameVersion));
        btnRetry.setVisibility(failed ? View.VISIBLE : View.GONE);
        // Launching while a patch is being installed (or failed to install) would start an unpatched game.
        btnLaunch.setEnabled(game && patchResult != null && !failed);
    }

    private void bind(View step, State state, String text) {
        @DrawableRes int iconRes = state == State.OK ? R.drawable.ic_check_circle : state == State.ERROR ? R.drawable.ic_warning : R.drawable.ic_pending;
        int attr = state == State.ERROR ? androidx.appcompat.R.attr.colorError
                : state == State.OK ? androidx.appcompat.R.attr.colorPrimary
                : com.google.android.material.R.attr.colorOnSurfaceVariant;
        int color = MaterialColors.getColor(step, attr);
        ImageView iv = step.findViewById(R.id.step_icon);
        iv.setImageResource(iconRes);
        iv.setColorFilter(color);
        TextView tv = step.findViewById(R.id.step_text);
        tv.setText(text);
        tv.setTextColor(state == State.ERROR ? color : MaterialColors.getColor(step, com.google.android.material.R.attr.colorOnSurface));
    }

    private void startBootstrap() {
        android.content.Context context = requireContext();
        if (!SBASettings.getFusionWarningShown(context) && !SBASettings.getFusionEnabled(context)) {
            // First launch on a device that failed the Fusion check: explain what is turned off before going on.
            FusionWarning.showFirstLaunch(context, DeviceSupport.fusionProblems(context), () -> {
                SBASettings.setFusionWarningShown(context, true);
                launchBootstrap();
            });
            return;
        }
        launchBootstrap();
    }

    private void launchBootstrap() {
        MainActivity activity = (MainActivity) requireActivity();
        Intent intent = new Intent(activity, BootstrapActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        activity.finish();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
