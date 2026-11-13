package dev.segually.sbamodloader.browser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import dev.segually.sbamodloader.browser.BrowserModels.Category;
import dev.segually.sbamodloader.browser.BrowserModels.Entry;
import dev.segually.sbamodloader.browser.BrowserModels.Kind;
import dev.segually.sbamodloader.browser.BrowserModels.Sub;

/** What the user has asked the browser to show: a search, a type, a category with subcategories, and an order. */
public final class BrowseQuery {
    public enum Sort { POPULAR, TOP_RATED, RECENTLY_UPDATED, NAME, SIZE }

    public String text = "";
    /** Null shows every type. */
    public Kind kind;
    /** Null shows every category. */
    public Category category;
    /** Empty means any subcategory of {@link #category}; otherwise a mod must be in at least one of these. */
    public final Set<Sub> subs = EnumSet.noneOf(Sub.class);
    public Sort sort = Sort.POPULAR;

    public List<Entry> apply(List<Entry> all) {
        String needle = text.trim().toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>();
        for (Entry e : all) {
            if (kind != null && e.kind != kind) continue;
            if (category != null && !e.inCategory(category)) continue;
            if (!subs.isEmpty()) {
                boolean any = false;
                for (Sub s : e.categories) if (subs.contains(s)) any = true;
                if (!any) continue;
            }
            if (!needle.isEmpty() && !e.searchText().contains(needle)) continue;
            out.add(e);
        }
        out.sort(comparator());
        return out;
    }

    private Comparator<Entry> comparator() {
        switch (sort) {
            case TOP_RATED:
                return Comparator.<Entry>comparingDouble(e -> e.rating).reversed().thenComparing(e -> e.name);
            case RECENTLY_UPDATED:
                return Comparator.<Entry>comparingLong(e -> e.updatedMillis).reversed().thenComparing(e -> e.name);
            case NAME:
                return Comparator.comparing(e -> e.name.toLowerCase(Locale.ROOT));
            case SIZE:
                return Comparator.<Entry>comparingLong(e -> e.size).thenComparing(e -> e.name);
            default:
                return Comparator.<Entry>comparingLong(e -> e.downloads).reversed().thenComparing(e -> e.name);
        }
    }
}
