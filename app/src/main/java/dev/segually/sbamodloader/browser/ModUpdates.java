package dev.segually.sbamodloader.browser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import dev.segually.sbamodloader.browser.BrowserModels.Entry;
import dev.segually.sbamodloader.browser.BrowserModels.Version;

/**
 * Finds installed mods that have a newer release. Plain Java, no Android classes, so it is tested on a desktop JVM.
 *
 * <p>The release to update to is the newest one that is made for the installed game. When none of the newer releases
 * is, there is no update to offer: a newer release for another game would only break the mod. When the game version is
 * not known, the newest release counts.
 */
public final class ModUpdates {
    private ModUpdates() {
    }

    public static final class Update {
        public final Entry entry;
        public final String installed;
        public final Version target;

        Update(Entry entry, String installed, Version target) {
            this.entry = entry;
            this.installed = installed;
            this.target = target;
        }
    }

    /** The updates available, ordered by mod name. */
    public static List<Update> find(Map<String, Entry> index, Map<String, String> installed, String gameVersion) {
        List<Update> out = new ArrayList<>();
        for (Map.Entry<String, String> have : installed.entrySet()) {
            Entry entry = index.get(have.getKey());
            if (entry == null) continue;
            Version best = null;
            for (Version v : entry.versions) {
                if (ModVersions.compare(v.version, have.getValue()) <= 0) continue;
                if (gameVersion != null && !v.supportsGame(gameVersion)) continue;
                if (best == null || ModVersions.compare(v.version, best.version) > 0) best = v;
            }
            if (best != null) out.add(new Update(entry, have.getValue(), best));
        }
        Collections.sort(out, (a, b) -> a.entry.name.compareToIgnoreCase(b.entry.name));
        return out;
    }
}
