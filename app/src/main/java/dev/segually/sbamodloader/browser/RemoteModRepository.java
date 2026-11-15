package dev.segually.sbamodloader.browser;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import dev.segually.sbamodloader.browser.BrowserModels.Entry;

/**
 * The real mod repository: {@code index.json} from the SBAModding/ModRepository GitHub repository, fetched on every
 * load. The last good copy is kept on disk so the browser still works offline.
 */
public final class RemoteModRepository implements ModRepository {
    public static final String INDEX_URL = "https://raw.githubusercontent.com/SBAModding/ModRepository/main/index.json";
    private static final String TAG = "RemoteModRepository";
    private static final int MAX_INDEX_BYTES = 8 << 20;

    private final String indexUrl;
    private final File cache;
    private boolean fromCache;

    private final String repoId;
    private final String repoName;

    /** The official repository. */
    public RemoteModRepository(Context context) {
        this(context, RepoSource.official());
    }

    public RemoteModRepository(Context context, RepoSource source) {
        String override = dev.segually.sbamodloader.BuildConfig.DEBUG && source.isOfficial()
                ? dev.segually.sbamodloader.SBASettings.getDebugIndexUrl(context) : "";
        this.indexUrl = override.isEmpty() ? source.url : override;
        this.repoId = source.id();
        this.repoName = source.name;
        // Every repository has its own saved copy (the official one keeps its old file name).
        String file = !override.isEmpty() ? "repository-index-debug.json"
                : repoId == null ? "repository-index.json" : "repository-index-" + repoId + ".json";
        this.cache = new File(context.getApplicationContext().getFilesDir(), file);
    }

    /** Notes on each mod which repository it came from and where its pictures are. */
    private List<Entry> tag(List<Entry> entries) {
        for (Entry e : entries) {
            e.repoName = repoName;
            e.imageBase = imageBase();
        }
        return entries;
    }

    /** Where this repository's pictures are resolved from: the folder of its index. */
    public String imageBase() {
        return indexUrl.substring(0, indexUrl.lastIndexOf('/') + 1);
    }

    /** True when the last {@link #load()} could not reach the repository and used the saved copy. */
    public boolean loadedFromCache() {
        return fromCache;
    }

    @Override
    public List<Entry> load() throws IOException {
        fromCache = false;
        try {
            String json = fetch(indexUrl);
            List<Entry> entries = ModIndexParser.parse(json, repoId); // only a valid index replaces the saved copy
            Files.write(cache.toPath(), json.getBytes(StandardCharsets.UTF_8));
            applyStats(entries);
            return tag(entries);
        } catch (IOException | JSONException e) {
            Log.w(TAG, "Could not load the repository, trying the saved copy", e);
            if (!cache.isFile()) throw e instanceof IOException ? (IOException) e : new IOException(e);
            try {
                List<Entry> entries = ModIndexParser.parse(new String(Files.readAllBytes(cache.toPath()), StandardCharsets.UTF_8), repoId);
                fromCache = true;
                return tag(entries);
            } catch (JSONException ex) {
                throw new IOException("The saved repository copy is unreadable", ex);
            }
        }
    }

    /**
     * Download counts: {@code stats.json} next to the index, a map of mod id to total downloads. Best effort: without
     * it (offline, or a repository that has none) the page just shows no count.
     */
    private void applyStats(List<Entry> entries) {
        try {
            String statsUrl = indexUrl.substring(0, indexUrl.lastIndexOf('/') + 1) + "stats.json";
            org.json.JSONObject stats = new org.json.JSONObject(fetch(statsUrl));
            for (Entry e : entries) {
                long n = stats.optLong(e.rawId(), 0);
                if (n > 0) e.downloads = n;
            }
        } catch (IOException | JSONException | RuntimeException ignored) {
            // No counts today.
        }
    }

    /** The saved copy of the index, without touching the network. Empty when there is none. */
    public List<Entry> loadCached() {
        try {
            if (cache.isFile()) {
                return tag(ModIndexParser.parse(new String(Files.readAllBytes(cache.toPath()), StandardCharsets.UTF_8), repoId));
            }
        } catch (IOException | JSONException e) {
            Log.w(TAG, "The saved repository copy is unreadable", e);
        }
        return new java.util.ArrayList<>();
    }

    /**
     * Reads the index at {@code url} to see that it is a mod repository, and returns it as a source named after what the
     * index calls itself (or its host). Nothing is saved. Call from a background thread.
     */
    public static RepoSource probe(String url) throws IOException {
        String json = fetch(url);
        try {
            ModIndexParser.parse(json, "rx"); // throws when it is not an index at all
        } catch (JSONException e) {
            throw new IOException("That address is not a mod repository");
        }
        String name = ModIndexParser.indexName(json);
        if (name == null) {
            String host = url.replaceFirst("^https?://", "");
            name = host.contains("/") ? host.substring(0, host.indexOf('/')) : host;
        }
        return new RepoSource(name, url, true);
    }

    private static String fetch(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(20_000);
        c.setRequestProperty("User-Agent", "SBAModLoader");
        c.setRequestProperty("Cache-Control", "no-cache");
        try {
            if (c.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_INDEX_BYTES) throw new IOException("The index is too large");
                }
                return out.toString(StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }
}
