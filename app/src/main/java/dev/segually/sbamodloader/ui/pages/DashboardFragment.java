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
import dev.segually.sbamodloader.BuildConfig;
import dev.segually.sbamodloader.MainActivity;
import dev.segually.sbamodloader.R;
import dev.segually.sbamodloader.TargetGame;
import dev.segually.sbamodloader.tools.PatchManager;
import dev.segually.sbamodloader.tools.UpdateChecker;
import dev.segually.sbamodloader.ui.Insets;

public class DashboardFragment extends Fragment {
    private enum State { OK, ERROR, PENDING }

    private View stepGame, stepVersion, stepPatches;
    private TextView setupTitle;
    private MaterialButton btnRetry, btnLaunch;
    private UpdateChecker.State updateState = UpdateChecker.State.UNKNOWN;

    /** Null while the patch source is being checked. */
    private PatchManager.SyncResult patchResult;
    private boolean patchSyncing;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View v = inflater.inflate(R.layout.fragment_dashboard, container, false);
        Insets.padForChrome(v.findViewById(R.id.page_scroll), dp(96));
        stepGame = v.findViewById(R.id.step_game);
        stepVersion = v.findViewById(R.id.step_version);
        stepPatches = v.findViewById(R.id.step_patches);
        setupTitle = v.findViewById(R.id.setup_title);
        btnRetry = v.findViewById(R.id.btn_retry_patches);
        btnLaunch = v.findViewById(R.id.btn_launch);

        btnRetry.setOnClickListener(x -> syncPatches());
        btnLaunch.setOnClickListener(x -> startBootstrap());

        new Thread(() -> {
            UpdateChecker.State s = UpdateChecker.check(BuildConfig.VERSION_NAME);
            if (isAdded()) requireActivity().runOnUiThread(() -> {
                updateState = s;
                render();
            });
        }, "update-check").start();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        syncPatches();
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
        boolean latest = updateState != UpdateChecker.State.OUTDATED;
        bind(stepGame, game ? State.OK : State.ERROR, getString(game ? R.string.step_game_ok : R.string.step_game_missing));
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
