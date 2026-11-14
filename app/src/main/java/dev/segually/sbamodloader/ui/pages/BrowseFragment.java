package dev.segually.sbamodloader.ui.pages;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputEditText;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import dev.segually.sbamodloader.R;
import dev.segually.sbamodloader.browser.BrowseQuery;
import dev.segually.sbamodloader.browser.BrowserModels.Category;
import dev.segually.sbamodloader.browser.BrowserModels.Entry;
import dev.segually.sbamodloader.browser.BrowserModels.Kind;
import dev.segually.sbamodloader.browser.BrowserModels.Sub;
import dev.segually.sbamodloader.browser.ModInstaller;
import dev.segually.sbamodloader.browser.RemoteModRepository;
import dev.segually.sbamodloader.ui.Insets;

/**
 * The mod browser. On a wide (landscape) layout the search, filters and list sit on the left and the selected
 * mod on the right; in portrait the list and the detail are separate pages and back returns to the list.
 */
public class BrowseFragment extends Fragment {
    /** Argument: the id of a mod whose store page to open as soon as the list has loaded. */
    public static final String ARG_MOD_ID = "mod_id";
    private final BrowseQuery query = new BrowseQuery();
    private RemoteModRepository repository;
    private boolean offlineCopy;
    private final Set<String> busy = new HashSet<>(); // mods being installed right now
    private List<Entry> all = new ArrayList<>();
    private final ModAdapter adapter = new ModAdapter();
    private String selectedId;

    private View detailPage; // null when the list and the detail share the screen
    private ChipGroup chipsSub;
    private View subsScroll;
    private TextView resultCount, emptyView;
    private View retryButton;
    private MaterialButton sortButton;
    private ScrollViewHolder detail;

    private final OnBackPressedCallback backCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            closeDetail();
        }
    };

    private static final class ScrollViewHolder {
        View scroll;
        LinearLayout content;
        View empty;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View v = inflater.inflate(R.layout.fragment_browse, container, false);
        repository = new RemoteModRepository(requireContext());
        detailPage = v.findViewById(R.id.detail_page);

        detail = new ScrollViewHolder();
        detail.scroll = v.findViewById(R.id.detail_scroll);
        detail.content = v.findViewById(R.id.detail_content);
        detail.empty = v.findViewById(R.id.detail_empty);

        Insets.padForChrome(v.findViewById(R.id.browse_list_page), 0);
        if (detailPage != null) Insets.padForChrome(detailPage, 0);
        else Insets.padForChrome(detail.scroll, 0);

        resultCount = v.findViewById(R.id.result_count);
        emptyView = v.findViewById(R.id.browse_empty);
        sortButton = v.findViewById(R.id.btn_sort);
        chipsSub = v.findViewById(R.id.chips_sub);
        subsScroll = v.findViewById(R.id.subs_scroll);

        RecyclerView list = v.findViewById(R.id.browse_list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        TextInputEditText search = v.findViewById(R.id.search_input);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void afterTextChanged(Editable s) {
                query.text = s.toString();
                refresh();
            }
        });

        ChipGroup kinds = v.findViewById(R.id.chips_kind);
        kinds.setOnCheckedStateChangeListener((group, ids) -> {
            int id = ids.isEmpty() ? R.id.chip_kind_all : ids.get(0);
            query.kind = id == R.id.chip_kind_fusion ? Kind.FUSION
                    : id == R.id.chip_kind_native ? Kind.NATIVE
                    : id == R.id.chip_kind_pack ? Kind.PACK : null;
            refresh();
        });
        ChipGroup categories = v.findViewById(R.id.chips_category);
        categories.setOnCheckedStateChangeListener((group, ids) -> {
            int id = ids.isEmpty() ? R.id.chip_cat_all : ids.get(0);
            query.category = id == R.id.chip_cat_texture ? Category.TEXTURE_PACK
                    : id == R.id.chip_cat_mod ? Category.MOD : null;
            query.subs.clear();
            rebuildSubChips();
            refresh();
        });

        sortButton.setOnClickListener(this::showSortMenu);
        updateSortLabel();

        if (detailPage != null) {
            ((MaterialToolbar) v.findViewById(R.id.detail_toolbar)).setNavigationOnClickListener(x -> closeDetail());
            requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backCallback);
        }

        retryButton = v.findViewById(R.id.browse_retry);
        retryButton.setOnClickListener(x -> loadRepository());
        loadRepository();
        return v;
    }

    private void loadRepository() {
        resultCount.setText(R.string.browse_loading);
        retryButton.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        new Thread(() -> {
            List<Entry> loaded;
            try {
                loaded = repository.load();
            } catch (IOException e) {
                android.util.Log.e("BrowseFragment", "Could not load the repository", e);
                loaded = null;
            }
            List<Entry> result = loaded;
            boolean cached = repository.loadedFromCache();
            if (isAdded()) requireActivity().runOnUiThread(() -> {
                if (result == null) {
                    resultCount.setText(R.string.browse_load_failed);
                    emptyView.setText(R.string.browse_load_failed_detail);
                    emptyView.setVisibility(View.VISIBLE);
                    retryButton.setVisibility(View.VISIBLE);
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

    // ---- filtering ------------------------------------------------------------------------------

    private void rebuildSubChips() {
        chipsSub.removeAllViews();
        if (query.category == null) {
            subsScroll.setVisibility(View.GONE);
            return;
        }
        subsScroll.setVisibility(View.VISIBLE);
        for (Sub sub : Sub.of(query.category)) {
            Chip chip = new Chip(requireContext());
            chip.setText(subLabel(sub));
            chip.setCheckable(true);
            chip.setChipIconVisible(false);
            chip.setOnCheckedChangeListener((b, checked) -> {
                if (checked) query.subs.add(sub);
                else query.subs.remove(sub);
                refresh();
            });
            chipsSub.addView(chip);
        }
    }

    private void showSortMenu(View anchor) {
        androidx.appcompat.widget.PopupMenu menu = new androidx.appcompat.widget.PopupMenu(requireContext(), anchor);
        BrowseQuery.Sort[] sorts = BrowseQuery.Sort.values();
        for (BrowseQuery.Sort s : sorts) {
            menu.getMenu().add(Menu.NONE, s.ordinal(), s.ordinal(), sortLabel(s)).setCheckable(true).setChecked(s == query.sort);
        }
        menu.getMenu().setGroupCheckable(Menu.NONE, true, true);
        menu.setOnMenuItemClickListener(item -> {
            query.sort = sorts[item.getItemId()];
            updateSortLabel();
            refresh();
            return true;
        });
        menu.show();
    }

    private void updateSortLabel() {
        sortButton.setText(getString(R.string.browse_sort_format, sortLabel(query.sort)));
    }

    private void refresh() {
        if (!isAdded()) return;
        List<Entry> shown = query.apply(all);
        adapter.submit(shown);
        String count = shown.size() == 1 ? getString(R.string.browse_result_one)
                : getString(R.string.browse_results, shown.size());
        resultCount.setText(offlineCopy ? count + " \u00b7 " + getString(R.string.browse_offline_copy) : count);
        emptyView.setText(all.isEmpty() ? R.string.browse_repository_empty : R.string.browse_no_results);
        emptyView.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ---- detail ---------------------------------------------------------------------------------

    private void select(Entry e) {
        selectedId = e.id;
        adapter.notifyDataSetChanged();
        showDetail(e);
        if (detailPage != null) {
            requireView().findViewById(R.id.browse_list_page).setVisibility(View.GONE);
            detailPage.setVisibility(View.VISIBLE);
            backCallback.setEnabled(true);
        }
    }

    private void closeDetail() {
        if (detailPage == null) return;
        detailPage.setVisibility(View.GONE);
        requireView().findViewById(R.id.browse_list_page).setVisibility(View.VISIBLE);
        backCallback.setEnabled(false);
    }

    private String installedGameVersion() {
        try {
            return requireContext().getPackageManager().getPackageInfo(dev.segually.sbamodloader.TargetGame.PACKAGE_NAME, 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private int color(int attr) {
        return MaterialColors.getColor(requireView(), attr);
    }

    private TextView text(String s, int style, int colorAttr) {
        TextView t = new TextView(requireContext());
        t.setText(s);
        t.setTextAppearance(style);
        t.setTextColor(color(colorAttr));
        return t;
    }

    private TextView section(String title) {
        TextView t = text(title, com.google.android.material.R.style.TextAppearance_Material3_TitleMedium,
                com.google.android.material.R.attr.colorOnSurface);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(28);
        lp.bottomMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    private void showDetail(Entry e) {
        LinearLayout c = detail.content;
        c.removeAllViews();
        detail.empty.setVisibility(View.GONE);
        detail.scroll.setVisibility(View.VISIBLE);
        detail.scroll.scrollTo(0, 0);

        // Header
        LinearLayout header = new LinearLayout(requireContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView avatar = new TextView(requireContext());
        styleAvatar(avatar, e, 20);
        avatar.setTextSize(32);
        header.addView(avatar, new LinearLayout.LayoutParams(dp(80), dp(80)));
        LinearLayout titles = new LinearLayout(requireContext());
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart(dp(16));
        titles.addView(text(e.name, com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall,
                com.google.android.material.R.attr.colorOnSurface));
        titles.addView(text(e.author, com.google.android.material.R.style.TextAppearance_Material3_BodyMedium,
                com.google.android.material.R.attr.colorOnSurfaceVariant));
        titles.addView(text("v" + e.version + " · " + kindLabel(e.kind),
                com.google.android.material.R.style.TextAppearance_Material3_BodySmall,
                com.google.android.material.R.attr.colorOnSurfaceVariant));
        header.addView(titles, tlp);
        c.addView(header);

        // Badges: audited, source
        ChipGroup badges = new ChipGroup(requireContext());
        badges.setPadding(0, dp(12), 0, 0);
        badges.addView(badge(getString(R.string.browse_audited), R.drawable.ic_verified, true));
        badges.addView(badge(getString(e.openSource ? R.string.detail_open_source_short : R.string.detail_closed_source_short),
                e.openSource ? R.drawable.ic_code : R.drawable.ic_lock, false));
        String installedGame = installedGameVersion();
        if (installedGame != null && !e.gameVersions.contains(installedGame)) {
            badges.addView(badge(getString(R.string.detail_for_version, String.join(", ", e.gameVersions)), R.drawable.ic_warning, false));
        }
        c.addView(badges);

        // Install, update, remove
        LinearLayout installArea = new LinearLayout(requireContext());
        installArea.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.topMargin = dp(16);
        c.addView(installArea, ilp);
        renderInstallArea(installArea, e, null);

        // Stats: only what the repository actually has (it carries no download counts or ratings yet)
        LinearLayout stats = new LinearLayout(requireContext());
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setPadding(0, dp(20), 0, 0);
        if (e.ratingCount > 0) {
            stats.addView(stat(String.format(Locale.US, "%.1f \u2605", e.rating), getString(R.string.detail_rating_count, e.ratingCount)));
        }
        if (e.downloads > 0) stats.addView(stat(compact(e.downloads), getString(R.string.detail_downloads)));
        stats.addView(stat(Formatter.formatShortFileSize(requireContext(), e.size), getString(R.string.detail_size)));
        stats.addView(stat(DateUtils.getRelativeTimeSpanString(e.updatedMillis, System.currentTimeMillis(),
                DateUtils.DAY_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString(), getString(R.string.detail_updated).toLowerCase(Locale.ROOT)));
        c.addView(stats);

        // About
        c.addView(section(getString(R.string.detail_about)));
        c.addView(text(e.description, com.google.android.material.R.style.TextAppearance_Material3_BodyMedium,
                com.google.android.material.R.attr.colorOnSurface));

        // Verification
        c.addView(section(getString(R.string.detail_verification)));
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(16), dp(8), dp(16), dp(16));
        card.addView(verifyRow(R.drawable.ic_verified, getString(R.string.detail_audited_title),
                getString(R.string.detail_audited_body, e.audit.version, e.audit.date)));
        if (e.openSource) {
            card.addView(verifyRow(R.drawable.ic_code, getString(R.string.detail_open_source_title, e.license),
                    getString(R.string.detail_open_source_body, e.source.repo, e.source.tag)));
        } else {
            card.addView(verifyRow(R.drawable.ic_lock, getString(R.string.detail_closed_source_title, e.license),
                    getString(R.string.detail_closed_source_body, e.source.host())));
        }
        // The checksum means nothing to most people; it is there for developers.
        if (dev.segually.sbamodloader.SBASettings.getDeveloperFeatures(requireContext())) {
            TextView hashTitle = text(getString(R.string.detail_hash_title),
                    com.google.android.material.R.style.TextAppearance_Material3_LabelMedium,
                    com.google.android.material.R.attr.colorOnSurfaceVariant);
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hlp.topMargin = dp(12);
            card.addView(hashTitle, hlp);
            TextView hash = text(e.sha256, com.google.android.material.R.style.TextAppearance_Material3_BodySmall,
                    com.google.android.material.R.attr.colorOnSurface);
            hash.setTypeface(android.graphics.Typeface.MONOSPACE);
            hash.setTextIsSelectable(true);
            card.addView(hash);
        }
        c.addView(card);

        // Categories
        c.addView(section(getString(R.string.detail_categories)));
        ChipGroup cats = new ChipGroup(requireContext());
        for (Sub s : e.categories) {
            cats.addView(badge(categoryLabel(s.parent) + " › " + subLabel(s), 0, false));
        }
        c.addView(cats);

        // Details
        c.addView(section(getString(R.string.detail_info)));
        c.addView(info(getString(R.string.detail_type), kindLabel(e.kind)));
        c.addView(info(getString(R.string.detail_version), e.version));
        c.addView(info(getString(R.string.detail_game_versions), String.join(", ", e.gameVersions)));
        c.addView(info(getString(R.string.detail_license), e.license));
        if (!e.requires.isEmpty()) c.addView(info(getString(R.string.detail_requires), String.join(", ", e.requires)));

        if (!e.changelog.isEmpty()) {
            c.addView(section(getString(R.string.detail_whats_new)));
            c.addView(text(e.changelog, com.google.android.material.R.style.TextAppearance_Material3_BodyMedium,
                    com.google.android.material.R.attr.colorOnSurface));
        }
    }

    /** Install, Update and Remove for {@code e}. {@code status} is a progress line while an install is running. */
    private void renderInstallArea(LinearLayout area, Entry e, @Nullable String status) {
        area.removeAllViews();
        ModInstaller.State state = ModInstaller.state(requireContext(), e);
        boolean working = busy.contains(e.id);

        MaterialButton primary = new MaterialButton(requireContext());
        primary.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        if (working) {
            primary.setText(status != null ? status : getString(R.string.detail_working));
            primary.setEnabled(false);
        } else if (state == ModInstaller.State.INSTALLED) {
            primary.setText(R.string.detail_remove);
            primary.setIconResource(R.drawable.ic_delete);
            primary.setOnClickListener(x -> {
                ModInstaller.remove(requireContext(), e);
                renderInstallArea(area, e, null);
                adapter.notifyDataSetChanged();
            });
        } else {
            primary.setText(state == ModInstaller.State.UPDATE_AVAILABLE ? getString(R.string.detail_update, e.version) : getString(R.string.detail_install));
            primary.setIconResource(R.drawable.ic_download);
            primary.setOnClickListener(x -> startInstall(area, e));
        }
        area.addView(primary);
        if (!working && state == ModInstaller.State.UPDATE_AVAILABLE) {
            MaterialButton remove = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            remove.setText(R.string.detail_remove);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
            rlp.topMargin = dp(8);
            remove.setLayoutParams(rlp);
            remove.setOnClickListener(x -> {
                ModInstaller.remove(requireContext(), e);
                renderInstallArea(area, e, null);
                adapter.notifyDataSetChanged();
            });
            area.addView(remove);
        }
    }

    private void startInstall(LinearLayout area, Entry e) {
        busy.add(e.id);
        renderInstallArea(area, e, getString(R.string.detail_working));
        adapter.notifyDataSetChanged();
        android.content.Context app = requireContext().getApplicationContext();
        List<Entry> snapshot = all;
        new Thread(() -> {
            String error = null;
            try {
                ModInstaller.install(app, e, snapshot, (mod, done, total) -> {
                    String line = mod.id.equals(e.id)
                            ? getString(R.string.detail_downloading, total > 0 ? (int) (done * 100 / total) : 0)
                            : getString(R.string.detail_downloading_requirement, mod.name);
                    if (isAdded()) requireActivity().runOnUiThread(() -> {
                        if (selectedId != null && selectedId.equals(e.id)) renderInstallArea(area, e, line);
                    });
                });
            } catch (IOException ex) {
                android.util.Log.e("BrowseFragment", "Install of " + e.id + " failed", ex);
                error = ex.getMessage();
            }
            String err = error;
            if (isAdded()) requireActivity().runOnUiThread(() -> {
                busy.remove(e.id);
                adapter.notifyDataSetChanged();
                if (selectedId != null && selectedId.equals(e.id)) renderInstallArea(area, e, null);
                if (err != null) Toast.makeText(requireContext(), getString(R.string.detail_install_failed, e.name, err), Toast.LENGTH_LONG).show();
            });
        }, "mod-install").start();
    }

    private Chip badge(String label, int icon, boolean accent) {
        Chip chip = new Chip(requireContext());
        chip.setText(label);
        chip.setClickable(false);
        chip.setCheckable(false);
        if (icon != 0) {
            chip.setChipIconResource(icon);
            chip.setChipIconVisible(true);
            if (accent) chip.setChipIconTint(android.content.res.ColorStateList.valueOf(color(androidx.appcompat.R.attr.colorPrimary)));
        }
        return chip;
    }

    private View stat(String value, String label) {
        LinearLayout col = new LinearLayout(requireContext());
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.addView(text(value, com.google.android.material.R.style.TextAppearance_Material3_TitleMedium,
                com.google.android.material.R.attr.colorOnSurface));
        col.addView(text(label, com.google.android.material.R.style.TextAppearance_Material3_BodySmall,
                com.google.android.material.R.attr.colorOnSurfaceVariant));
        col.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return col;
    }

    private View verifyRow(int icon, String title, String body) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(12), 0, 0);
        android.widget.ImageView iv = new android.widget.ImageView(requireContext());
        iv.setImageResource(icon);
        iv.setColorFilter(color(androidx.appcompat.R.attr.colorPrimary));
        row.addView(iv, new LinearLayout.LayoutParams(dp(24), dp(24)));
        LinearLayout col = new LinearLayout(requireContext());
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clp.setMarginStart(dp(16));
        col.addView(text(title, com.google.android.material.R.style.TextAppearance_Material3_BodyLarge,
                com.google.android.material.R.attr.colorOnSurface));
        col.addView(text(body, com.google.android.material.R.style.TextAppearance_Material3_BodySmall,
                com.google.android.material.R.attr.colorOnSurfaceVariant));
        row.addView(col, clp);
        return row;
    }

    private View info(String label, String value) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, dp(6));
        TextView l = text(label, com.google.android.material.R.style.TextAppearance_Material3_BodyMedium,
                com.google.android.material.R.attr.colorOnSurfaceVariant);
        row.addView(l, new LinearLayout.LayoutParams(dp(140), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView v = text(value, com.google.android.material.R.style.TextAppearance_Material3_BodyMedium,
                com.google.android.material.R.attr.colorOnSurface);
        row.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
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

    private void styleAvatar(TextView t, Entry e, float radiusDp) {
        t.setText(e.name.substring(0, 1).toUpperCase(Locale.ROOT));
        t.setGravity(Gravity.CENTER);
        t.setTextColor(Color.WHITE);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{tint(e.id, 0.55f), tint(e.id + "b", 0.35f)});
        g.setCornerRadius(dp(radiusDp));
        t.setBackground(g);
    }

    // ---- list ------------------------------------------------------------------------------------

    private final class ModAdapter extends RecyclerView.Adapter<ModAdapter.Holder> {
        private List<Entry> items = new ArrayList<>();

        void submit(List<Entry> list) {
            items = list;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_browse_mod, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Entry e = items.get(position);
            styleAvatar(h.avatar, e, 16);
            h.name.setText(e.name);
            h.meta.setText(e.author + " · " + kindLabel(e.kind));
            h.summary.setText(e.summary);
            StringBuilder stats = new StringBuilder();
            if (e.ratingCount > 0) stats.append(String.format(Locale.US, "\u2605 %.1f  \u00b7  ", e.rating));
            if (e.downloads > 0) stats.append(compact(e.downloads)).append("  \u00b7  ");
            stats.append(Formatter.formatShortFileSize(requireContext(), e.size));
            ModInstaller.State state = ModInstaller.state(requireContext(), e);
            if (busy.contains(e.id)) stats.append("  \u00b7  ").append(getString(R.string.detail_working));
            else if (state == ModInstaller.State.INSTALLED) stats.append("  \u00b7  ").append(getString(R.string.detail_installed));
            else if (state == ModInstaller.State.UPDATE_AVAILABLE) stats.append("  \u00b7  ").append(getString(R.string.detail_update_available));
            h.stats.setText(stats);
            h.root.setBackgroundResource(e.id.equals(selectedId) && detailPage == null
                    ? R.drawable.bg_card_selected : R.drawable.bg_card);
            h.root.setOnClickListener(v -> select(e));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final View root;
            final TextView avatar, name, meta, summary, stats;

            Holder(View v) {
                super(v);
                root = v.findViewById(R.id.item_root);
                avatar = v.findViewById(R.id.item_avatar);
                name = v.findViewById(R.id.item_name);
                meta = v.findViewById(R.id.item_meta);
                summary = v.findViewById(R.id.item_summary);
                stats = v.findViewById(R.id.item_stats);
            }
        }
    }
}
