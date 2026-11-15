package dev.segually.sbamodloader.browser;

import android.content.Context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import dev.segually.sbamodloader.SBASettings;
import dev.segually.sbamodloader.browser.BrowserModels.Entry;

/** Every repository the player uses (the official one, then the ones they added), read together. */
public final class Repositories {
    private Repositories() {
    }

    public static final class Result {
        public final List<Entry> entries = new ArrayList<>();
        /** True when at least one repository could only be read from its saved copy. */
        public boolean anyFromCache;
        /** Names of repositories that could not be read at all. */
        public final List<String> failed = new ArrayList<>();
        /** The official repository could not be read (neither live nor from a saved copy). */
        public boolean officialFailed;
    }

    /** The official repository first, then the enabled extra ones. */
    public static List<RepoSource> enabled(Context context) {
        List<RepoSource> out = new ArrayList<>();
        out.add(RepoSource.official());
        for (RepoSource r : SBASettings.getExtraRepos(context)) if (r.enabled && !r.isOfficial()) out.add(r);
        return out;
    }

    /** Reads every repository. {@code network} false only uses the saved copies. Call from a background thread. */
    public static Result load(Context context, boolean network) {
        Result result = new Result();
        for (RepoSource source : enabled(context)) {
            RemoteModRepository repo = new RemoteModRepository(context, source);
            try {
                List<Entry> entries;
                if (network) {
                    entries = repo.load();
                    if (repo.loadedFromCache()) result.anyFromCache = true;
                } else {
                    entries = repo.loadCached();
                    result.anyFromCache = true;
                }
                result.entries.addAll(entries);
            } catch (IOException e) {
                result.failed.add(source.name);
                if (source.isOfficial()) result.officialFailed = true;
            }
        }
        return result;
    }
}
