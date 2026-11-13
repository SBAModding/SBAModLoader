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
import dev.segually.sbamodloader.TargetGame;
import dev.segually.sbamodloader.tools.Utilities;
import dev.segually.sbamodloader.ui.Insets;

public class SettingsFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View v = inflater.inflate(R.layout.fragment_settings, container, false);
        Insets.padForChrome(v.findViewById(R.id.page_scroll), Math.round(96 * getResources().getDisplayMetrics().density));

        MaterialSwitch libunity = v.findViewById(R.id.switch_libunity);
        libunity.setChecked(SBASettings.getUseUnstrippedLibUnity(requireContext()));
        libunity.setOnCheckedChangeListener((b, on) -> SBASettings.setUseUnstrippedLibUnity(requireContext(), on));

        ((TextView) v.findViewById(R.id.data_path)).setText(
                Utilities.getExternalSBAModLoaderDirectory(TargetGame.PACKAGE_NAME, null).getAbsolutePath());
        ((TextView) v.findViewById(R.id.about_version)).setText(getString(R.string.settings_about_format, BuildConfig.VERSION_NAME));
        return v;
    }
}
