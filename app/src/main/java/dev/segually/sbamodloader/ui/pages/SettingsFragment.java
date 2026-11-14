package dev.segually.sbamodloader.ui.pages;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.materialswitch.MaterialSwitch;


import dev.segually.sbamodloader.BuildConfig;
import dev.segually.sbamodloader.R;
import dev.segually.sbamodloader.SBASettings;
import dev.segually.sbamodloader.tools.PatchManager;
import dev.segually.sbamodloader.tools.DeviceSupport;
import dev.segually.sbamodloader.tools.AppUpdater;
import dev.segually.sbamodloader.ui.FusionWarning;
import dev.segually.sbamodloader.ui.UpdatePrompt;
import android.widget.Toast;
import dev.segually.sbamodloader.TargetGame;
import dev.segually.sbamodloader.tools.Utilities;
import dev.segually.sbamodloader.ui.Insets;

public class SettingsFragment extends Fragment {
    private View devSection;
    private View devCard;
    private View libunityCard;
    private View dataLabel;
    private View dataPath;
    private MaterialSwitch devSwitch;
    private TextView patchInfo;
    private int versionTaps;
    private Toast tapToast;

    private final androidx.activity.result.ActivityResultLauncher<String[]> patchPicker =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(), this::loadPatch);

    private void refreshDeveloper() {
        boolean on = SBASettings.getDeveloperFeatures(requireContext());
        int shown = on ? View.VISIBLE : View.GONE;
        // Anything technical, or that only exists for developers, is only there once developer features are on.
        devSection.setVisibility(shown);
        devCard.setVisibility(shown);
        libunityCard.setVisibility(shown);
        dataLabel.setVisibility(shown);
        dataPath.setVisibility(shown);
        PatchManager.Manifest m = PatchManager.installedManifest(requireContext());
        if (m == null) {
            patchInfo.setText(R.string.patch_none_installed);
        } else {
            String name = m.name.isEmpty() ? getString(R.string.patch_unnamed) : m.name;
            patchInfo.setText(getString(R.string.patch_installed_format, name, m.gameVersion)
                    + (m.skipHashVerification ? "\n" + getString(R.string.patch_hash_skipped) : ""));
        }
    }

    private void loadPatch(android.net.Uri uri) {
        if (uri == null) return;
        android.content.Context app = requireContext().getApplicationContext();
        patchInfo.setText(R.string.patch_installing);
        new Thread(() -> {
            String error = null;
            try {
                PatchManager.installLocal(app, uri);
            } catch (PatchManager.PatchException e) {
                error = e.getMessage();
            }
            String err = error;
            if (isAdded()) requireActivity().runOnUiThread(() -> {
                refreshDeveloper();
                Toast.makeText(requireContext(), err == null ? getString(R.string.patch_install_ok) : err, Toast.LENGTH_LONG).show();
            });
        }, "patch-install").start();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View v = inflater.inflate(R.layout.fragment_settings, container, false);
        Insets.padForChrome(v.findViewById(R.id.page_scroll), Insets.navClearance(requireContext()));

        devCard = v.findViewById(R.id.card_developer);
        libunityCard = v.findViewById(R.id.card_libunity);
        dataLabel = v.findViewById(R.id.data_label);
        dataPath = v.findViewById(R.id.data_path);
        MaterialSwitch libunity = v.findViewById(R.id.switch_libunity);
        libunity.setChecked(SBASettings.getUseUnstrippedLibUnity(requireContext()));
        libunity.setOnCheckedChangeListener((b, on) -> SBASettings.setUseUnstrippedLibUnity(requireContext(), on));

        MaterialSwitch fusion = v.findViewById(R.id.switch_fusion);
        fusion.setChecked(SBASettings.getFusionEnabled(requireContext()));
        final android.widget.CompoundButton.OnCheckedChangeListener[] fusionListener = new android.widget.CompoundButton.OnCheckedChangeListener[1];
        fusionListener[0] = (b, on) -> {
            if (on && !DeviceSupport.fusionSupported(requireContext())) {
                // Put the switch back until the user has read the warning and confirmed.
                b.setOnCheckedChangeListener(null);
                b.setChecked(false);
                b.setOnCheckedChangeListener(fusionListener[0]);
                FusionWarning.showTurnOn(requireContext(), DeviceSupport.fusionProblems(requireContext()), () -> {
                    SBASettings.setFusionEnabled(requireContext(), true);
                    b.setOnCheckedChangeListener(null);
                    b.setChecked(true);
                    b.setOnCheckedChangeListener(fusionListener[0]);
                }, () -> { });
            } else {
                SBASettings.setFusionEnabled(requireContext(), on);
            }
        };
        fusion.setOnCheckedChangeListener(fusionListener[0]);

        MaterialSwitch skipFusion = v.findViewById(R.id.switch_skipfusion);
        skipFusion.setChecked(SBASettings.getSkipFusionWhenUnused(requireContext()));
        skipFusion.setOnCheckedChangeListener((b, on) -> SBASettings.setSkipFusionWhenUnused(requireContext(), on));

        v.findViewById(R.id.safety_github).setOnClickListener(x -> openLink(AppUpdater.REPO_URL));
        v.findViewById(R.id.safety_discord).setOnClickListener(x -> openLink(AppUpdater.DISCORD_URL));
        setUpUpdates(v);

        devSection = v.findViewById(R.id.dev_section);
        patchInfo = v.findViewById(R.id.patch_info);
        devSwitch = v.findViewById(R.id.switch_developer);
        devSwitch.setChecked(SBASettings.getDeveloperFeatures(requireContext()));
        devSwitch.setOnCheckedChangeListener((b, on) -> {
            SBASettings.setDeveloperFeatures(requireContext(), on);
            refreshDeveloper();
        });
        v.findViewById(R.id.btn_load_patch).setOnClickListener(x -> patchPicker.launch(new String[]{"application/zip", "application/octet-stream", "*/*"}));
        v.findViewById(R.id.btn_remove_patch).setOnClickListener(x -> {
            PatchManager.remove(requireContext());
            refreshDeveloper();
        });
        com.google.android.material.textfield.TextInputEditText url = v.findViewById(R.id.patch_url);
        com.google.android.material.textfield.TextInputLayout urlLayout = v.findViewById(R.id.patch_url_layout);
        url.setText(SBASettings.getCustomPatchUrl(requireContext()));
        v.findViewById(R.id.btn_save_patch_url).setOnClickListener(x -> {
            String value = String.valueOf(url.getText()).trim();
            if (!value.isEmpty() && !value.startsWith("https://")) {
                urlLayout.setError(getString(R.string.settings_patch_url_invalid));
                return;
            }
            urlLayout.setError(null);
            SBASettings.setCustomPatchUrl(requireContext(), value);
            Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show();
        });
        refreshDeveloper();

        ((TextView) dataPath).setText(
                Utilities.getExternalSBAModLoaderDirectory(TargetGame.PACKAGE_NAME, null).getAbsolutePath());

        TextView version = v.findViewById(R.id.about_version);
        version.setText(getString(R.string.settings_about_format, BuildConfig.VERSION_NAME) + " (" + BuildConfig.VERSION_CODE + ")");
        version.setOnClickListener(x -> onVersionTapped());
        ((TextView) v.findViewById(R.id.about_build)).setText(buildData());
        return v;
    }

    private void openLink(String url) {
        try {
            startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)));
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(requireContext(), url, Toast.LENGTH_LONG).show();
        }
    }

    private void setUpUpdates(View v) {
        MaterialSwitch auto = v.findViewById(R.id.switch_autoupdate);
        auto.setChecked(SBASettings.getAutoUpdate(requireContext()));
        auto.setOnCheckedChangeListener((b, on) -> SBASettings.setAutoUpdate(requireContext(), on));

        MaterialSwitch pre = v.findViewById(R.id.switch_prereleases);
        pre.setChecked(SBASettings.getUpdatePrereleases(requireContext()));
        TextView status = v.findViewById(R.id.update_status);
        android.widget.Button button = v.findViewById(R.id.btn_check_update);
        final AppUpdater.Release[] found = new AppUpdater.Release[1];
        pre.setOnCheckedChangeListener((b, on) -> {
            SBASettings.setUpdatePrereleases(requireContext(), on);
            // The answer for the old choice no longer applies.
            found[0] = null;
            status.setVisibility(View.GONE);
            button.setText(R.string.updates_check_now);
        });

        button.setOnClickListener(x -> {
            if (found[0] != null) {
                UpdatePrompt.start(requireActivity(), found[0]);
                return;
            }
            status.setVisibility(View.VISIBLE);
            status.setText(R.string.updates_checking);
            button.setEnabled(false);
            android.content.Context app = requireContext().getApplicationContext();
            boolean prereleases = SBASettings.getUpdatePrereleases(app);
            new Thread(() -> {
                AppUpdater.Release release = null;
                boolean failed = false;
                try {
                    release = AppUpdater.findNewer(prereleases);
                    SBASettings.setLastUpdateCheck(app, System.currentTimeMillis());
                } catch (java.io.IOException e) {
                    failed = true;
                }
                AppUpdater.Release r = release;
                boolean f = failed;
                if (isAdded()) requireActivity().runOnUiThread(() -> {
                    button.setEnabled(true);
                    if (f) {
                        status.setText(R.string.updates_check_failed);
                    } else if (r == null) {
                        status.setText(R.string.updates_up_to_date);
                    } else {
                        found[0] = r;
                        status.setText(getString(R.string.updates_available, r.version));
                        button.setText(getString(R.string.updates_update_to, r.version));
                    }
                });
            }, "update-check").start();
        });
    }

    /** Version, build and device lines for the bottom of the page; useful to quote when reporting a problem. */
    private String buildData() {
        java.util.List<String> lines = new java.util.ArrayList<>();
        String type = BuildConfig.BUILD_TYPE;
        lines.add(getString(R.string.settings_build_type, type.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + type.substring(1)));
        lines.add(getString(R.string.settings_build_commit, BuildConfig.GIT_SHA));
        lines.add(getString(R.string.settings_build_time, java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(new java.util.Date(BuildConfig.BUILD_TIME))));
        lines.add(getString(R.string.settings_build_android, android.os.Build.VERSION.RELEASE, android.os.Build.VERSION.SDK_INT));
        try {
            String game = requireContext().getPackageManager().getPackageInfo(TargetGame.PACKAGE_NAME, 0).versionName;
            lines.add(getString(R.string.settings_build_game, game));
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            lines.add(getString(R.string.settings_build_game_missing));
        }
        return String.join("\n", lines);
    }

    /** Seven taps on the version number turn on developer features, like Android's build number. */
    private void onVersionTapped() {
        if (tapToast != null) tapToast.cancel();
        if (SBASettings.getDeveloperFeatures(requireContext())) {
            tapToast = Toast.makeText(requireContext(), R.string.settings_dev_already, Toast.LENGTH_SHORT);
            tapToast.show();
            return;
        }
        versionTaps++;
        int left = 7 - versionTaps;
        if (left <= 0) {
            versionTaps = 0;
            SBASettings.setDeveloperFeatures(requireContext(), true);
            devSwitch.setChecked(true);
            refreshDeveloper();
            tapToast = Toast.makeText(requireContext(), R.string.settings_dev_on, Toast.LENGTH_SHORT);
            tapToast.show();
        } else if (left <= 3) {
            tapToast = Toast.makeText(requireContext(), getString(R.string.settings_dev_steps, left), Toast.LENGTH_SHORT);
            tapToast.show();
        }
    }
}
