package dev.segually.sbamodloader.ui.pages;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;


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
import dev.segually.sbamodloader.ui.compose.DashboardState;
import dev.segually.sbamodloader.ui.compose.DashboardScreenHost;
import dev.segually.sbamodloader.ui.compose.SetupStep;
import dev.segually.sbamodloader.ui.compose.StepState;

public class DashboardFragment extends Fragment {
    /** What the Compose screen shows; render() replaces it whenever something changes. */
    private final androidx.compose.runtime.MutableState<DashboardState> uiState =
            androidx.compose.runtime.SnapshotStateKt.mutableStateOf(new DashboardState(), androidx.compose.runtime.SnapshotStateKt.structuralEqualityPolicy());
    /** A newer version of the loader, or null when there is none (or it could not be checked). */
    private AppUpdater.Release newerVersion;

    /** Null while the patch source is being checked. */
    private PatchManager.SyncResult patchResult;
    private boolean patchSyncing;
    /** How many installed mods have an update waiting. */
    private int modUpdates;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View v = inflater.inflate(R.layout.fragment_dashboard, container, false);
        Insets.padForChrome(v.findViewById(R.id.page_scroll), Insets.navClearance(requireContext()), requireContext().getResources().getConfiguration().screenWidthDp >= 840 ? 1100 : 760, false, v.findViewById(R.id.page_appbar));
        androidx.compose.ui.platform.ComposeView compose = v.findViewById(R.id.dashboard_content);
        compose.setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed.INSTANCE);
        DashboardScreenHost.setContent(compose, uiState, this::startBootstrap, this::syncPatches,
                () -> ((MainActivity) requireActivity()).openMods());

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
        if (dev.segually.sbamodloader.BuildConfig.DEBUG && requireActivity().getIntent().getBooleanExtra("sba_launch", false)) {
            requireActivity().getIntent().removeExtra("sba_launch");
            // Debug builds: start the game as the Launch button does, once the game setup has been checked.
            requireView().postDelayed(this::launchBootstrap, 4000);
        }
    }

    /** Shows a card when installed mods have updates. */
    private void checkMods() {
        android.content.Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            dev.segually.sbamodloader.browser.ModHealth h = dev.segually.sbamodloader.browser.ModHealth.check(app, true);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                modUpdates = h.updates.size();
                render();
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
        java.util.List<SetupStep> steps = new java.util.ArrayList<>();
        steps.add(new SetupStep(game ? StepState.OK : StepState.ERROR,
                getString(game ? R.string.step_game_ok : R.string.step_game_missing), null));
        steps.add(new SetupStep(latest ? StepState.OK : StepState.ERROR,
                getString(latest ? R.string.step_version_ok : R.string.step_version_outdated),
                latest ? null : () -> { UpdatePrompt.start(requireActivity(), newerVersion); return kotlin.Unit.INSTANCE; }));

        boolean patchesOk = false;
        boolean failed = false;
        if (!game) {
            steps.add(new SetupStep(StepState.PENDING, getString(R.string.step_patches_no_source), null));
        } else if (patchResult == null) {
            steps.add(new SetupStep(StepState.PENDING, getString(R.string.step_patches_checking), null));
        } else {
            switch (patchResult.state) {
                case INSTALLED -> {
                    patchesOk = true;
                    steps.add(new SetupStep(StepState.OK, getString(R.string.step_patches_ok), null));
                }
                case NOT_AVAILABLE -> steps.add(new SetupStep(StepState.PENDING, getString(R.string.step_patches_not_available), null));
                case OFFLINE -> steps.add(new SetupStep(StepState.PENDING, getString(R.string.step_patches_offline), null));
                case FAILED -> {
                    failed = true;
                    String detail = patchResult.message == null || patchResult.message.isEmpty() ? "" : "\n" + patchResult.message;
                    steps.add(new SetupStep(StepState.ERROR, getString(R.string.step_patches_failed) + detail, null));
                }
            }
        }

        int done = (game ? 1 : 0) + (latest ? 1 : 0) + (patchesOk ? 1 : 0);
        // Launching while a patch is being installed (or failed to install) would start an unpatched game.
        uiState.setValue(new DashboardState(installedGameVersion(), steps, done,
                game && patchResult != null && !failed, failed, modUpdates));
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
