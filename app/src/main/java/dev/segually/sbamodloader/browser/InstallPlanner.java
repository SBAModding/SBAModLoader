package dev.segually.sbamodloader.browser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.segually.sbamodloader.browser.BrowserModels.Dependency;
import dev.segually.sbamodloader.browser.BrowserModels.Entry;
import dev.segually.sbamodloader.browser.BrowserModels.Version;

/**
 * Works out what installing one release involves: which other mods have to come with it, which versions of them, and
 * what the player should be told first. Plain Java, no Android classes, so it is tested on a desktop JVM.
 *
 * <p>Dependencies are chosen so that each satisfies the version bounds of whoever needs it. A dependency that is already
 * installed and fits is left alone. One that is installed but does not fit is replaced (and the player is told, as
 * they are about to get an older or newer copy than they have now).
 */
public final class InstallPlanner {
    private InstallPlanner() {
    }

    public enum Kind {
        /** The chosen release was made for other game versions than the installed one. */
        GAME_MISMATCH(false),
        /** The chosen release is not the newest one. */
        MOD_OUTDATED(false),
        /** An installed dependency is older than this release accepts; the plan updates it. */
        DEP_OUTDATED(false),
        /** An installed dependency is newer than this release accepts; the plan puts an older copy in its place. */
        DEP_NEEDS_OLDER(false),
        /** The only dependency releases that fit were made for other game versions. */
        DEP_GAME_MISMATCH(false),
        /** A dependency is not in the repository at all. */
        DEP_MISSING(true),
        /** A dependency exists, but none of its releases fit the bounds. */
        DEP_NO_VERSION(true),
        /** Two mods in the plan need versions of a third that cannot both be true. */
        DEP_CONFLICT(true);

        /** A blocking issue means the install cannot go ahead. */
        public final boolean blocking;

        Kind(boolean blocking) {
            this.blocking = blocking;
        }
    }

    /** One thing to tell the player. Which of the fields are set depends on {@link #kind}. */
    public static final class Issue {
        public final Kind kind;
        /** The mod the issue is about (the target for GAME_MISMATCH and MOD_OUTDATED, otherwise a dependency). */
        public final String mod;
        /** The mod that needs {@link #mod}, for dependency issues. */
        public final String dependant;
        /** The bounds that were asked for, for dependency issues. */
        public final Dependency requirement;
        /** Kind specific: a version (chosen, installed or planned). */
        public final String version;
        /** Kind specific: the latest version, or the game versions the release was made for (comma separated). */
        public final String other;

        Issue(Kind kind, String mod, String dependant, Dependency requirement, String version, String other) {
            this.kind = kind;
            this.mod = mod;
            this.dependant = dependant;
            this.requirement = requirement;
            this.version = version;
            this.other = other;
        }
    }

    /** One download, in the order they have to be installed (dependencies first, the chosen mod last). */
    public static final class Step {
        public final Entry entry;
        public final Version version;
        public final boolean dependency;
        /** The version that is installed now and gets replaced, or null for a fresh install. */
        public final String replaces;

        Step(Entry entry, Version version, boolean dependency, String replaces) {
            this.entry = entry;
            this.version = version;
            this.dependency = dependency;
            this.replaces = replaces;
        }
    }

    public static final class Plan {
        public final List<Step> steps = new ArrayList<>();
        public final List<Issue> issues = new ArrayList<>();

        public boolean blocked() {
            for (Issue i : issues) if (i.kind.blocking) return true;
            return false;
        }

        /** Whether the player should see a summary first: something to warn about, or more than the one download. */
        public boolean needsConfirmation() {
            return !issues.isEmpty() || steps.size() > 1;
        }

        public long totalBytes() {
            long total = 0;
            for (Step s : steps) total += s.version.size;
            return total;
        }
    }

    /**
     * @param target      the mod being installed
     * @param chosen      the release of it to install
     * @param index       every mod in the repository, by id
     * @param installed   the version of each mod the browser has installed, by id
     * @param gameVersion the installed game's version, or null when it is not known
     */
    public static Plan plan(Entry target, Version chosen, Map<String, Entry> index, Map<String, String> installed,
                            String gameVersion) {
        Plan plan = new Plan();
        Context c = new Context(plan, index, installed, gameVersion);

        if (gameVersion != null && !chosen.supportsGame(gameVersion)) {
            plan.issues.add(new Issue(Kind.GAME_MISMATCH, target.name, null, null, chosen.version,
                    String.join(", ", chosen.gameVersions)));
        }
        if (ModVersions.compare(chosen.version, target.latest().version) < 0) {
            plan.issues.add(new Issue(Kind.MOD_OUTDATED, target.name, null, null, chosen.version,
                    target.latest().version));
        }

        c.effective.put(target.id, chosen.version);
        c.planned.add(target.id);
        c.resolve(target, chosen);
        plan.steps.add(new Step(target, chosen, false, installed.get(target.id)));
        return plan;
    }

    private static final class Context {
        final Plan plan;
        final Map<String, Entry> index;
        final Map<String, String> installed;
        final String gameVersion;
        /** The version each mod will have once the plan is done: what is installed, or what the plan puts there. */
        final Map<String, String> effective;
        final Set<String> planned = new HashSet<>();

        Context(Plan plan, Map<String, Entry> index, Map<String, String> installed, String gameVersion) {
            this.plan = plan;
            this.index = index;
            this.installed = installed;
            this.gameVersion = gameVersion;
            this.effective = new HashMap<>(installed);
        }

        void resolve(Entry entry, Version version) {
            for (Dependency d : version.dependencies) {
                Entry dep = index.get(d.id);
                if (dep == null) {
                    plan.issues.add(new Issue(Kind.DEP_MISSING, d.id, entry.name, d, null, null));
                    continue;
                }
                String have = effective.get(d.id);
                if (have != null && ModVersions.satisfies(d, have)) continue;
                if (have != null && planned.contains(d.id)) {
                    // Something earlier in this plan already fixed the version of this mod.
                    plan.issues.add(new Issue(Kind.DEP_CONFLICT, dep.name, entry.name, d, have, null));
                    continue;
                }
                String replaces = installed.get(d.id);
                if (replaces != null) {
                    boolean tooNew = d.max != null && ModVersions.compare(replaces, d.max) > 0;
                    plan.issues.add(new Issue(tooNew ? Kind.DEP_NEEDS_OLDER : Kind.DEP_OUTDATED, dep.name, entry.name,
                            d, replaces, null));
                }

                Version pick = pick(dep, d);
                if (pick == null) {
                    plan.issues.add(new Issue(Kind.DEP_NO_VERSION, dep.name, entry.name, d, null, null));
                    continue;
                }
                if (gameVersion != null && !pick.supportsGame(gameVersion)) {
                    plan.issues.add(new Issue(Kind.DEP_GAME_MISMATCH, dep.name, entry.name, d, pick.version,
                            String.join(", ", pick.gameVersions)));
                }
                effective.put(d.id, pick.version);
                planned.add(d.id);
                resolve(dep, pick);
                plan.steps.add(new Step(dep, pick, true, replaces));
            }
        }

        /** The newest release inside the bounds, preferring ones made for this game. Null when none fits. */
        Version pick(Entry dep, Dependency d) {
            Version best = null;
            boolean bestMatchesGame = false;
            for (Version v : dep.versions) {
                if (!ModVersions.satisfies(d, v.version)) continue;
                boolean matches = v.supportsGame(gameVersion);
                boolean better = best == null
                        || (matches && !bestMatchesGame)
                        || (matches == bestMatchesGame && ModVersions.compare(v.version, best.version) > 0);
                if (better) {
                    best = v;
                    bestMatchesGame = matches;
                }
            }
            return best;
        }
    }
}
