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

import dev.segually.sbamodloader.BootstrapActivity;
import dev.segually.sbamodloader.BuildConfig;
import dev.segually.sbamodloader.MainActivity;
import dev.segually.sbamodloader.R;
import dev.segually.sbamodloader.TargetGame;
import dev.segually.sbamodloader.tools.UpdateChecker;
import dev.segually.sbamodloader.tools.PatchManager;
import com.google.android.material.color.MaterialColors;
import dev.segually.sbamodloader.ui.Insets;

public class DashboardFragment extends Fragment {
    private View stepGame, stepVersion, stepPatches;
    private TextView setupTitle;
    private MaterialButton btnLaunch;
    private UpdateChecker.State updateState = UpdateChecker.State.UNKNOWN;
    private PatchManager.Status patchStatus = PatchManager.Status.NO_SOURCE;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View v = inflater.inflate(R.layout.fragment_dashboard, container, false);
        Insets.padForChrome(v.findViewById(R.id.page_scroll), dp(96));
        stepGame = v.findViewById(R.id.step_game);
        stepVersion = v.findViewById(R.id.step_version);
        stepPatches = v.findViewById(R.id.step_patches);
        setupTitle = v.findViewById(R.id.setup_title);
        btnLaunch = v.findViewById(R.id.btn_launch);

        btnLaunch.setOnClickListener(x -> startBootstrap());

        new Thread(() -> {
            UpdateChecker.State s = UpdateChecker.check(BuildConfig.VERSION_NAME);
            if (isAdded()) requireActivity().runOnUiThread(() -> {
                updateState = s;
                refresh();
            });
        }, "update-check").start();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private boolean gameInstalled() {
        try {
            requireContext().getPackageManager().getPackageInfo(TargetGame.PACKAGE_NAME, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void refresh() {
        if (!isAdded()) return;
        boolean game = gameInstalled();
        boolean latest = updateState != UpdateChecker.State.OUTDATED;
        bind(stepGame, game ? State.OK : State.ERROR, getString(game ? R.string.step_game_ok : R.string.step_game_missing));
        bind(stepVersion, latest ? State.OK : State.ERROR, getString(latest ? R.string.step_version_ok : R.string.step_version_outdated));
        applyPatchStatus(game, latest, patchStatus);

        // Hashing the game APK is slow, so the patch status is resolved off the main thread.
        android.content.Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            PatchManager.Status s = game ? PatchManager.status(app) : PatchManager.Status.NO_SOURCE;
            if (isAdded()) requireActivity().runOnUiThread(() -> {
                patchStatus = s;
                applyPatchStatus(game, latest, s);
            });
        }, "patch-status").start();
    }

    private void applyPatchStatus(boolean game, boolean latest, PatchManager.Status status) {
        if (!isAdded()) return;
        switch (status) {
            case INSTALLED -> bind(stepPatches, State.OK, getString(R.string.step_patches_ok));
            case MISSING -> bind(stepPatches, State.ERROR, getString(R.string.step_patches_missing));
            default -> bind(stepPatches, State.PENDING, getString(R.string.step_patches_no_source));
        }
        int done = (game ? 1 : 0) + (latest ? 1 : 0) + (status == PatchManager.Status.INSTALLED ? 1 : 0);
        setupTitle.setText(getString(R.string.setup_progress, done, 3));
        btnLaunch.setEnabled(game && status != PatchManager.Status.MISSING);
    }

    private enum State { OK, ERROR, PENDING }

    private void bind(View step, State state, String text) {
        int iconRes = state == State.OK ? R.drawable.ic_check_circle : state == State.ERROR ? R.drawable.ic_warning : R.drawable.ic_pending;
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
