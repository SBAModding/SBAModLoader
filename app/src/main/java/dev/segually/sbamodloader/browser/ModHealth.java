package dev.segually.sbamodloader.browser;

import android.content.Context;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.segually.sbamodloader.TargetGame;
import dev.segually.sbamodloader.browser.BrowserModels.Entry;

/**
 * How the installed store mods stand: which have updates, and whether they need each other in ways that do not hold.
 * Checks against the repository (or its saved copy when offline). Call from a background thread.
 */
public final class ModHealth {
    public final Map<String, Entry> index = new HashMap<>();
    public List<ModUpdates.Update> updates = java.util.Collections.emptyList();
    /** Dependency problems among the installed mods, and what would fix them. */
    public InstallPlanner.Plan fixes = new InstallPlanner.Plan();
    public String gameVersion;
    public boolean fromCache;

    /** {@code useNetwork} false only reads the saved copy of the repository. */
    /** Debug builds only: pretend there are updates and problems, to see how they look. Set from the launch intent. */
    public static boolean debugFake;

    public static ModHealth check(Context context, boolean useNetwork) {
        Context app = context.getApplicationContext();
        ModHealth h = new ModHealth();
        if (dev.segually.sbamodloader.BuildConfig.DEBUG && debugFake) return fake();
        Repositories.Result read = Repositories.load(app, useNetwork);
        List<Entry> entries = read.entries;
        h.fromCache = read.anyFromCache;
        for (Entry e : entries) h.index.put(e.id, e);
        try {
            h.gameVersion = app.getPackageManager().getPackageInfo(TargetGame.PACKAGE_NAME, 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            h.gameVersion = null;
        }
        Map<String, String> installed = ModInstaller.installedVersions(app, entries);
        h.updates = ModUpdates.find(h.index, installed, h.gameVersion);
        h.fixes = InstallPlanner.planFixes(h.index, installed, h.gameVersion);
        return h;
    }

    private static ModHealth fake() {
        ModHealth h = new ModHealth();
        BrowserModels.Version newer = new BrowserModels.Version();
        newer.version = "1.2.0";
        newer.gameVersions = new java.util.ArrayList<>(java.util.Collections.singletonList("13.0.8"));
        newer.dependencies = new java.util.ArrayList<>();
        newer.size = 1000;
        Entry e = new Entry();
        e.id = "photon-nickname";
        e.name = "Photon Nickname";
        e.versions = new java.util.ArrayList<>(java.util.Collections.singletonList(newer));
        e.version = "1.2.0";
        h.updates = java.util.Collections.singletonList(new ModUpdates.Update(e, "1.0.0", newer));
        BrowserModels.Dependency need = new BrowserModels.Dependency("segualsba-client", "1.2.7", null);
        h.fixes.issues.add(new InstallPlanner.Issue(InstallPlanner.Kind.DEP_OUTDATED, "SegualSBA Client", "Photon Nickname", need, "1.0.1", null));
        h.fixes.issues.add(new InstallPlanner.Issue(InstallPlanner.Kind.DEP_NEEDS_OLDER, "Hive Reborn", "Super Cars Adventure",
                new BrowserModels.Dependency("hive-reborn", null, "1.0.0"), "1.0.2", null));
        h.fixes.steps.add(new InstallPlanner.Step(e, newer, true, "1.0.1"));
        h.gameVersion = "13.0.8";
        return h;
    }

    public boolean hasProblems() {
        return !fixes.issues.isEmpty();
    }
}
