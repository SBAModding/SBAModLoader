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
import android.widget.Toast;
import dev.segually.sbamodloader.TargetGame;
import dev.segually.sbamodloader.tools.Utilities;
import dev.segually.sbamodloader.ui.Insets;

public class SettingsFragment extends Fragment {
    private View devSection;
    private TextView patchInfo;

    private final androidx.activity.result.ActivityResultLauncher<String[]> patchPicker =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(), this::loadPatch);

    private void refreshDeveloper() {
        boolean on = SBASettings.getDeveloperFeatures(requireContext());
        devSection.setVisibility(on ? View.VISIBLE : View.GONE);
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
        Insets.padForChrome(v.findViewById(R.id.page_scroll), Math.round(96 * getResources().getDisplayMetrics().density));

        MaterialSwitch libunity = v.findViewById(R.id.switch_libunity);
        libunity.setChecked(SBASettings.getUseUnstrippedLibUnity(requireContext()));
        libunity.setOnCheckedChangeListener((b, on) -> SBASettings.setUseUnstrippedLibUnity(requireContext(), on));

        MaterialSwitch skipFusion = v.findViewById(R.id.switch_skipfusion);
        skipFusion.setChecked(SBASettings.getSkipFusionWhenUnused(requireContext()));
        skipFusion.setOnCheckedChangeListener((b, on) -> SBASettings.setSkipFusionWhenUnused(requireContext(), on));

        devSection = v.findViewById(R.id.dev_section);
        patchInfo = v.findViewById(R.id.patch_info);
        MaterialSwitch dev = v.findViewById(R.id.switch_developer);
        dev.setChecked(SBASettings.getDeveloperFeatures(requireContext()));
        dev.setOnCheckedChangeListener((b, on) -> {
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

        ((TextView) v.findViewById(R.id.data_path)).setText(
                Utilities.getExternalSBAModLoaderDirectory(TargetGame.PACKAGE_NAME, null).getAbsolutePath());
        ((TextView) v.findViewById(R.id.about_version)).setText(getString(R.string.settings_about_format, BuildConfig.VERSION_NAME));
        return v;
    }
}
