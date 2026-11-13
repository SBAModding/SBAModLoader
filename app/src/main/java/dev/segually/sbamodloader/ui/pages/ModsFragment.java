package dev.segually.sbamodloader.ui.pages;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import dev.segually.sbamodloader.R;
import dev.segually.sbamodloader.TargetGame;
import dev.segually.sbamodloader.tools.Utilities;
import dev.segually.sbamodloader.ui.Insets;

/** Lists BepInEx plugins; disabling a mod renames it to *.dll.disabled. */
public class ModsFragment extends Fragment {
    private static final String DISABLED = ".disabled";
    private final List<File> mods = new ArrayList<>();
    private RecyclerView list;
    private TextView empty;

    private final ActivityResultLauncher<String[]> picker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::importMod);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View v = inflater.inflate(R.layout.fragment_mods, container, false);
        Insets.padForChrome(v.findViewById(R.id.page_root), 0);
        list = v.findViewById(R.id.mods_list);
        list.setPadding(0, 0, 0, Math.round(96 * getResources().getDisplayMetrics().density));
        empty = v.findViewById(R.id.mods_empty);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(new Adapter());
        v.findViewById(R.id.btn_add_mod).setOnClickListener(x -> {
            picker.launch(new String[]{"*/*"});
        });
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    private File pluginsDir() {
        return new File(Utilities.getExternalSBAModLoaderDirectory(TargetGame.PACKAGE_NAME, "BepInEx"), "plugins");
    }

    private void reload() {
        mods.clear();
        File[] files = pluginsDir().listFiles();
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                if (f.isFile() && (n.endsWith(".dll") || n.endsWith(".dll" + DISABLED))) mods.add(f);
            }
            mods.sort(Comparator.comparing(f -> f.getName().toLowerCase()));
        }
        empty.setVisibility(mods.isEmpty() ? View.VISIBLE : View.GONE);
        list.getAdapter().notifyDataSetChanged();
    }

    private void importMod(Uri uri) {
        if (uri == null) return;
        File dir = pluginsDir();
        String name = uri.getLastPathSegment();
        if (name == null || !name.toLowerCase().endsWith(".dll")) {
            Toast.makeText(requireContext(), R.string.mods_copy_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        name = name.substring(name.lastIndexOf('/') + 1).substring(name.lastIndexOf(':') + 1);
        File out = new File(dir, new File(name).getName());
        try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
            if (in == null || (!dir.exists() && !dir.mkdirs())) throw new java.io.IOException();
            try (FileOutputStream os = new FileOutputStream(out)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) os.write(buf, 0, n);
            }
            reload();
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.mods_copy_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mod, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            File f = mods.get(position);
            boolean enabled = !f.getName().endsWith(DISABLED);
            String label = f.getName().replace(DISABLED, "");
            h.name.setText(label);
            h.toggle.setOnCheckedChangeListener(null);
            h.toggle.setChecked(enabled);
            h.toggle.setOnCheckedChangeListener((b, on) -> {
                File target = new File(f.getParentFile(), on ? label : label + DISABLED);
                if (f.renameTo(target)) mods.set(h.getBindingAdapterPosition(), target);
                else b.setChecked(!on);
            });
        }

        @Override
        public int getItemCount() {
            return mods.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView name;
            final MaterialSwitch toggle;

            Holder(View v) {
                super(v);
                name = v.findViewById(R.id.mod_name);
                toggle = v.findViewById(R.id.mod_switch);
            }
        }
    }
}
