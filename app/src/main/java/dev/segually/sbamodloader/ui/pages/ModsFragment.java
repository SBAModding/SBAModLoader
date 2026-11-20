package dev.segually.sbamodloader.ui.pages;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

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
import dev.segually.sbamodloader.browser.InstallPlanner;
import dev.segually.sbamodloader.browser.InstalledMods;
import dev.segually.sbamodloader.browser.ModHealth;
import dev.segually.sbamodloader.browser.ModInstaller;
import dev.segually.sbamodloader.browser.ModUpdates;
import dev.segually.sbamodloader.browser.PlanText;
import dev.segually.sbamodloader.browser.RemoteModRepository;
import dev.segually.sbamodloader.tools.ModNames;
import dev.segually.sbamodloader.SBASettings;
import dev.segually.sbamodloader.TargetGame;
import dev.segually.sbamodloader.tools.ModFiles;
import dev.segually.sbamodloader.tools.ModPacks;
import dev.segually.sbamodloader.tools.Utilities;
import dev.segually.sbamodloader.ui.Insets;
import dev.segually.sbamodloader.ui.compose.ModChip;
import dev.segually.sbamodloader.ui.compose.ModRow;
import dev.segually.sbamodloader.ui.compose.ModsActions;
import dev.segually.sbamodloader.ui.compose.ModsScreenHost;
import dev.segually.sbamodloader.ui.compose.ModsState;
import dev.segually.sbamodloader.ui.compose.UpdatesCard;

/**
 * Lists everything that can be added to the game. Native mods and asset packs come first, in load order, and can
 * be dragged to change it (they load, and win asset conflicts, from top to bottom). BepInEx plugins follow.
 * Disabling a mod renames it with a ".disabled" suffix.
 */
public class ModsFragment extends Fragment implements ModsActions {
    private static final String DISABLED = ModPacks.DISABLED;
    private final List<File> mods = new ArrayList<>();
    private int orderableCount;
    /** Mods that came from the store, by id: their names and authors come from the store's copy of the index. */
    private final Map<String, BrowserModels.Entry> storeEntries = new HashMap<>();
    private boolean developer;
    private ModHealth health;
    private final Map<String, ModUpdates.Update> updatesById = new HashMap<>();
    private boolean updating;
    /** The text and progress of the updates card while "Update all" runs. */
    private String updatingText;
    private Float updatingProgress;
    private boolean fabOpen;
    /** Store icons that have been loaded, by mod id. */
    private final Map<String, android.graphics.Bitmap> icons = new HashMap<>();
    private final androidx.compose.runtime.MutableState<ModsState> uiState =
            androidx.compose.runtime.SnapshotStateKt.mutableStateOf(new ModsState(), androidx.compose.runtime.SnapshotStateKt.structuralEqualityPolicy());

    private final ActivityResultLauncher<String[]> picker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::importMod);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View v = inflater.inflate(R.layout.fragment_mods, container, false);
        Insets.padForChrome(v.findViewById(R.id.page_scroll), Insets.navClearance(requireContext()), requireContext().getResources().getConfiguration().screenWidthDp >= 840 ? 1100 : 760, false, v.findViewById(R.id.page_appbar));
        androidx.compose.ui.platform.ComposeView content = v.findViewById(R.id.mods_content);
        content.setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed.INSTANCE);
        androidx.compose.ui.platform.ComposeView fab = v.findViewById(R.id.mods_fab);
        fab.setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed.INSTANCE);
        ModsScreenHost.setContent(content, uiState, this);
        ModsScreenHost.setFab(fab, uiState, this);
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (fabOpen) {
                            setFabOpen(false);
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
        reload();
        loadStoreNames();
        checkHealth(true);
        if (dev.segually.sbamodloader.BuildConfig.DEBUG && requireActivity().getIntent().getBooleanExtra("sba_settings", false)) {
            requireActivity().getIntent().removeExtra("sba_settings");
            requireView().postDelayed(() -> { // debug builds: open the first mod's settings, for layout checks
                for (File f : mods) {
                    dev.segually.sbamodloader.tools.ModSettings declared = dev.segually.sbamodloader.tools.ModSettingsStore.schemaFor(f);
                    if (declared != null) {
                        try {
                            dev.segually.sbamodloader.tools.ModSettingsStore.set(declared, "speed", "2.2");
                            dev.segually.sbamodloader.tools.ModSettingsStore.set(declared, "mode", "fast");
                            dev.segually.sbamodloader.tools.ModSettingsStore.set(declared, "name", "line\nbreak");
                        } catch (java.io.IOException e) {
                            android.util.Log.e("ModsFragment", "debug settings", e);
                        }
                        dev.segually.sbamodloader.ui.ModSettingsSheet.show(requireContext(), details(f).title, declared);
                        return;
                    }
                }
            }, 500);
        }
        if (dev.segually.sbamodloader.BuildConfig.DEBUG && requireActivity().getIntent().getBooleanExtra("sba_fab", false)) {
            requireView().postDelayed(() -> setFabOpen(true), 400); // debug builds: open the menu, for layout checks
        }
    }

    /** Reads the store's saved copy of the index (no network) so store mods show their store names. */
    private void loadStoreNames() {
        android.content.Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            Map<String, BrowserModels.Entry> loaded = new HashMap<>();
            for (BrowserModels.Entry e : dev.segually.sbamodloader.browser.Repositories.load(app, false).entries) loaded.put(e.id, e);
            if (isAdded()) requireActivity().runOnUiThread(() -> {
                storeEntries.clear();
                storeEntries.putAll(loaded);
                refresh();
            });
        }, "mods-store-names").start();
    }

    // ---- updates and problems -----------------------------------------------------------------

    /** Looks for updates and for mods that do not fit together. Reads the saved store first, then asks the network. */
    private void checkHealth(boolean network) {
        android.content.Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            ModHealth quick = ModHealth.check(app, false);
            post(() -> applyHealth(quick));
            if (network) {
                ModHealth fresh = ModHealth.check(app, true);
                post(() -> applyHealth(fresh));
            }
        }, "mods-health").start();
    }

    private void post(Runnable r) {
        if (isAdded()) requireActivity().runOnUiThread(() -> {
            if (isAdded() && getView() != null) r.run();
        });
    }

    private void applyHealth(ModHealth h) {
        health = h;
        updatesById.clear();
        for (ModUpdates.Update u : h.updates) updatesById.put(u.entry.id, u);
        refresh();
        if (dev.segually.sbamodloader.BuildConfig.DEBUG && requireActivity().getIntent().getBooleanExtra("sba_updateall", false)
                && !h.updates.isEmpty() && !updating) {
            requireActivity().getIntent().removeExtra("sba_updateall");
            requireView().post(this::runUpdateAll); // debug builds: press Update all
        }
        if (dev.segually.sbamodloader.BuildConfig.DEBUG && requireActivity().getIntent().getBooleanExtra("sba_fix", false)
                && h.hasProblems() && !h.fixes.blocked()) {
            requireActivity().getIntent().removeExtra("sba_fix");
            final android.content.Context app = requireContext().getApplicationContext();
            new Thread(() -> { // debug builds: press Fix automatically
                try {
                    ModInstaller.install(app, h.fixes, (mod, done, total) -> { });
                    android.util.Log.i("SBA.Test", "sba_fix done: " + h.fixes.steps.size() + " steps");
                } catch (Exception e) {
                    android.util.Log.e("SBA.Test", "sba_fix failed", e);
                }
                post(() -> { reload(); checkHealth(true); });
            }, "debug-fix").start();
        }
        if (dev.segually.sbamodloader.BuildConfig.DEBUG && requireActivity().getIntent().getBooleanExtra("sba_problems", false)) {
            requireActivity().getIntent().removeExtra("sba_problems");
            requireView().post(this::showProblems); // debug builds: open the list, for layout checks
        }
    }

    /** Updates every mod that has an update, one after the other, with a progress bar in the updates card. */
    private void runUpdateAll() {
        if (updating || health == null || health.updates.isEmpty()) return;
        updating = true;
        updatingProgress = null;
        updatingText = null;
        refresh();
        final android.content.Context app = requireContext().getApplicationContext();
        final List<ModUpdates.Update> todo = new ArrayList<>(health.updates);
        final Map<String, BrowserModels.Entry> index = new HashMap<>(health.index);
        final String game = health.gameVersion;
        new Thread(() -> {
            String error = null;
            for (int i = 0; i < todo.size(); i++) {
                ModUpdates.Update u = todo.get(i);
                final int number = i + 1;
                post(() -> { updatingText = getString(R.string.updates_mods_working, u.entry.name, number, todo.size()); refresh(); });
                try {
                    InstallPlanner.Plan plan = InstallPlanner.plan(u.entry, u.target, index,
                            ModInstaller.installedVersions(app, index.values()), game);
                    ModInstaller.install(app, plan, (mod, done, total) -> {
                        if (total > 0) post(() -> { updatingProgress = (float) done / total; refresh(); });
                    });
                } catch (Exception e) {
                    android.util.Log.e("ModsFragment", "Update of " + u.entry.id + " failed", e);
                    error = getStringSafe(app, u.entry.name, e);
                    break;
                }
            }
            final String err = error;
            post(() -> {
                updating = false;
                updatingText = null;
                updatingProgress = null;
                if (err != null) Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show();
                reload();
                checkHealth(true);
            });
        }, "mods-update-all").start();
    }

    private static String getStringSafe(android.content.Context app, String name, Exception e) {
        return app.getString(R.string.updates_mods_failed, name, e.getMessage() != null ? e.getMessage() : e.toString());
    }

    /** The list of problems, with a button that fixes them when the store has everything that is needed. */
    private void showProblems() {
        if (health == null) return;
        final ModHealth h = health;
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());
        LinearLayout column = new LinearLayout(requireContext());
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        column.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(requireContext());
        title.setText(R.string.problems_title);
        title.setTextAppearance(R.style.TextAppearance_SBA_HeadlineSmallEmphasized);
        column.addView(title);

        for (InstallPlanner.Issue issue : h.fixes.issues) {
            TextView row = new TextView(requireContext());
            row.setText(PlanText.issue(requireContext(), issue, h.gameVersion));
            row.setTextAppearance(R.style.TextAppearance_SBA_BodyLarge);
            int p = Math.round(16 * getResources().getDisplayMetrics().density);
            row.setPadding(p, p, p, p);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(20 * getResources().getDisplayMetrics().density);
            bg.setColor(com.google.android.material.color.MaterialColors.getColor(column,
                    com.google.android.material.R.attr.colorSurfaceContainerHigh));
            row.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Math.round(10 * getResources().getDisplayMetrics().density);
            column.addView(row, lp);
        }

        TextView hint = new TextView(requireContext());
        hint.setTextAppearance(R.style.TextAppearance_SBA_BodyMedium);
        boolean canFix = !h.fixes.blocked() && !h.fixes.steps.isEmpty();
        if (h.fixes.blocked()) {
            hint.setText(R.string.problems_fix_none);
        } else {
            List<String> names = new ArrayList<>();
            for (InstallPlanner.Step step : h.fixes.steps) names.add(step.entry.name + " " + step.version.version);
            hint.setText(getString(R.string.problems_fix_hint, String.join(", ", names)));
        }
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = Math.round(16 * getResources().getDisplayMetrics().density);
        column.addView(hint, hlp);

        com.google.android.material.button.MaterialButton fix = new com.google.android.material.button.MaterialButton(requireContext());
        fix.setText(R.string.problems_fix);
        fix.setEnabled(canFix);
        fix.setIconResource(R.drawable.ic_check);
        fix.setIconGravity(com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START);
        fix.setTextAppearance(R.style.TextAppearance_SBA_TitleMediumEmphasized);
        fix.setTextColor(com.google.android.material.color.MaterialColors.getColor(column,
                com.google.android.material.R.attr.colorOnPrimary));
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(64 * getResources().getDisplayMetrics().density));
        flp.topMargin = Math.round(16 * getResources().getDisplayMetrics().density);
        column.addView(fix, flp);
        fix.setOnClickListener(x -> {
            fix.setEnabled(false);
            final android.content.Context app = requireContext().getApplicationContext();
            new Thread(() -> {
                String error = null;
                try {
                    ModInstaller.install(app, h.fixes, (mod, done, total) -> { });
                } catch (Exception e) {
                    error = e.getMessage() != null ? e.getMessage() : e.toString();
                }
                final String err = error;
                post(() -> {
                    sheet.dismiss();
                    Toast.makeText(requireContext(), err == null ? getString(R.string.problems_fixed) : err, Toast.LENGTH_LONG).show();
                    reload();
                    checkHealth(true);
                });
            }, "mods-fix").start();
        });
        sheet.setContentView(column);
        sheet.show();
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
        refresh();
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
                    // A store mod is forgotten as well, with every file it put in place; otherwise the store would
                    // keep saying it is installed.
                    InstalledMods installed = new InstalledMods(requireContext());
                    String storeId = installed.idForFile(f);
                    InstalledMods.Record record = storeId == null ? null : installed.get(storeId);
                    if (record != null) {
                        for (File part : record.files) {
                            //noinspection ResultOfMethodCallIgnored
                            new File(part.getParentFile(), ModPacks.baseName(part)).delete();
                            //noinspection ResultOfMethodCallIgnored
                            new File(part.getParentFile(), ModPacks.baseName(part) + DISABLED).delete();
                        }
                        installed.remove(storeId);
                    }
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                    reload();
                    checkHealth(false);
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

    // ---- what the page shows -------------------------------------------------------------------

    /** Rebuilds what the Compose page shows from the file list, the store and the health check. */
    private void refresh() {
        if (!isAdded()) return;
        List<ModRow> rows = new ArrayList<>();
        for (int position = 0; position < mods.size(); position++) {
            File f = mods.get(position);
            boolean orderable = position < orderableCount;
            Details d = details(f);
            BrowserModels.Entry fromStore = d.storeId == null ? null : storeEntries.get(d.storeId);
            String letter = d.title == null || d.title.isEmpty() ? "?" : d.title.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
            rows.add(new ModRow(f.getAbsolutePath(), d.title, describe(d, orderable ? position + 1 : 0), letter,
                    d.storeId == null ? null : icons.get(d.storeId),
                    d.storeId == null ? 0 : fromStore != null && !fromStore.official() ? 2 : 1,
                    chips(f), !f.getName().endsWith(DISABLED), orderable,
                    dev.segually.sbamodloader.tools.ModSettingsStore.schemaFor(f) != null,
                    d.storeId != null, developer ? technical(f) : null));
            if (d.storeId != null && fromStore != null && fromStore.icon != null && !icons.containsKey(d.storeId)) loadIcon(d.storeId, fromStore);
        }
        UpdatesCard updatesCard = null;
        String problems = null;
        if (health != null) {
            if (!health.updates.isEmpty()) {
                String body = updating && updatingText != null ? updatingText
                        : health.updates.size() == 1 ? getString(R.string.updates_mods_body_one)
                        : getString(R.string.updates_mods_body, health.updates.size());
                updatesCard = new UpdatesCard(body, updating, updatingProgress);
            }
            if (health.hasProblems()) {
                problems = health.fixes.issues.size() == 1 ? getString(R.string.problems_body_one)
                        : getString(R.string.problems_body, health.fixes.issues.size());
            }
        }
        uiState.setValue(new ModsState(getString(R.string.mods_order_hint), updatesCard, problems, rows, developer, fabOpen));
    }

    private void loadIcon(String storeId, BrowserModels.Entry store) {
        icons.put(storeId, null); // asked for, so it is not asked for again while it loads
        dev.segually.sbamodloader.browser.ImageLoader.load(requireContext(), store.imageBase, store.icon, 160, bitmap -> {
            if (!isAdded()) return;
            icons.put(storeId, bitmap);
            refresh();
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
    private List<ModChip> chips(File f) {
        List<ModChip> chips = new ArrayList<>();
        String base = ModPacks.baseName(f);
        if (base.endsWith(ModPacks.PACK_EXTENSION)) {
            List<String> described = ModPacks.describe(f);
            for (String p : described) {
                chips.add(new ModChip(getString("native".equals(p) ? R.string.mods_chip_native
                        : "java".equals(p) ? R.string.mods_chip_java : R.string.mods_chip_assets),
                        "assets".equals(p) ? R.drawable.ic_folder : R.drawable.ic_code));
            }
            if (chips.isEmpty()) chips.add(new ModChip(getString(R.string.mods_chip_pack), R.drawable.ic_folder));
        } else if (base.endsWith(".so")) {
            chips.add(new ModChip(getString(R.string.mods_chip_native), R.drawable.ic_code));
        } else {
            chips.add(new ModChip(getString(R.string.mods_chip_plugin), R.drawable.ic_code));
        }
        String storeId = new InstalledMods(requireContext()).idForFile(f);
        ModUpdates.Update update = storeId == null ? null : updatesById.get(storeId);
        if (update != null) chips.add(new ModChip(getString(R.string.updates_chip, update.target.version), R.drawable.ic_download));
        if (!base.endsWith(ModPacks.PACK_EXTENSION) && !base.endsWith(".so") && !SBASettings.getFusionEnabled(requireContext())) {
            chips.add(new ModChip(getString(R.string.mods_needs_fusion), R.drawable.ic_warning));
        }
        return chips;
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

    // ---- ModsActions ----------------------------------------------------------------------------

    private int indexOf(String key) {
        for (int i = 0; i < mods.size(); i++) if (mods.get(i).getAbsolutePath().equals(key)) return i;
        return -1;
    }

    @Override
    public void toggle(String key, boolean on) {
        int pos = indexOf(key);
        if (pos < 0) return;
        File current = mods.get(pos);
        File target = new File(current.getParentFile(), on ? ModPacks.baseName(current) : ModPacks.baseName(current) + DISABLED);
        if (current.renameTo(target)) mods.set(pos, target);
        refresh();
    }

    @Override
    public void remove(String key) {
        int pos = indexOf(key);
        if (pos >= 0) confirmRemove(mods.get(pos));
    }

    @Override
    public void openSettings(String key) {
        int pos = indexOf(key);
        if (pos < 0) return;
        File f = mods.get(pos);
        dev.segually.sbamodloader.tools.ModSettings declared = dev.segually.sbamodloader.tools.ModSettingsStore.schemaFor(f);
        if (declared != null) dev.segually.sbamodloader.ui.ModSettingsSheet.show(requireContext(), details(f).title, declared);
    }

    @Override
    public void open(String key) {
        int pos = indexOf(key);
        if (pos < 0) return;
        String storeId = details(mods.get(pos)).storeId;
        if (storeId != null) ((MainActivity) requireActivity()).openBrowse(storeId);
    }

    @Override
    public void move(int from, int to) {
        if (from < 0 || to < 0 || from >= orderableCount || to >= orderableCount || from == to) return;
        mods.add(to, mods.remove(from));
        refresh();
    }

    @Override
    public void dragFinished() {
        ModPacks.saveOrder(requireContext(), mods.subList(0, orderableCount));
        refresh(); // the position numbers
    }

    @Override
    public void updateAll() {
        runUpdateAll();
    }

    @Override
    public void reviewProblems() {
        showProblems();
    }

    @Override
    public void addByHand() {
        picker.launch(new String[]{"*/*"});
    }

    @Override
    public void browse() {
        ((MainActivity) requireActivity()).openBrowse(null);
    }

    @Override
    public void setFabOpen(boolean open) {
        fabOpen = open;
        refresh();
    }
}
