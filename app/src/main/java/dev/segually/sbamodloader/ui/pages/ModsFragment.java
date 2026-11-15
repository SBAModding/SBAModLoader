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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import dev.segually.sbamodloader.MainActivity;
import dev.segually.sbamodloader.R;
import dev.segually.sbamodloader.browser.BrowserModels;
import dev.segually.sbamodloader.browser.InstalledMods;
import dev.segually.sbamodloader.browser.RemoteModRepository;
import dev.segually.sbamodloader.tools.ModNames;
import dev.segually.sbamodloader.SBASettings;
import dev.segually.sbamodloader.TargetGame;
import dev.segually.sbamodloader.tools.ModFiles;
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
    /** Mods that came from the store, by id: their names and authors come from the store's copy of the index. */
    private final Map<String, BrowserModels.Entry> storeEntries = new HashMap<>();
    private boolean developer;
    private dev.segually.sbamodloader.ui.FabMenu fabMenu;
    private com.google.android.material.button.MaterialButton addModItem;

    private final ActivityResultLauncher<String[]> picker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::importMod);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View v = inflater.inflate(R.layout.fragment_mods, container, false);
        Insets.padForChrome(v.findViewById(R.id.page_scroll), Insets.navClearance(requireContext()), 760, false);
        list = v.findViewById(R.id.mods_list);
        empty = v.findViewById(R.id.mods_empty);
        int columns = getResources().getInteger(R.integer.mods_columns);
        columns = Math.max(1, columns);
        list.setLayoutManager(columns > 1
                ? new androidx.recyclerview.widget.GridLayoutManager(requireContext(), columns)
                : new LinearLayoutManager(requireContext()));
        if (columns > 1) {
            final int gap = Math.round(6 * getResources().getDisplayMetrics().density);
            list.addItemDecoration(new RecyclerView.ItemDecoration() {
                @Override
                public void getItemOffsets(@NonNull android.graphics.Rect out, @NonNull View view,
                                           @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                    out.left = gap;
                    out.right = gap;
                }
            });
        }
        list.setAdapter(new Adapter());
        touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {
            @Override
            public boolean isLongPressDragEnabled() {
                return false; // dragging starts from the handle
            }

            @Override
            public int getMovementFlags(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                int all = ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;
                return vh.getBindingAdapterPosition() < orderableCount ? makeMovementFlags(all, 0) : 0;
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
        // Adding files by hand skips the store's audit and hash check, so the button only exists with developer
        // features on (see onResume).
        fabMenu = new dev.segually.sbamodloader.ui.FabMenu(v.findViewById(R.id.fab_main), v.findViewById(R.id.fab_items),
                v.findViewById(R.id.fab_scrim));
        addModItem = fabMenu.addItem(R.string.mods_add, R.drawable.ic_add, () -> picker.launch(new String[]{"*/*"}));
        fabMenu.addItem(R.string.mods_browse, R.drawable.ic_nav_browse, () -> ((MainActivity) requireActivity()).openBrowse(null));
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (fabMenu.isOpen()) {
                            fabMenu.close();
                        } else {
                            setEnabled(false);
                            requireActivity().getOnBackPressedDispatcher().onBackPressed();
                        }
                    }
                });
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        developer = SBASettings.getDeveloperFeatures(requireContext());
        addModItem.setVisibility(developer ? View.VISIBLE : View.GONE);
        reload();
        loadStoreNames();
        if (dev.segually.sbamodloader.BuildConfig.DEBUG && requireActivity().getIntent().getBooleanExtra("sba_fab", false)) {
            requireView().postDelayed(() -> fabMenu.open(), 400); // debug builds: open the menu, for layout checks
        }
    }

    /** Reads the store's saved copy of the index (no network) so store mods show their store names. */
    private void loadStoreNames() {
        android.content.Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            Map<String, BrowserModels.Entry> loaded = new HashMap<>();
            for (BrowserModels.Entry e : new RemoteModRepository(app).loadCached()) loaded.put(e.id, e);
            if (isAdded()) requireActivity().runOnUiThread(() -> {
                storeEntries.clear();
                storeEntries.putAll(loaded);
                if (list != null) list.getAdapter().notifyDataSetChanged();
            });
        }, "mods-store-names").start();
    }

    private File pluginsDir() {
        return ModFiles.pluginsDir();
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
                    ModFiles.copy(in, new File(dir, name));
                }
                else ModFiles.unzipInto(in, dir, null);
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

    private void confirmRemove(File f) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.mods_remove_title)
                .setMessage(titleOf(f))
                .setPositiveButton(R.string.mods_remove, (d, w) -> {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                    reload();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Everything shown for one mod. A pack's own strings win, then the store's, then the file name. */
    private static final class Details {
        String title;
        String author;
        String version;
        String storeId;
    }

    private Details details(File f) {
        Details d = new Details();
        String base = ModPacks.baseName(f);
        InstalledMods installed = new InstalledMods(requireContext());
        d.storeId = installed.idForFile(f);
        BrowserModels.Entry store = d.storeId == null ? null : storeEntries.get(d.storeId);
        if (base.endsWith(ModPacks.PACK_EXTENSION)) {
            ModPacks.Info info = ModPacks.info(f);
            d.title = info.name;
            d.author = info.author;
            d.version = info.version;
        }
        if (store != null) {
            if (d.title == null) d.title = store.name;
            if (d.author == null) d.author = store.author;
            if (d.version == null) {
                // The version the browser installed, not the store's newest: an older release may be the one in use.
                InstalledMods.Record record = installed.get(d.storeId);
                d.version = record != null && !record.version.isEmpty() ? record.version : store.version;
            }
        }
        ModNames.Parsed fromFile = ModNames.parse(base);
        if (d.title == null || d.title.isEmpty()) d.title = fromFile.name.isEmpty() ? base : fromFile.name;
        if (d.version == null) d.version = fromFile.version;
        return d;
    }

    private String titleOf(File f) {
        return details(f).title;
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
            boolean orderable = position < orderableCount;
            Details d = details(f);
            dev.segually.sbamodloader.ui.Motion.pressable(h.itemView);
            h.name.setText(d.title);
            h.kind.setText(describe(d, orderable ? position + 1 : 0));
            bindChips(h, f);
            bindIcon(h, d);
            h.badge.setVisibility(d.storeId != null ? View.VISIBLE : View.GONE);
            h.itemView.setAlpha(enabled ? 1f : 0.6f);
            dev.segually.sbamodloader.ui.Motion.appear(h.itemView, position);
            h.file.setVisibility(developer ? View.VISIBLE : View.GONE);
            if (developer) h.file.setText(technical(f));
            // A mod from the store opens its store page when tapped.
            h.itemView.setClickable(d.storeId != null);
            h.itemView.setOnClickListener(d.storeId == null ? null
                    : v -> ((MainActivity) requireActivity()).openBrowse(d.storeId));
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

        /** The line under the name: who made it and which version. */
        private String describe(Details d, int number) {
            List<String> parts = new ArrayList<>();
            if (developer && number > 0) parts.add("#" + number);
            if (d.author != null) parts.add(getString(R.string.mods_by_author, d.author));
            if (d.version != null) parts.add(getString(R.string.mods_version, d.version));
            return String.join(" \u00b7 ", parts);
        }

        /** Small labels for what the mod is made of: native code, Java code, assets, or a plugin. */
        private void bindChips(Holder h, File f) {
            h.chips.removeAllViews();
            String base = ModPacks.baseName(f);
            List<Integer> labels = new ArrayList<>();
            List<Integer> icons = new ArrayList<>();
            if (base.endsWith(ModPacks.PACK_EXTENSION)) {
                for (String p : ModPacks.describe(f)) {
                    labels.add("native".equals(p) ? R.string.mods_chip_native
                            : "java".equals(p) ? R.string.mods_chip_java : R.string.mods_chip_assets);
                    icons.add("assets".equals(p) ? R.drawable.ic_folder : R.drawable.ic_code);
                }
                if (labels.isEmpty()) {
                    labels.add(R.string.mods_chip_pack);
                    icons.add(R.drawable.ic_folder);
                }
            } else if (base.endsWith(".so")) {
                labels.add(R.string.mods_chip_native);
                icons.add(R.drawable.ic_code);
            } else {
                labels.add(R.string.mods_chip_plugin);
                icons.add(R.drawable.ic_code);
            }
            for (int i = 0; i < labels.size(); i++) addChip(h.chips, labels.get(i), icons.get(i));
            if (!base.endsWith(ModPacks.PACK_EXTENSION) && !base.endsWith(".so")
                    && !SBASettings.getFusionEnabled(requireContext())) {
                addChip(h.chips, R.string.mods_needs_fusion, R.drawable.ic_warning);
            }
        }

        private void addChip(ViewGroup into, int label, int icon) {
            TextView chip = (TextView) LayoutInflater.from(into.getContext()).inflate(R.layout.view_chip, into, false);
            chip.setText(label);
            android.graphics.drawable.Drawable d = androidx.appcompat.content.res.AppCompatResources.getDrawable(into.getContext(), icon);
            if (d != null) {
                d = d.mutate();
                int size = Math.round(14 * getResources().getDisplayMetrics().density);
                d.setBounds(0, 0, size, size);
                d.setTint(chip.getCurrentTextColor());
                chip.setCompoundDrawables(d, null, null, null);
            }
            into.addView(chip);
        }

        /** The store's icon when the mod came from the store, otherwise the first letter of its name. */
        private void bindIcon(Holder h, Details d) {
            String letter = d.title == null || d.title.isEmpty() ? "?" : d.title.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
            h.avatar.setText(letter);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(12 * getResources().getDisplayMetrics().density);
            bg.setStroke(Math.round(1.5f * getResources().getDisplayMetrics().density), com.google.android.material.color.MaterialColors.getColor(h.itemView, com.google.android.material.R.attr.colorOutlineVariant));
            bg.setColor(com.google.android.material.color.MaterialColors.getColor(h.itemView,
                    com.google.android.material.R.attr.colorPrimaryContainer));
            h.avatar.setBackground(bg);
            h.avatar.setTextColor(com.google.android.material.color.MaterialColors.getColor(h.itemView,
                    com.google.android.material.R.attr.colorOnPrimaryContainer));
            h.icon.setTag(d.storeId);
            h.icon.setImageDrawable(null);
            h.icon.setVisibility(View.GONE);
            BrowserModels.Entry store = d.storeId == null ? null : storeEntries.get(d.storeId);
            if (store == null || store.icon == null) return;
            dev.segually.sbamodloader.browser.ImageLoader.load(requireContext(), store.icon, 160, bitmap -> {
                if (!d.storeId.equals(h.icon.getTag())) return; // the row was reused for another mod meanwhile
                h.icon.setImageBitmap(bitmap);
                h.icon.setVisibility(View.VISIBLE);
            });
        }

        /** Developer view: the real file name and what is inside. */
        private String technical(File f) {
            String base = ModPacks.baseName(f);
            if (base.endsWith(ModPacks.PACK_EXTENSION)) {
                List<String> parts = new ArrayList<>();
                for (String p : ModPacks.describe(f)) {
                    parts.add(getString("native".equals(p) ? R.string.mods_part_native
                            : "java".equals(p) ? R.string.mods_part_java : R.string.mods_part_assets));
                }
                return base + (parts.isEmpty() ? "" : " \u00b7 " + String.join(", ", parts));
            }
            return base;
        }

        @Override
        public int getItemCount() {
            return mods.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView avatar;
            final android.widget.ImageView icon;
            final View badge;
            final ViewGroup chips;
            final TextView kind;
            final TextView file;
            final MaterialSwitch toggle;
            final View remove;
            final View handle;

            Holder(View v) {
                super(v);
                name = v.findViewById(R.id.mod_name);
                avatar = v.findViewById(R.id.mod_avatar);
                icon = v.findViewById(R.id.mod_icon);
                badge = v.findViewById(R.id.mod_store_badge);
                chips = v.findViewById(R.id.mod_chips);
                kind = v.findViewById(R.id.mod_kind);
                file = v.findViewById(R.id.mod_file);
                toggle = v.findViewById(R.id.mod_switch);
                remove = v.findViewById(R.id.mod_remove);
                handle = v.findViewById(R.id.mod_handle);
            }
        }
    }
}
