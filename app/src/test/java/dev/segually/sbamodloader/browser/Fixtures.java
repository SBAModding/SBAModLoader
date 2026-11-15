package dev.segually.sbamodloader.browser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.segually.sbamodloader.browser.BrowserModels.Dependency;
import dev.segually.sbamodloader.browser.BrowserModels.Entry;
import dev.segually.sbamodloader.browser.BrowserModels.Source;
import dev.segually.sbamodloader.browser.BrowserModels.Version;

/** Small builders for mods in tests. */
final class Fixtures {
    private Fixtures() {
    }

    /** A release made for the given game versions, 1000 bytes. */
    static Version release(String version, String game, Dependency... dependencies) {
        Version v = new Version();
        v.version = version;
        v.gameVersions = new ArrayList<>(Arrays.asList(game.split(",")));
        v.dependencies = new ArrayList<>(Arrays.asList(dependencies));
        v.size = 1000;
        v.sha256 = "0".repeat(64);
        v.source = new Source(false, "", "", "", "https://example.com/x-" + version + ".sbapack");
        return v;
    }

    /** A mod with these releases, which must be listed newest first. */
    static Entry mod(String id, Version... releases) {
        Entry e = new Entry();
        e.id = id;
        e.name = id.toUpperCase();
        e.versions = new ArrayList<>(Arrays.asList(releases));
        e.version = releases[0].version;
        return e;
    }

    static Dependency dep(String id, String min, String max) {
        return new Dependency(id, min, max);
    }

    static Map<String, Entry> index(Entry... mods) {
        Map<String, Entry> out = new HashMap<>();
        for (Entry m : mods) out.put(m.id, m);
        return out;
    }

    static Map<String, String> installed(String... idAndVersion) {
        Map<String, String> out = new HashMap<>();
        for (int i = 0; i < idAndVersion.length; i += 2) out.put(idAndVersion[i], idAndVersion[i + 1]);
        return out;
    }

    static List<InstallPlanner.Kind> kinds(InstallPlanner.Plan plan) {
        List<InstallPlanner.Kind> out = new ArrayList<>();
        for (InstallPlanner.Issue i : plan.issues) out.add(i.kind);
        return out;
    }

    static List<String> steps(InstallPlanner.Plan plan) {
        List<String> out = new ArrayList<>();
        for (InstallPlanner.Step s : plan.steps) out.add(s.entry.id + "@" + s.version.version);
        return out;
    }
}
