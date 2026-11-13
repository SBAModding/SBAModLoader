package dev.segually.sbamodloader.ui.pages;

import android.net.Uri;
import android.os.Bundle;
import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dev.segually.sbamodloader.R;
import dev.segually.sbamodloader.TargetGame;
import dev.segually.sbamodloader.tools.ModPacks;
import dev.segually.sbamodloader.tools.Utilities;
import dev.segually.sbamodloader.ui.Insets;

/**
 * Lists everything that can be added to the game. Native mods and asset packs come first, in load order, and can
 * be dragged to change it (they load, and win asset conflicts, from top to bottom). BepInEx plugins follow.
 * Disabling a mod renames it with a ".disabled" suffix.
 */
public class ModsFragment extends Fragment {
    private static final String DISABLED = ModPacks.DISABLED;
    private final List<File> mods = new ArrayList<>();
    private int orderableCount;
    private RecyclerView list;
    private TextView empty;
    private ItemTouchHelper touchHelper;

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
        touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean isLongPressDragEnabled() {
                return false; // dragging starts from the handle
            }

            @Override
            public int getMovementFlags(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                return vh.getBindingAdapterPosition() < orderableCount
                        ? makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) : 0;
            }

            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder from, @NonNull RecyclerView.ViewHolder to) {
                int a = from.getBindingAdapterPosition();
                int b = to.getBindingAdapterPosition();
                if (a < 0 || b < 0 || a >= orderableCount || b >= orderableCount) return false;
                mods.add(b, mods.remove(a));
                list.getAdapter().notifyItemMoved(a, b);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
            }

            @Override
            public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(rv, vh);
                ModPacks.saveOrder(requireContext(), mods.subList(0, orderableCount));
                list.getAdapter().notifyItemRangeChanged(0, orderableCount); // refresh the position numbers
            }
        });
        touchHelper.attachToRecyclerView(list);
        v.findViewById(R.id.btn_add_mod).setOnClickListener(x -> picker.launch(new String[]{"*/*"}));
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
        mods.addAll(ModPacks.orderedMods(requireContext()));
        orderableCount = mods.size();
        List<File> plugins = new ArrayList<>();
        File[] files = pluginsDir().listFiles();
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                if (f.isFile() && (n.endsWith(".dll") || n.endsWith(".dll" + DISABLED))) plugins.add(f);
            }
        }
        plugins.sort(Comparator.comparing(f -> f.getName().toLowerCase()));
        mods.addAll(plugins);
        empty.setVisibility(mods.isEmpty() ? View.VISIBLE : View.GONE);
        list.getAdapter().notifyDataSetChanged();
    }

    private String displayName(Uri uri) {
        try (android.database.Cursor c = requireContext().getContentResolver()
                .query(uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String n = c.getString(0);
                if (n != null && !n.isEmpty()) return new File(n).getName();
            }
        } catch (Exception ignored) {
        }
        String seg = uri.getLastPathSegment();
        return seg == null ? null : new File(seg).getName();
    }

    /** Adds a mod: a plain .dll, or a .zip whose contents are unpacked into the plugins folder. */
    private void importMod(Uri uri) {
        if (uri == null) return;
        String name = displayName(uri);
        String lower = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        if (!lower.endsWith(".dll") && !lower.endsWith(".zip") && !lower.endsWith(".so")
                && !lower.endsWith(ModPacks.PACK_EXTENSION)) {
            Toast.makeText(requireContext(), R.string.mods_unsupported, Toast.LENGTH_SHORT).show();
            return;
        }
        android.content.Context app = requireContext().getApplicationContext();
        File dir = lower.endsWith(".so") ? ModPacks.nativeModsDir()
                : lower.endsWith(ModPacks.PACK_EXTENSION) ? ModPacks.packsDir() : pluginsDir();
        new Thread(() -> {
            boolean ok;
            try (InputStream in = app.getContentResolver().openInputStream(uri)) {
                if (in == null || (!dir.exists() && !dir.mkdirs())) throw new java.io.IOException("no stream");
                if (lower.endsWith(".dll") || lower.endsWith(".so") || lower.endsWith(ModPacks.PACK_EXTENSION)) {
                    copy(in, new File(dir, name));
                }
                else unzipInto(in, dir);
                ok = true;
            } catch (Exception e) {
                android.util.Log.e("ModsFragment", "Failed to import mod " + name, e);
                ok = false;
            }
            boolean success = ok;
            if (isAdded()) requireActivity().runOnUiThread(() -> {
                if (!success) Toast.makeText(requireContext(), R.string.mods_copy_failed, Toast.LENGTH_SHORT).show();
                reload();
            });
        }, "mod-import").start();
    }

    private static void copy(InputStream in, File out) throws java.io.IOException {
        try (FileOutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) os.write(buf, 0, n);
        }
    }

    private static void unzipInto(InputStream in, File dir) throws java.io.IOException {
        String root = dir.getCanonicalPath() + File.separator;
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(in)) {
            java.util.zip.ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                File out = new File(dir, e.getName());
                if (!out.getCanonicalPath().startsWith(root)) continue; // zip-slip guard
                if (e.isDirectory()) {
                    out.mkdirs();
                } else {
                    File parent = out.getParentFile();
                    if (parent != null) parent.mkdirs();
                    copy(zis, out);
                }
            }
        }
    }

    private void confirmRemove(File f) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.mods_remove_title)
                .setMessage(f.getName().replace(DISABLED, ""))
                .setPositiveButton(R.string.mods_remove, (d, w) -> {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                    reload();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mod, parent, false));
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            File f = mods.get(position);
            boolean enabled = !f.getName().endsWith(DISABLED);
            String label = ModPacks.baseName(f);
            boolean orderable = position < orderableCount;
            h.name.setText(label);
            h.kind.setText(describe(f, orderable ? position + 1 : 0));
            h.handle.setVisibility(orderable ? View.VISIBLE : View.GONE);
            h.handle.setOnTouchListener((v, e) -> {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) touchHelper.startDrag(h);
                return false;
            });
            h.toggle.setOnCheckedChangeListener(null);
            h.toggle.setChecked(enabled);
            h.remove.setOnClickListener(v -> confirmRemove(mods.get(h.getBindingAdapterPosition())));
            h.toggle.setOnCheckedChangeListener((b, on) -> {
                int pos = h.getBindingAdapterPosition();
                if (pos < 0) return;
                File current = mods.get(pos);
                File target = new File(current.getParentFile(), on ? ModPacks.baseName(current) : ModPacks.baseName(current) + DISABLED);
                if (current.renameTo(target)) mods.set(pos, target);
                else b.setChecked(!on);
            });
        }

        private String describe(File f, int number) {
            String base = ModPacks.baseName(f);
            String kind;
            if (base.endsWith(ModPacks.PACK_EXTENSION)) {
                List<String> parts = new ArrayList<>();
                for (String p : ModPacks.describe(f)) {
                    parts.add(getString("native".equals(p) ? R.string.mods_part_native
                            : "java".equals(p) ? R.string.mods_part_java : R.string.mods_part_assets));
                }
                kind = parts.isEmpty() ? getString(R.string.mods_kind_pack_empty)
                        : getString(R.string.mods_kind_pack, String.join(", ", parts));
            } else if (base.endsWith(".so")) {
                kind = getString(R.string.mods_kind_native);
            } else {
                kind = getString(R.string.mods_kind_plugin);
            }
            return number > 0 ? "#" + number + " \u00b7 " + kind : kind;
        }

        @Override
        public int getItemCount() {
            return mods.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView kind;
            final MaterialSwitch toggle;
            final View remove;
            final View handle;

            Holder(View v) {
                super(v);
                name = v.findViewById(R.id.mod_name);
                kind = v.findViewById(R.id.mod_kind);
                toggle = v.findViewById(R.id.mod_switch);
                remove = v.findViewById(R.id.mod_remove);
                handle = v.findViewById(R.id.mod_handle);
            }
        }
    }
}
