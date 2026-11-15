package dev.segually.sbamodloader.browser;

import android.content.Context;

import dev.segually.sbamodloader.R;
import dev.segually.sbamodloader.browser.BrowserModels.Dependency;

/** The sentences for install plans and dependency problems, shared by the store pages and the problems list. */
public final class PlanText {
    private PlanText() {
    }

    /** "1.2.7 or newer", "1.0 to 1.4" and so on. */
    public static String bounds(Context context, Dependency d) {
        if (d.min != null && d.max != null) return context.getString(R.string.bound_range, d.min, d.max);
        if (d.min != null) return context.getString(R.string.bound_min, d.min);
        if (d.max != null) return context.getString(R.string.bound_max, d.max);
        return context.getString(R.string.bound_any);
    }

    public static String issue(Context context, InstallPlanner.Issue i, String gameVersion) {
        String needs = i.requirement == null ? "" : bounds(context, i.requirement);
        switch (i.kind) {
            case GAME_MISMATCH:
                return context.getString(R.string.plan_game_mismatch, i.mod, i.version, i.other,
                        gameVersion != null ? gameVersion : context.getString(R.string.plan_game_unknown));
            case MOD_OUTDATED:
                return context.getString(R.string.plan_outdated, i.mod, i.version, i.other);
            case DEP_OUTDATED:
                return context.getString(R.string.plan_dep_outdated, i.dependant, i.mod, needs, i.version);
            case DEP_NEEDS_OLDER:
                return context.getString(R.string.plan_dep_needs_older, i.dependant, i.mod, needs, i.version);
            case DEP_GAME_MISMATCH:
                return context.getString(R.string.plan_dep_game_mismatch, i.dependant, i.mod, needs, i.version, i.other);
            case DEP_MISSING:
                return context.getString(R.string.plan_dep_missing, i.dependant, i.mod);
            case DEP_NO_VERSION:
                return context.getString(R.string.plan_dep_no_version, i.dependant, i.mod, needs);
            default:
                return context.getString(R.string.plan_dep_conflict, i.dependant, i.mod, needs, i.version);
        }
    }
}
