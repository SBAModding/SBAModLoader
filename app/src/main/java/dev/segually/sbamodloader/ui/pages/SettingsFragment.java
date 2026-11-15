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
import dev.segually.sbamodloader.ui.AccentSwatches;
import dev.segually.sbamodloader.ui.Insets;
import dev.segually.sbamodloader.ui.Theming;

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
        Insets.padForChrome(v.findViewById(R.id.page_scroll), Insets.navClearance(requireContext()), 720, false, v.findViewById(R.id.page_appbar));

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
        setUpAppearance(v);
        setUpSources(v);
        rowToggles(v, R.id.card_libunity, R.id.switch_libunity);
        rowToggles(v, R.id.card_fusion, R.id.switch_fusion);
        rowToggles(v, R.id.card_skipfusion, R.id.switch_skipfusion);
        rowToggles(v, R.id.card_developer, R.id.switch_developer);
        rowToggles(v, R.id.row_autoupdate, R.id.switch_autoupdate);
        rowToggles(v, R.id.row_prereleases, R.id.switch_prereleases);

        devSection = v.findViewById(R.id.dev_section);
        patchInfo = v.findViewById(R.id.patch_info);
        devSwitch = v.findViewById(R.id.switch_developer);
        devSwitch.setChecked(SBASettings.getDeveloperFeatures(requireContext()));
        devSwitch.setOnCheckedChangeListener((b, on) -> {
            SBASettings.setDeveloperFeatures(requireContext(), on);
            refreshDeveloper();
        });
        v.findViewById(R.id.btn_load_patch).setOnClickListener(x -> patchPicker.launch(new String[]{"application/zip", "application/octet-stream", "*/*"}));
        v.findViewById(R.id.btn_export_assemblies).setOnClickListener(x -> {
            android.content.Context c = requireContext();
            if (!dev.segually.sbamodloader.tools.InteropExport.available()) {
                Toast.makeText(c, R.string.export_assemblies_none, Toast.LENGTH_LONG).show();
            } else if (!dev.segually.sbamodloader.tools.InteropExport.generatedHere(c)) {
                Toast.makeText(c, R.string.export_assemblies_downloaded, Toast.LENGTH_LONG).show();
            } else {
                exportPicker.launch(dev.segually.sbamodloader.tools.InteropExport.suggestedName(c));
            }
        });
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

    // ---- mod sources ------------------------------------------------------------------------------

    private void setUpSources(View v) {
        android.widget.LinearLayout list = v.findViewById(R.id.sources_list);
        renderSources(list);
        v.findViewById(R.id.btn_add_repo).setOnClickListener(x -> showAddRepo(list));
    }

    private void renderSources(android.widget.LinearLayout list) {
        list.removeAllViews();
        android.content.Context c = requireContext();
        list.addView(sourceRow(getString(R.string.sources_official), getString(R.string.sources_official_body), true, null, null));
        final java.util.List<dev.segually.sbamodloader.browser.RepoSource> extra = SBASettings.getExtraRepos(c);
        for (int i = 0; i < extra.size(); i++) {
            final int index = i;
            dev.segually.sbamodloader.browser.RepoSource r = extra.get(i);
            list.addView(sourceRow(r.name, r.url, r.enabled, on -> {
                java.util.List<dev.segually.sbamodloader.browser.RepoSource> now = SBASettings.getExtraRepos(c);
                now.set(index, new dev.segually.sbamodloader.browser.RepoSource(r.name, r.url, on));
                SBASettings.setExtraRepos(c, now);
            }, () -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(c)
                    .setTitle(R.string.sources_remove_title)
                    .setMessage(getString(R.string.sources_remove_body))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.sources_remove, (d, w) -> {
                        java.util.List<dev.segually.sbamodloader.browser.RepoSource> now = SBASettings.getExtraRepos(c);
                        now.remove(index);
                        SBASettings.setExtraRepos(c, now);
                        renderSources(list);
                    }).show()));
        }
    }

    private interface Toggled {
        void on(boolean enabled);
    }

    /** One repository: its name, its address, and a switch and a remove button (the official one has neither). */
    private View sourceRow(String name, String detail, boolean enabled, Toggled toggled, Runnable remove) {
        android.content.Context c = requireContext();
        float d = getResources().getDisplayMetrics().density;
        android.widget.LinearLayout row = new android.widget.LinearLayout(c);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(Math.round(20 * d), Math.round(12 * d), Math.round(12 * d), Math.round(12 * d));
        row.setMinimumHeight(Math.round(72 * d));
        android.widget.LinearLayout col = new android.widget.LinearLayout(c);
        col.setOrientation(android.widget.LinearLayout.VERTICAL);
        TextView title = new TextView(c);
        title.setTextAppearance(R.style.TextAppearance_SBA_RowTitle);
        title.setText(name);
        col.addView(title);
        TextView sub = new TextView(c);
        sub.setTextAppearance(R.style.TextAppearance_SBA_RowBody);
        sub.setText(detail);
        sub.setMaxLines(2);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        col.addView(sub);
        row.addView(col, new android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (toggled == null) {
            android.widget.ImageView lock = new android.widget.ImageView(c);
            lock.setImageResource(R.drawable.ic_verified);
            lock.setImageTintList(android.content.res.ColorStateList.valueOf(
                    com.google.android.material.color.MaterialColors.getColor(row, androidx.appcompat.R.attr.colorPrimary)));
            row.addView(lock, new android.widget.LinearLayout.LayoutParams(Math.round(28 * d), Math.round(28 * d)));
        } else {
            MaterialSwitch sw = new MaterialSwitch(c);
            sw.setChecked(enabled);
            sw.setOnCheckedChangeListener((b, on) -> toggled.on(on));
            row.addView(sw);
            android.widget.ImageButton del = new android.widget.ImageButton(c);
            del.setImageResource(R.drawable.ic_delete);
            del.setBackgroundResource(android.R.color.transparent);
            del.setContentDescription(getString(R.string.sources_remove));
            del.setImageTintList(android.content.res.ColorStateList.valueOf(
                    com.google.android.material.color.MaterialColors.getColor(row, com.google.android.material.R.attr.colorOnSurfaceVariant)));
            del.setOnClickListener(x -> remove.run());
            row.addView(del, new android.widget.LinearLayout.LayoutParams(Math.round(48 * d), Math.round(48 * d)));
        }
        return row;
    }

    private void showAddRepo(android.widget.LinearLayout list) {
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
                    renderSources(list);
                    dialog.dismiss();
                    Toast.makeText(app, getString(R.string.sources_added, source.name), Toast.LENGTH_SHORT).show();
                });
            }, "repo-probe").start();
        });
    }

    /** Makes the whole row toggle its switch, not just the switch itself. */
    private static void rowToggles(View root, int rowId, int switchId) {
        View row = root.findViewById(rowId);
        MaterialSwitch sw = root.findViewById(switchId);
        row.setOnClickListener(x -> sw.toggle());
    }

    private void setUpAppearance(View v) {
        android.content.Context app = requireContext().getApplicationContext();
        com.google.android.material.button.MaterialButtonToggleGroup group = v.findViewById(R.id.theme_mode_group);
        int mode = SBASettings.getThemeMode(app);
        group.check(mode == SBASettings.THEME_LIGHT ? R.id.theme_light
                : mode == SBASettings.THEME_DARK ? R.id.theme_dark : R.id.theme_system);
        group.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            int chosen = id == R.id.theme_light ? SBASettings.THEME_LIGHT
                    : id == R.id.theme_dark ? SBASettings.THEME_DARK : SBASettings.THEME_SYSTEM;
            if (chosen == SBASettings.getThemeMode(app)) return;
            SBASettings.setThemeMode(app, chosen);
            requireActivity().recreate();
        });

        com.google.android.material.button.MaterialButtonToggleGroup layoutGroup = v.findViewById(R.id.layout_mode_group);
        int layoutMode = SBASettings.getLayoutMode(app);
        layoutGroup.check(layoutMode == SBASettings.LAYOUT_PHONE ? R.id.layout_phone
                : layoutMode == SBASettings.LAYOUT_DESKTOP ? R.id.layout_desktop : R.id.layout_auto);
        layoutGroup.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            int chosen = id == R.id.layout_phone ? SBASettings.LAYOUT_PHONE
                    : id == R.id.layout_desktop ? SBASettings.LAYOUT_DESKTOP : SBASettings.LAYOUT_AUTO;
            if (chosen == SBASettings.getLayoutMode(app)) return;
            SBASettings.setLayoutMode(app, chosen);
            requireActivity().recreate();
        });

        View materialYouRow = v.findViewById(R.id.row_material_you);
        MaterialSwitch materialYou = v.findViewById(R.id.switch_material_you);
        View accentRow = v.findViewById(R.id.row_accent);
        boolean dynamic = Theming.accentSupported();
        // Without dynamic colours (Android 11 and older) there is nothing to choose: the app's own colours are used.
        materialYouRow.setVisibility(dynamic ? View.VISIBLE : View.GONE);
        accentRow.setVisibility(dynamic ? View.VISIBLE : View.GONE);
        materialYou.setChecked(SBASettings.getMaterialYou(app));
        rowToggles(v, R.id.row_material_you, R.id.switch_material_you);
        AccentSwatches swatches = new AccentSwatches(requireContext(), v.findViewById(R.id.accent_swatches),
                () -> requireActivity().recreate());
        Runnable showAccent = () -> {
            boolean you = SBASettings.getMaterialYou(app);
            v.findViewById(R.id.accent_scroll).setAlpha(you ? 0.38f : 1f);
            swatches.setEnabled(!you);
            ((TextView) v.findViewById(R.id.accent_hint)).setText(
                    you ? R.string.appearance_accent_off : R.string.appearance_accent_desc);
        };
        showAccent.run();
        materialYou.setOnCheckedChangeListener((b, on) -> {
            SBASettings.setMaterialYou(app, on);
            showAccent.run();
            requireActivity().recreate();
        });
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
