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

    public RemoteModRepository(Context context) {
        String override = dev.segually.sbamodloader.BuildConfig.DEBUG
                ? dev.segually.sbamodloader.SBASettings.getDebugIndexUrl(context) : "";
        this.indexUrl = override.isEmpty() ? INDEX_URL : override;
        // A different index never shares the saved copy of the official one.
        this.cache = new File(context.getApplicationContext().getFilesDir(),
                override.isEmpty() ? "repository-index.json" : "repository-index-debug.json");
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
            List<Entry> entries = ModIndexParser.parse(json); // only a valid index replaces the saved copy
            Files.write(cache.toPath(), json.getBytes(StandardCharsets.UTF_8));
            applyStats(entries);
            return entries;
        } catch (IOException | JSONException e) {
            Log.w(TAG, "Could not load the repository, trying the saved copy", e);
            if (!cache.isFile()) throw e instanceof IOException ? (IOException) e : new IOException(e);
            try {
                List<Entry> entries = ModIndexParser.parse(new String(Files.readAllBytes(cache.toPath()), StandardCharsets.UTF_8));
                fromCache = true;
                return entries;
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
                long n = stats.optLong(e.id, 0);
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
                return ModIndexParser.parse(new String(Files.readAllBytes(cache.toPath()), StandardCharsets.UTF_8));
            }
        } catch (IOException | JSONException e) {
            Log.w(TAG, "The saved repository copy is unreadable", e);
        }
        return new java.util.ArrayList<>();
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
