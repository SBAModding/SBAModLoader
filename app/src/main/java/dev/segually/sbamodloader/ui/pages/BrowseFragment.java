package dev.segually.sbamodloader.ui.pages;

import android.graphics.Color;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

import dev.segually.sbamodloader.R;
import dev.segually.sbamodloader.browser.BrowseQuery;
import dev.segually.sbamodloader.browser.BrowserModels.Category;
import dev.segually.sbamodloader.browser.BrowserModels.Dependency;
import dev.segually.sbamodloader.browser.BrowserModels.Entry;
import dev.segually.sbamodloader.browser.BrowserModels.Kind;
import dev.segually.sbamodloader.browser.BrowserModels.Sub;
import dev.segually.sbamodloader.browser.BrowserModels.Version;
import dev.segually.sbamodloader.browser.ImageLoader;
import dev.segually.sbamodloader.browser.InstallPlanner;
import dev.segually.sbamodloader.browser.ModInstaller;
import dev.segually.sbamodloader.browser.ModVersions;
import dev.segually.sbamodloader.ui.compose.Badge;
import dev.segually.sbamodloader.ui.compose.BrowseHeader;
import dev.segually.sbamodloader.ui.compose.BrowseHeaderActions;
import dev.segually.sbamodloader.ui.compose.BrowseItem;
import dev.segually.sbamodloader.ui.compose.BrowseListActions;
import dev.segually.sbamodloader.ui.compose.BrowseListState;
import dev.segually.sbamodloader.ui.compose.BrowseNav;
import dev.segually.sbamodloader.ui.compose.BrowseScreenHost;
import dev.segually.sbamodloader.ui.compose.DetailActions;
import dev.segually.sbamodloader.ui.compose.DetailState;
import dev.segually.sbamodloader.ui.compose.InfoLine;
import dev.segually.sbamodloader.ui.compose.InstallUi;
import dev.segually.sbamodloader.ui.compose.Reason;
import dev.segually.sbamodloader.ui.compose.StatTile;
import dev.segually.sbamodloader.ui.compose.SubChip;
import dev.segually.sbamodloader.ui.compose.ViewerRequest;

/**
 * The mod browser. On a wide (landscape) layout the search, filters and list sit on the left and the selected
 * mod on the right; in portrait the list and the detail are separate pages and back returns to the list.
 */
public class BrowseFragment extends Fragment implements BrowseHeaderActions, BrowseListActions, DetailActions {
    /** Argument: the id of a mod whose store page to open as soon as the list has loaded. */
    public static final String ARG_MOD_ID = "mod_id";
    private final BrowseQuery query = new BrowseQuery();
    private boolean offlineCopy;
    /**
     * Mods being installed right now. Static because an install keeps running when the user leaves the Browse tab:
     * a new BrowseFragment must still know about it, or it would offer to start the same install again.
     */
    private static final Set<String> busy = java.util.Collections.synchronizedSet(new HashSet<>());
    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());
    /** The Browse fragment currently on screen, if any: where a running install reports back to. */
    private static java.lang.ref.WeakReference<BrowseFragment> visible = new java.lang.ref.WeakReference<>(null);
    private List<Entry> all = new ArrayList<>();
    private String selectedId;
    /** The release picked for each mod on this page (mod id to version); a mod without an entry shows its default. */
    private final Map<String, String> chosen = new HashMap<>();
    /** Store icons that have been loaded, by mod id. */
    private final Map<String, android.graphics.Bitmap> icons = new HashMap<>();
    /** The text of a running install, and how far its download is, 0 to 100 (0 shows an indeterminate bar). */
    private String installStatus;
    private int installPercent;
    private String loadMessage;
    private boolean loadFailed;
    private boolean loading;
    private int kindChoice;
    private int categoryChoice;

    /** True when the list and the store page share the screen (landscape, wide windows); otherwise they take turns. */
    private boolean twoPane() {
        android.content.res.Configuration c = getResources().getConfiguration();
        return c.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE || c.screenWidthDp >= 840;
    }

    private final androidx.compose.runtime.MutableState<BrowseHeader> headerState = state(new BrowseHeader());
    private final androidx.compose.runtime.MutableState<BrowseListState> listState = state(new BrowseListState());
    private final androidx.compose.runtime.MutableState<DetailState> detailState = state(null);
    private final androidx.compose.runtime.MutableState<ViewerRequest> viewerState = state(null);
    private final androidx.compose.runtime.MutableState<BrowseNav> navState = state(new BrowseNav(false));

    private static <T> androidx.compose.runtime.MutableState<T> state(T initial) {
        return androidx.compose.runtime.SnapshotStateKt.mutableStateOf(initial, androidx.compose.runtime.SnapshotStateKt.structuralEqualityPolicy());
    }

    private final OnBackPressedCallback backCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            closeDetail();
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View v = inflater.inflate(R.layout.fragment_browse, container, false);
        visible = new java.lang.ref.WeakReference<>(this);
        androidx.compose.ui.platform.ComposeView root = v.findViewById(R.id.browse_root);
        BrowseScreenHost.root(root, headerState, this, listState, this, detailState, this, getString(R.string.browse_select_prompt),
                navState, this::closeDetail, viewerState, () -> viewerState.setValue(null));
        updateHeader();
        if (!twoPane()) requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backCallback);

        loadRepository();
        return v;
    }

    @Override
    public void onDestroyView() {
        if (visible.get() == this) visible = new java.lang.ref.WeakReference<>(null);
        super.onDestroyView();
    }

    /** Runs {@code action} on the main thread against the Browse fragment on screen; does nothing when there is none. */
    private static void onUi(java.util.function.Consumer<BrowseFragment> action) {
        MAIN.post(() -> {
            BrowseFragment f = visible.get();
            if (f == null || !f.isAdded() || f.getView() == null) return;
            try {
                action.accept(f);
            } catch (RuntimeException ex) {
                android.util.Log.w("BrowseFragment", "Could not update the install state", ex);
            }
        });
    }

    private void loadRepository() {
        loading = true;
        loadFailed = false;
        updateHeader();
        updateList();
        final android.content.Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            dev.segually.sbamodloader.browser.Repositories.Result read = dev.segually.sbamodloader.browser.Repositories.load(app, true);
            // The official repository is the one that matters: without it (and without a saved copy) nothing is shown.
            List<Entry> result = read.officialFailed && read.entries.isEmpty() ? null : read.entries;
            boolean cached = read.anyFromCache;
            if (isAdded()) requireActivity().runOnUiThread(() -> {
                loading = false;
                if (result == null) {
                    loadFailed = true;
                    updateHeader();
                    updateList();
                    return;
                }
                all = result;
                offlineCopy = cached;
                refresh();
                Bundle args = getArguments();
                String wanted = args == null ? null : args.getString(ARG_MOD_ID);
                if (wanted != null) {
                    args.remove(ARG_MOD_ID); // only once; coming back to the list later should not reopen it
                    for (Entry candidate : all) if (candidate.id.equals(wanted)) select(candidate);
                }
            });
        }, "browse-load").start();
    }

    /** Opens a mod's store page when this tab is shown again; before the list has loaded, once it has. */
    public void openMod(String id) {
        for (Entry candidate : all) {
            if (candidate.id.equals(id)) {
                select(candidate);
                return;
            }
        }
        Bundle args = getArguments();
        if (args == null) args = new Bundle();
        args.putString(ARG_MOD_ID, id);
        setArguments(args);
    }

    // ---- BrowseHeaderActions -----------------------------------------------------------------------

    @Override
    public void setQuery(@NonNull String text) {
        query.text = text;
        refresh();
    }

    @Override
    public void setKind(int kind) {
        kindChoice = kind;
        query.kind = kind == 1 ? Kind.FUSION : kind == 2 ? Kind.NATIVE : kind == 3 ? Kind.PACK : null;
        refresh();
    }

    @Override
    public void setCategory(int category) {
        categoryChoice = category;
        query.category = category == 1 ? Category.TEXTURE_PACK : category == 2 ? Category.MOD : null;
        query.subs.clear();
        refresh();
    }

    @Override
    public void toggleSub(int index, boolean checked) {
        if (query.category == null) return;
        Sub sub = Sub.of(query.category).get(index);
        if (checked) query.subs.add(sub);
        else query.subs.remove(sub);
        refresh();
    }

    @Override
    public void setSort(int index) {
        query.sort = BrowseQuery.Sort.values()[index];
        refresh();
    }

    // ---- BrowseListActions -------------------------------------------------------------------------

    @Override
    public void open(@NonNull String id) {
        for (Entry e : all) if (e.id.equals(id)) select(e);
    }

    @Override
    public void retry() {
        loadRepository();
    }

    // ---- what the page shows -------------------------------------------------------------------------

    private void refresh() {
        if (!isAdded()) return;
        updateHeader();
        updateList();
    }

    private void updateHeader() {
        if (!isAdded()) return;
        List<Entry> shown = all.isEmpty() && (loading || loadFailed) ? new ArrayList<>() : query.apply(all);
        String count;
        if (loading) count = getString(R.string.browse_loading);
        else if (loadFailed) count = getString(R.string.browse_load_failed);
        else {
            count = shown.size() == 1 ? getString(R.string.browse_result_one) : getString(R.string.browse_results, shown.size());
            if (offlineCopy) count = count + " \u00b7 " + getString(R.string.browse_offline_copy);
        }
        List<SubChip> subs = new ArrayList<>();
        if (query.category != null) {
            for (Sub sub : Sub.of(query.category)) subs.add(new SubChip(subLabel(sub), query.subs.contains(sub)));
        }
        BrowseQuery.Sort[] sorts = BrowseQuery.Sort.values();
        List<String> options = new ArrayList<>();
        for (BrowseQuery.Sort s : sorts) options.add(sortLabel(s));
        headerState.setValue(new BrowseHeader(query.text == null ? "" : query.text, kindChoice, categoryChoice, subs, count,
                getString(R.string.browse_sort_format, sortLabel(query.sort)), options, query.sort.ordinal()));
    }

    private void updateList() {
        if (!isAdded()) return;
        if (loadFailed) {
            listState.setValue(new BrowseListState(new ArrayList<>(), getString(R.string.browse_load_failed_detail), true));
            return;
        }
        List<Entry> shown = loading ? new ArrayList<>() : query.apply(all);
        List<BrowseItem> items = new ArrayList<>();
        String game = installedGameVersion();
        for (Entry e : shown) {
            StringBuilder stats = new StringBuilder();
            if (e.ratingCount > 0) stats.append(String.format(Locale.US, "\u2605 %.1f  \u00b7  ", e.rating));
            if (e.downloads > 0) stats.append(compact(e.downloads)).append("  \u00b7  ");
            stats.append(Formatter.formatShortFileSize(requireContext(), e.size));
            ModInstaller.State installed = ModInstaller.state(requireContext(), e);
            if (busy.contains(e.id)) stats.append("  \u00b7  ").append(getString(R.string.detail_working));
            else if (installed == ModInstaller.State.INSTALLED) stats.append("  \u00b7  ").append(getString(R.string.detail_installed));
            else if (installed == ModInstaller.State.UPDATE_AVAILABLE) stats.append("  \u00b7  ").append(getString(R.string.detail_update_available));
            if (game != null && ModVersions.bestFor(e, game) == null) stats.append("  \u00b7  ").append(getString(R.string.detail_other_game));
            items.add(new BrowseItem(e.id, e.name.substring(0, 1).toUpperCase(Locale.ROOT),
                    new kotlin.Pair<>(tint(e.id, 0.55f), tint(e.id + "b", 0.35f)), iconOf(e, 112),
                    e.name, e.author + " \u00b7 " + kindLabel(e.kind) + (e.official() ? "" : " \u00b7 " + e.repoName),
                    e.official(), e.summary, stats.toString(), e.id.equals(selectedId) && twoPane()));
        }
        String message = null;
        if (!loading && items.isEmpty()) message = getString(all.isEmpty() ? R.string.browse_repository_empty : R.string.browse_no_results);
        listState.setValue(new BrowseListState(items, message, false));
    }

    /** A mod's store icon once it has loaded (the load is started the first time it is asked for). */
    @Nullable
    private android.graphics.Bitmap iconOf(Entry e, int pixels) {
        if (e.icon == null) return null;
        if (icons.containsKey(e.id)) return icons.get(e.id);
        icons.put(e.id, null);
        ImageLoader.load(requireContext(), e.imageBase, e.icon, 256, bitmap -> {
            if (!isAdded()) return;
            icons.put(e.id, bitmap);
            updateList();
            if (e.id.equals(selectedId)) updateDetail();
        });
        return null;
    }

    // ---- detail ---------------------------------------------------------------------------------

    private void select(Entry e) {
        selectedId = e.id;
        updateDetail();
        if (!twoPane()) {
            navState.setValue(new BrowseNav(true));
            backCallback.setEnabled(true);
        } else {
            updateList();
        }
        if (dev.segually.sbamodloader.BuildConfig.DEBUG && requireActivity().getIntent().getBooleanExtra("sba_viewer", false) && !e.screenshots.isEmpty()) {
            requireActivity().getIntent().removeExtra("sba_viewer");
            requireView().postDelayed(() -> openViewer(Math.min(1, e.screenshots.size() - 1)), 800); // debug builds: open the viewer
        }
    }

    private void closeDetail() {
        if (twoPane()) return;
        navState.setValue(new BrowseNav(false));
        backCallback.setEnabled(false);
    }

    private String installedGameVersion() {
        try {
            return requireContext().getPackageManager().getPackageInfo(dev.segually.sbamodloader.TargetGame.PACKAGE_NAME, 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    @Nullable
    private Entry selected() {
        if (selectedId == null) return null;
        for (Entry e : all) if (e.id.equals(selectedId)) return e;
        return null;
    }

    /** The release shown for {@code e}: the one the user picked, otherwise the newest that fits the installed game. */
    private Version chosenVersion(Entry e) {
        Version v = e.version(chosen.get(e.id));
        return v != null ? v : ModVersions.defaultFor(e, installedGameVersion());
    }

    /** The project owner's name; their mods say so on the safety card. */
    private static final String PROJECT_OWNER = "Segually";

    private void updateDetail() {
        Entry e = selected();
        if (e == null || !isAdded()) {
            detailState.setValue(null);
            return;
        }
        final Version cv = chosenVersion(e);
        android.content.Context c = requireContext();
        List<Badge> badges = new ArrayList<>();
        if (e.official()) badges.add(new Badge(getString(R.string.browse_audited), R.drawable.ic_verified, true));
        else badges.add(new Badge(getString(R.string.browse_unverified), R.drawable.ic_warning, false));
        badges.add(new Badge(getString(e.openSource ? R.string.detail_open_source_short : R.string.detail_closed_source_short),
                e.openSource ? R.drawable.ic_code : R.drawable.ic_lock, false));
        String installedGame = installedGameVersion();
        if (installedGame != null && !cv.supportsGame(installedGame)) {
            badges.add(new Badge(getString(R.string.detail_for_version, String.join(", ", cv.gameVersions)), R.drawable.ic_warning, false));
        }

        List<StatTile> stats = new ArrayList<>();
        // Only what the repository actually has (it carries no download counts or ratings yet).
        if (e.ratingCount > 0) stats.add(new StatTile(String.format(Locale.US, "%.1f \u2605", e.rating), getString(R.string.detail_rating_count, e.ratingCount)));
        if (e.downloads > 0) stats.add(new StatTile(compact(e.downloads), getString(R.string.detail_downloads)));
        stats.add(new StatTile(Formatter.formatShortFileSize(c, cv.size), getString(R.string.detail_size)));
        stats.add(new StatTile(DateUtils.getRelativeTimeSpanString(cv.updatedMillis, System.currentTimeMillis(),
                DateUtils.DAY_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString(), getString(R.string.detail_updated).toLowerCase(Locale.ROOT)));

        List<Reason> reasons = new ArrayList<>();
        for (dev.segually.sbamodloader.browser.SafetyReasons.Reason reason
                : dev.segually.sbamodloader.browser.SafetyReasons.build(e, cv, PROJECT_OWNER)) {
            reasons.add(reasonOf(reason));
        }
        // The checksum means nothing to most people; it is there for developers.
        String hash = dev.segually.sbamodloader.SBASettings.getDeveloperFeatures(c) ? cv.sha256 : null;

        List<String> categories = new ArrayList<>();
        for (Sub s : e.categories) categories.add(categoryLabel(s.parent) + " \u203a " + subLabel(s));

        List<InfoLine> info = new ArrayList<>();
        info.add(new InfoLine(getString(R.string.detail_type), kindLabel(e.kind)));
        info.add(new InfoLine(getString(R.string.detail_version), cv.version));
        info.add(new InfoLine(getString(R.string.detail_game_versions), String.join(", ", cv.gameVersions)));
        info.add(new InfoLine(getString(R.string.detail_license), e.license));
        if (!cv.dependencies.isEmpty()) {
            List<String> needs = new ArrayList<>();
            for (Dependency d : cv.dependencies) needs.add(dependencyLabel(d));
            info.add(new InfoLine(getString(R.string.detail_requires), String.join("\n", needs)));
        } else if (!e.requires.isEmpty() && cv == e.latest()) {
            info.add(new InfoLine(getString(R.string.detail_requires), String.join(", ", e.requires)));
        }

        detailState.setValue(new DetailState(e.id, e.name.substring(0, 1).toUpperCase(Locale.ROOT),
                new kotlin.Pair<>(tint(e.id, 0.55f), tint(e.id + "b", 0.35f)), iconOf(e, 256),
                e.name, e.author, "v" + cv.version + " \u00b7 " + kindLabel(e.kind), badges, installUi(e, cv), stats,
                e.imageBase, e.screenshots, e.description, reasons, hash, categories, info,
                cv.changelog.isEmpty() ? null : cv.changelog));
    }

    /** What the install area shows: a progress bar while an install runs, otherwise the buttons. */
    private InstallUi installUi(Entry e, Version cv) {
        if (busy.contains(e.id)) return new InstallUi.Working(installStatus != null ? installStatus : getString(R.string.detail_working), installPercent);
        String installed = ModInstaller.installedVersion(requireContext(), e);
        boolean same = installed != null && installed.equals(cv.version);
        boolean newer = installed != null && ModVersions.compare(cv.version, installed) > 0;
        String label = same ? getString(R.string.detail_remove)
                : installed == null ? getString(R.string.detail_install)
                : getString(newer ? R.string.detail_update : R.string.detail_switch, cv.version);
        boolean split = !same && e.versions.size() > 1;
        List<String> options = new ArrayList<>();
        int current = 0;
        String game = installedGameVersion();
        if (split) {
            for (int i = 0; i < e.versions.size(); i++) {
                Version v = e.versions.get(i);
                StringBuilder text = new StringBuilder(getString(R.string.detail_version_item, v.version, String.join(", ", v.gameVersions)));
                if (i == 0) text.append(" \u00b7 ").append(getString(R.string.detail_version_latest));
                if (v.supportsGame(game)) text.append(" \u00b7 ").append(getString(R.string.detail_version_fits));
                options.add(text.toString());
                if (v == cv) current = i;
            }
        }
        return new InstallUi.Idle(label, same, split, installed != null && !same, options, current);
    }

    // ---- DetailActions -------------------------------------------------------------------------------

    @Override
    public void primary() {
        Entry e = selected();
        if (e == null) return;
        Version cv = chosenVersion(e);
        String installed = ModInstaller.installedVersion(requireContext(), e);
        if (installed != null && installed.equals(cv.version)) {
            ModInstaller.remove(requireContext(), e);
            updateDetail();
            updateList();
        } else {
            startInstall(e, cv);
        }
    }

    @Override
    public void remove() {
        Entry e = selected();
        if (e == null) return;
        ModInstaller.remove(requireContext(), e);
        updateDetail();
        updateList();
    }

    @Override
    public void pickVersion(int index) {
        Entry e = selected();
        if (e == null || index < 0 || index >= e.versions.size()) return;
        chosen.put(e.id, e.versions.get(index).version);
        updateDetail();
    }

    @Override
    public void openViewer(int index) {
        Entry e = selected();
        if (e != null) viewerState.setValue(new ViewerRequest(e.imageBase, e.screenshots, index));
    }

    /** Works out what the install involves; asks first when there is anything to warn about or extra to download. */
    private void startInstall(Entry e, Version version) {
        if (busy.contains(e.id)) return; // already running (for instance, started before the user left this tab)
        Map<String, Entry> index = new HashMap<>();
        for (Entry x : all) index.put(x.id, x);
        InstallPlanner.Plan plan = InstallPlanner.plan(e, version, index,
                ModInstaller.installedVersions(requireContext(), all), installedGameVersion());
        if (plan.needsConfirmation()) showPlanDialog(e, plan, () -> runInstall(e, plan));
        else runInstall(e, plan);
    }

    private void runInstall(Entry e, InstallPlanner.Plan plan) {
        final List<String> ids = new ArrayList<>();
        for (InstallPlanner.Step step : plan.steps) ids.add(step.entry.id);
        synchronized (busy) {
            for (String id : ids) if (busy.contains(id)) return;
            busy.addAll(ids);
        }
        installPercent = 0;
        installStatus = getString(R.string.detail_working);
        updateDetail();
        updateList();
        // The worker thread must not touch this fragment: the user can leave the tab while it runs. It only uses
        // the application context, and reports back through onUi, which skips a fragment that is gone.
        final android.content.Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            String error = null;
            try {
                ModInstaller.install(app, plan, (mod, done, total) -> {
                    String line = mod.id.equals(e.id)
                            ? app.getString(R.string.detail_downloading, total > 0 ? (int) (done * 100 / total) : 0)
                            : app.getString(R.string.detail_downloading_requirement, mod.name);
                    final int percent = total > 0 ? (int) (done * 100 / total) : 0;
                    onUi(f -> {
                        f.installPercent = percent;
                        f.installStatus = line;
                        if (e.id.equals(f.selectedId)) f.updateDetail();
                    });
                });
            } catch (Exception ex) { // not only IOException: anything thrown here would otherwise crash the app
                android.util.Log.e("BrowseFragment", "Install of " + e.id + " failed", ex);
                error = ex.getMessage() != null ? ex.getMessage() : ex.toString();
            }
            final String err = error;
            busy.removeAll(ids);
            if (err != null) {
                MAIN.post(() -> Toast.makeText(app, app.getString(R.string.detail_install_failed, e.name, err), Toast.LENGTH_LONG).show());
            }
            onUi(f -> {
                f.updateList();
                if (e.id.equals(f.selectedId)) f.updateDetail();
            });
        }, "mod-install").start();
    }

    // ---- versions, warnings and dependencies -------------------------------------------------------

    private String boundsText(Dependency d) {
        return dev.segually.sbamodloader.browser.PlanText.bounds(requireContext(), d);
    }

    private String modName(String id) {
        for (Entry x : all) if (x.id.equals(id)) return x.name;
        // A dependency of a mod from another repository may be in the official one.
        int colon = id.indexOf(':');
        if (colon > 0) for (Entry x : all) if (x.id.equals(id.substring(colon + 1))) return x.name;
        return id;
    }

    private String dependencyLabel(Dependency d) {
        return getString(R.string.detail_requires_item, modName(d.id), boundsText(d));
    }

    private String issueText(InstallPlanner.Issue i) {
        return dev.segually.sbamodloader.browser.PlanText.issue(requireContext(), i, installedGameVersion());
    }

    /** What will be downloaded and what to watch out for, before anything is. A blocked plan can only be closed. */
    private void showPlanDialog(Entry e, InstallPlanner.Plan plan, Runnable confirmed) {
        StringBuilder body = new StringBuilder();
        if (plan.steps.size() > 1) {
            body.append(getString(R.string.plan_downloads, plan.steps.size(),
                    Formatter.formatShortFileSize(requireContext(), plan.totalBytes())));
            for (InstallPlanner.Step step : plan.steps) {
                body.append("\n\u2022 ").append(step.replaces == null
                        ? getString(R.string.plan_download_new, step.entry.name, step.version.version)
                        : getString(R.string.plan_download_replace, step.entry.name, step.version.version, step.replaces));
            }
        }
        if (!plan.issues.isEmpty()) {
            if (body.length() > 0) body.append("\n\n");
            for (int i = 0; i < plan.issues.size(); i++) {
                if (i > 0) body.append("\n\n");
                body.append("\u26A0 ").append(issueText(plan.issues.get(i)));
            }
        }
        dev.segually.sbamodloader.ui.AppDialogs.plan(requireContext(),
                getString(plan.blocked() ? R.string.plan_title_blocked
                        : plan.issues.isEmpty() ? R.string.plan_title_confirm : R.string.plan_title_warn, e.name),
                body.toString(), plan.blocked(),
                getString(plan.blocked() ? R.string.plan_ok : plan.issues.isEmpty() ? R.string.plan_install : R.string.plan_install_anyway),
                confirmed);
    }

    // ---- the safety card ----------------------------------------------------------------------------

    private Reason reasonOf(dev.segually.sbamodloader.browser.SafetyReasons.Reason r) {
        switch (r.type) {
            case AUDITED:
                return new Reason(R.drawable.ic_verified, getString(R.string.detail_audited_title), getString(R.string.detail_audited_body, r.a, r.b));
            case UNVERIFIED:
                return new Reason(R.drawable.ic_warning, getString(R.string.safety_unverified, r.a), getString(R.string.safety_unverified_body));
            case NOTE:
                return new Reason(R.drawable.ic_check_circle, r.a, null);
            case NO_CODE:
                return new Reason(R.drawable.ic_check_circle, getString(R.string.safety_no_code), getString(R.string.safety_no_code_body));
            case NATIVE_CODE:
                return new Reason(R.drawable.ic_code, getString(R.string.safety_native), getString(R.string.safety_native_body));
            case JAVA_CODE:
                return new Reason(R.drawable.ic_code, getString(R.string.safety_java), getString(R.string.safety_java_body));
            case PLUGIN_CODE:
                return new Reason(R.drawable.ic_code, getString(R.string.safety_plugin), getString(R.string.safety_plugin_body));
            case OPEN_SOURCE:
                return new Reason(R.drawable.ic_code, getString(R.string.detail_open_source_title, r.a), getString(R.string.detail_open_source_body, r.b, r.c));
            case CLOSED_SOURCE:
                return new Reason(R.drawable.ic_lock, getString(R.string.detail_closed_source_title, r.a), getString(R.string.detail_closed_source_body, r.b));
            case OWNER:
                return new Reason(R.drawable.ic_verified, getString(R.string.safety_owner), getString(R.string.safety_owner_body, r.a));
            default: {
                int n = Integer.parseInt(r.a);
                return new Reason(R.drawable.ic_check_circle,
                        n == 1 ? getString(R.string.safety_history_one) : getString(R.string.safety_history, n),
                        getString(R.string.safety_history_body));
            }
        }
    }

    // ---- labels and small helpers ----------------------------------------------------------------

    private String kindLabel(Kind k) {
        return getString(k == Kind.FUSION ? R.string.kind_fusion : k == Kind.NATIVE ? R.string.kind_native : R.string.kind_pack);
    }

    private String categoryLabel(Category c) {
        return getString(c == Category.TEXTURE_PACK ? R.string.category_texture_pack : R.string.category_mod);
    }

    private String subLabel(Sub s) {
        switch (s) {
            case FUN: return getString(R.string.sub_fun);
            case EXTRA_CONTENT: return getString(R.string.sub_extra_content);
            case REPLACEMENT: return getString(R.string.sub_replacement);
            case QUALITY_OF_LIFE: return getString(R.string.sub_quality_of_life);
            case EXPANSION: return getString(R.string.sub_expansion);
            case CHANGES: return getString(R.string.sub_changes);
            case CHEATS: return getString(R.string.sub_cheats);
            default: return getString(R.string.sub_port);
        }
    }

    private String sortLabel(BrowseQuery.Sort s) {
        switch (s) {
            case TOP_RATED: return getString(R.string.sort_top_rated);
            case RECENTLY_UPDATED: return getString(R.string.sort_recent);
            case NAME: return getString(R.string.sort_name);
            case SIZE: return getString(R.string.sort_size);
            default: return getString(R.string.sort_popular);
        }
    }

    private static String compact(long n) {
        if (n >= 1_000_000) return String.format(Locale.US, "%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format(Locale.US, "%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }

    private static int tint(String seed, float value) {
        float hue = Math.floorMod(seed.hashCode() * 31, 360);
        return Color.HSVToColor(new float[]{hue, 0.5f, value + 0.2f});
    }

}
