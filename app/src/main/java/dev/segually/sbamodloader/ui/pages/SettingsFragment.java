package dev.segually.sbamodloader.ui.pages;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;



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
import dev.segually.sbamodloader.ui.AccentSwatches;
import dev.segually.sbamodloader.ui.compose.SettingsActions;
import dev.segually.sbamodloader.ui.compose.SettingsScreenHost;
import dev.segually.sbamodloader.ui.compose.SettingsState;
import dev.segually.sbamodloader.ui.compose.SourceRow;
import dev.segually.sbamodloader.ui.Theming;

public class SettingsFragment extends Fragment implements SettingsActions {
    /** What the Compose page shows; refresh() rebuilds it from the settings whenever something changes. */
    private final androidx.compose.runtime.MutableState<SettingsState> uiState =
            androidx.compose.runtime.SnapshotStateKt.mutableStateOf(new SettingsState(), androidx.compose.runtime.SnapshotStateKt.structuralEqualityPolicy());
    private int versionTaps;
    private Toast tapToast;
    /** Shown instead of the installed patch while one is being installed. */
    private String patchInfoOverride;
    private String patchUrlError;
    // The state of the "check for updates" button.
    private AppUpdater.Release foundUpdate;
    private String updateStatus;
    private String updateButton;
    private boolean updateButtonEnabled = true;

    private final androidx.activity.result.ActivityResultLauncher<String> exportPicker =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip"),
                    this::exportAssemblies);

    private void exportAssemblies(android.net.Uri uri) {
        if (uri == null) return;
        android.content.Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            String error = null;
            try {
                dev.segually.sbamodloader.tools.InteropExport.export(app, uri);
            } catch (java.io.IOException e) {
                error = e.getMessage() != null ? e.getMessage() : e.toString();
            }
            String err = error;
            if (isAdded()) requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(),
                    err == null ? getString(R.string.export_assemblies_done) : getString(R.string.export_assemblies_failed, err),
                    Toast.LENGTH_LONG).show());
        }, "interop-export").start();
    }

    private final androidx.activity.result.ActivityResultLauncher<String[]> patchPicker =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(), this::loadPatch);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View v = inflater.inflate(R.layout.fragment_settings, container, false);
        updateButton = getString(R.string.updates_check_now);
        androidx.compose.ui.platform.ComposeView compose = v.findViewById(R.id.settings_content);
        compose.setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed.INSTANCE);
        refresh();
        SettingsScreenHost.setContent(compose, uiState, this);
        return v;
    }

    /** Rebuilds what the page shows from the stored settings. */
    private void refresh() {
        if (!isAdded()) return;
        android.content.Context c = requireContext();
        boolean dev = SBASettings.getDeveloperFeatures(c);
        String patch;
        if (patchInfoOverride != null) {
            patch = patchInfoOverride;
        } else {
            PatchManager.Manifest m = PatchManager.installedManifest(c);
            if (m == null) {
                patch = getString(R.string.patch_none_installed);
            } else {
                String name = m.name.isEmpty() ? getString(R.string.patch_unnamed) : m.name;
                patch = getString(R.string.patch_installed_format, name, m.gameVersion)
                        + (m.skipHashVerification ? "\n" + getString(R.string.patch_hash_skipped) : "");
            }
        }
        java.util.List<SourceRow> sources = new java.util.ArrayList<>();
        sources.add(new SourceRow(getString(R.string.sources_official), getString(R.string.sources_official_body), true, true));
        for (dev.segually.sbamodloader.browser.RepoSource r : SBASettings.getExtraRepos(c)) {
            sources.add(new SourceRow(r.name, r.url, r.enabled, false));
        }
        uiState.setValue(new SettingsState(
                SBASettings.getThemeMode(c), SBASettings.getLayoutMode(c),
                Theming.accentSupported(), SBASettings.getMaterialYou(c), SBASettings.getAccentColor(c),
                SBASettings.getAutoUpdate(c), SBASettings.getUpdatePrereleases(c),
                updateStatus, updateButton, updateButtonEnabled,
                SBASettings.getFusionEnabled(c), SBASettings.getSkipFusionWhenUnused(c), SBASettings.getUseUnstrippedLibUnity(c),
                dev, patch, SBASettings.getCustomPatchUrl(c), patchUrlError, sources,
                Utilities.getExternalSBAModLoaderDirectory(TargetGame.PACKAGE_NAME, null).getAbsolutePath(),
                getString(R.string.settings_about_format, BuildConfig.VERSION_NAME) + " (" + BuildConfig.VERSION_CODE + ")",
                buildData()));
    }

    private void loadPatch(android.net.Uri uri) {
        if (uri == null) return;
        android.content.Context app = requireContext().getApplicationContext();
        patchInfoOverride = getString(R.string.patch_installing);
        refresh();
        new Thread(() -> {
            String error = null;
            try {
                PatchManager.installLocal(app, uri);
            } catch (PatchManager.PatchException e) {
                error = e.getMessage();
            }
            String err = error;
            if (isAdded()) requireActivity().runOnUiThread(() -> {
                patchInfoOverride = null;
                refresh();
                Toast.makeText(requireContext(), err == null ? getString(R.string.patch_install_ok) : err, Toast.LENGTH_LONG).show();
            });
        }, "patch-install").start();
    }

    // ---- SettingsActions ----------------------------------------------------------------------------

    @Override
    public void setThemeMode(int mode) {
        android.content.Context app = requireContext().getApplicationContext();
        int chosen = mode == 1 ? SBASettings.THEME_LIGHT : mode == 2 ? SBASettings.THEME_DARK : SBASettings.THEME_SYSTEM;
        if (chosen == SBASettings.getThemeMode(app)) return;
        SBASettings.setThemeMode(app, chosen);
        requireActivity().recreate();
    }

    @Override
    public void setLayoutMode(int mode) {
        android.content.Context app = requireContext().getApplicationContext();
        int chosen = mode == 1 ? SBASettings.LAYOUT_PHONE : mode == 2 ? SBASettings.LAYOUT_DESKTOP : SBASettings.LAYOUT_AUTO;
        if (chosen == SBASettings.getLayoutMode(app)) return;
        SBASettings.setLayoutMode(app, chosen);
        requireActivity().recreate();
    }

    @Override
    public void setMaterialYou(boolean on) {
        SBASettings.setMaterialYou(requireContext().getApplicationContext(), on);
        requireActivity().recreate();
    }

    @Override
    public void setAccent(int argb) {
        SBASettings.setAccentColor(requireContext().getApplicationContext(), argb);
        requireActivity().recreate();
    }

    @Override
    public void pickCustomAccent() {
        AccentSwatches.pickCustom(requireContext(), this::setAccent);
    }

    @Override
    public void setAutoUpdate(boolean on) {
        SBASettings.setAutoUpdate(requireContext(), on);
        refresh();
    }

    @Override
    public void setPrereleases(boolean on) {
        SBASettings.setUpdatePrereleases(requireContext(), on);
        // The answer for the old choice no longer applies.
        foundUpdate = null;
        updateStatus = null;
        updateButton = getString(R.string.updates_check_now);
        refresh();
    }

    @Override
    public void checkForUpdate() {
        if (foundUpdate != null) {
            UpdatePrompt.start(requireActivity(), foundUpdate);
            return;
        }
        updateStatus = getString(R.string.updates_checking);
        updateButtonEnabled = false;
        refresh();
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
                updateButtonEnabled = true;
                if (f) {
                    updateStatus = getString(R.string.updates_check_failed);
                } else if (r == null) {
                    updateStatus = getString(R.string.updates_up_to_date);
                } else {
                    foundUpdate = r;
                    updateStatus = getString(R.string.updates_available, r.version);
                    updateButton = getString(R.string.updates_update_to, r.version);
                }
                refresh();
            });
        }, "update-check").start();
    }

    @Override
    public void setFusion(boolean on) {
        android.content.Context c = requireContext();
        if (on && !DeviceSupport.fusionSupported(c)) {
            // Leave it off until the user has read the warning and confirmed.
            FusionWarning.showTurnOn(c, DeviceSupport.fusionProblems(c), () -> {
                SBASettings.setFusionEnabled(c, true);
                refresh();
            }, () -> { });
        } else {
            SBASettings.setFusionEnabled(c, on);
        }
        refresh();
    }

    @Override
    public void setSkipFusion(boolean on) {
        SBASettings.setSkipFusionWhenUnused(requireContext(), on);
        refresh();
    }

    @Override
    public void setLibunity(boolean on) {
        SBASettings.setUseUnstrippedLibUnity(requireContext(), on);
        refresh();
    }

    @Override
    public void setDeveloper(boolean on) {
        SBASettings.setDeveloperFeatures(requireContext(), on);
        refresh();
    }

    @Override
    public void savePatchUrl(String value) {
        if (!value.isEmpty() && !value.startsWith("https://")) {
            patchUrlError = getString(R.string.settings_patch_url_invalid);
            refresh();
            return;
        }
        patchUrlError = null;
        SBASettings.setCustomPatchUrl(requireContext(), value);
        refresh();
        Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void loadLocalPatch() {
        patchPicker.launch(new String[]{"application/zip", "application/octet-stream", "*/*"});
    }

    @Override
    public void exportAssemblies() {
        android.content.Context c = requireContext();
        if (!dev.segually.sbamodloader.tools.InteropExport.available()) {
            Toast.makeText(c, R.string.export_assemblies_none, Toast.LENGTH_LONG).show();
        } else if (!dev.segually.sbamodloader.tools.InteropExport.generatedHere(c)) {
            Toast.makeText(c, R.string.export_assemblies_downloaded, Toast.LENGTH_LONG).show();
        } else {
            exportPicker.launch(dev.segually.sbamodloader.tools.InteropExport.suggestedName(c));
        }
    }

    @Override
    public void removePatch() {
        PatchManager.remove(requireContext());
        refresh();
    }

    @Override
    public void addRepository() {
        showAddRepoDialog();
    }

    @Override
    public void setRepositoryEnabled(int index, boolean on) {
        android.content.Context c = requireContext();
        java.util.List<dev.segually.sbamodloader.browser.RepoSource> now = SBASettings.getExtraRepos(c);
        if (index < 0 || index >= now.size()) return;
        dev.segually.sbamodloader.browser.RepoSource r = now.get(index);
        now.set(index, new dev.segually.sbamodloader.browser.RepoSource(r.name, r.url, on));
        SBASettings.setExtraRepos(c, now);
        refresh();
    }

    @Override
    public void removeRepository(int index) {
        android.content.Context c = requireContext();
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(c)
                .setTitle(R.string.sources_remove_title)
                .setMessage(getString(R.string.sources_remove_body))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.sources_remove, (d, w) -> {
                    java.util.List<dev.segually.sbamodloader.browser.RepoSource> now = SBASettings.getExtraRepos(c);
                    if (index >= 0 && index < now.size()) now.remove(index);
                    SBASettings.setExtraRepos(c, now);
                    refresh();
                }).show();
    }

    @Override
    public void openGithub() {
        openLink(AppUpdater.REPO_URL);
    }

    @Override
    public void openDiscord() {
        openLink(AppUpdater.DISCORD_URL);
    }

    // ---- mod sources ------------------------------------------------------------------------------

    private void showAddRepoDialog() {
        android.content.Context c = requireContext();
        float d = getResources().getDisplayMetrics().density;
        android.widget.LinearLayout body = new android.widget.LinearLayout(c);
        body.setOrientation(android.widget.LinearLayout.VERTICAL);
        body.setPadding(Math.round(24 * d), Math.round(8 * d), Math.round(24 * d), 0);
        TextView warn = new TextView(c);
        warn.setTextAppearance(R.style.TextAppearance_SBA_BodyMedium);
        warn.setText(R.string.sources_add_warning);
        body.addView(warn);
        com.google.android.material.textfield.TextInputLayout layout = new com.google.android.material.textfield.TextInputLayout(c);
        layout.setHint(getString(R.string.sources_add_hint));
        com.google.android.material.textfield.TextInputEditText input = new com.google.android.material.textfield.TextInputEditText(layout.getContext());
        input.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        input.setSingleLine(true);
        layout.addView(input);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Math.round(16 * d);
        body.addView(layout, lp);
        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(c)
                .setTitle(R.string.sources_add_title)
                .setView(body)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.sources_add_confirm, null)
                .create();
        dialog.show();
        // The dialog stays open when the address is wrong, so the button is wired after it is shown.
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            String url = String.valueOf(input.getText()).trim();
            if (!dev.segually.sbamodloader.browser.RepoSource.validUrl(url, dev.segually.sbamodloader.BuildConfig.DEBUG)) {
                layout.setError(getString(R.string.sources_invalid));
                return;
            }
            for (dev.segually.sbamodloader.browser.RepoSource known : SBASettings.getExtraRepos(c)) {
                if (known.url.equals(url)) {
                    dialog.dismiss();
                    return;
                }
            }
            layout.setError(null);
            layout.setHelperText(getString(R.string.sources_checking));
            android.content.Context app = c.getApplicationContext();
            new Thread(() -> {
                dev.segually.sbamodloader.browser.RepoSource found = null;
                String error = null;
                try {
                    found = dev.segually.sbamodloader.browser.RemoteModRepository.probe(url);
                } catch (java.io.IOException e) {
                    error = e.getMessage() != null ? e.getMessage() : e.toString();
                }
                final dev.segually.sbamodloader.browser.RepoSource source = found;
                final String err = error;
                if (isAdded()) requireActivity().runOnUiThread(() -> {
                    if (source == null) {
                        layout.setHelperText(null);
                        layout.setError(getString(R.string.sources_failed, err));
                        return;
                    }
                    java.util.List<dev.segually.sbamodloader.browser.RepoSource> now = SBASettings.getExtraRepos(app);
                    now.add(source);
                    SBASettings.setExtraRepos(app, now);
                    refresh();
                    dialog.dismiss();
                    Toast.makeText(app, getString(R.string.sources_added, source.name), Toast.LENGTH_SHORT).show();
                });
            }, "repo-probe").start();
        });
    }

    private void openLink(String url) {
        try {
            startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)));
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(requireContext(), url, Toast.LENGTH_LONG).show();
        }
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
    @Override
    public void onVersionTap() {
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
            refresh();
            tapToast = Toast.makeText(requireContext(), R.string.settings_dev_on, Toast.LENGTH_SHORT);
            tapToast.show();
        } else if (left <= 3) {
            tapToast = Toast.makeText(requireContext(), getString(R.string.settings_dev_steps, left), Toast.LENGTH_SHORT);
            tapToast.show();
        }
    }
}
