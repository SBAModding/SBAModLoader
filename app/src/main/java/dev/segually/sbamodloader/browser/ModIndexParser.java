package dev.segually.sbamodloader.browser;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import dev.segually.sbamodloader.browser.BrowserModels.Audit;
import dev.segually.sbamodloader.browser.BrowserModels.Category;
import dev.segually.sbamodloader.browser.BrowserModels.Dependency;
import dev.segually.sbamodloader.browser.BrowserModels.Entry;
import dev.segually.sbamodloader.browser.BrowserModels.Kind;
import dev.segually.sbamodloader.browser.BrowserModels.Source;
import dev.segually.sbamodloader.browser.BrowserModels.Sub;
import dev.segually.sbamodloader.browser.BrowserModels.Version;

/** Reads the repository's index.json. */
public final class ModIndexParser {
    private static final String TAG = "ModIndexParser";

    private ModIndexParser() {
    }

    /**
     * Parses the index. An entry that is malformed is skipped (and logged), so one bad entry cannot hide every mod;
     * a file that is not an index at all, or has an unknown schema version, is an error.
     */
    public static List<Entry> parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (root.getInt("schemaVersion") != 1) throw new JSONException("Unsupported schema version");
        JSONArray mods = root.getJSONArray("mods");
        SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        date.setTimeZone(TimeZone.getTimeZone("UTC"));
        List<Entry> out = new ArrayList<>();
        for (int i = 0; i < mods.length(); i++) {
            try {
                out.add(entry(mods.getJSONObject(i), date));
            } catch (JSONException | ParseException | IllegalArgumentException e) {
                Log.w(TAG, "Skipping a malformed mod entry (#" + i + "): " + e.getMessage());
            }
        }
        return out;
    }

    private static Entry entry(JSONObject o, SimpleDateFormat date) throws JSONException, ParseException {
        Entry e = new Entry();
        e.id = o.getString("id");
        e.name = o.getString("name");
        e.author = o.getString("author");
        e.kind = Kind.of(o.getString("kind"));
        e.summary = o.getString("summary");
        e.description = o.optString("description", e.summary);
        // Statistics are optional: the repository does not carry them.
        e.downloads = o.optLong("downloads", 0);
        e.rating = (float) o.optDouble("rating", 0);
        e.ratingCount = o.optInt("ratingCount", 0);
        e.license = o.optString("license", "");
        e.openSource = o.getBoolean("openSource");

        JSONArray cats = o.getJSONArray("categories");
        for (int c = 0; c < cats.length(); c++) {
            String[] parts = cats.getString(c).split("/");
            e.categories.add(Sub.of(Category.of(parts[0]), parts[1]));
        }
        if (e.categories.isEmpty()) throw new JSONException("No categories");
        strings(o, "tags", e.tags);
        strings(o, "requires", e.requires);

        // The top level is the newest release. A bad newest release drops the mod, as before.
        Version newest = release(o, date);
        e.versions.add(newest);
        // Older releases are optional extras: one that is malformed is skipped and never hides the mod.
        JSONArray previous = o.optJSONArray("previousVersions");
        if (previous != null) {
            for (int i = 0; i < previous.length(); i++) {
                try {
                    Version old = release(previous.getJSONObject(i), date);
                    if (e.version(old.version) == null) e.versions.add(old);
                } catch (JSONException | ParseException | IllegalArgumentException ex) {
                    Log.w(TAG, e.id + ": skipping a malformed older version (#" + i + "): " + ex.getMessage());
                }
            }
        }
        // Loaders only trust the order they are given when it is the order they sort by.
        java.util.Collections.sort(e.versions, (a, b) -> ModVersions.compare(b.version, a.version));

        // The fields the rest of the app already reads describe the newest release.
        Version latest = e.latest();
        e.version = latest.version;
        e.size = latest.size;
        e.sha256 = latest.sha256;
        e.changelog = latest.changelog;
        e.gameVersions = latest.gameVersions;
        e.mirrors = latest.mirrors;
        e.updatedMillis = latest.updatedMillis;
        e.source = latest.source;
        e.audit = latest.audit;
        // A release that depends on something is shown with it even by code that only knows ids.
        if (e.requires.isEmpty()) for (Dependency d : latest.dependencies) e.requires.add(d.id);

        e.icon = image(o.optString("icon", ""));
        JSONArray shots = o.optJSONArray("screenshots");
        if (shots != null) {
            for (int i = 0; i < shots.length() && e.screenshots.size() < 8; i++) {
                String path = image(shots.optString(i, ""));
                if (path != null) e.screenshots.add(path);
            }
        }
        return e;
    }

    /** One release: the top level of a mod, or an entry of its previousVersions. */
    private static Version release(JSONObject o, SimpleDateFormat date) throws JSONException, ParseException {
        Version v = new Version();
        v.version = o.getString("version");
        v.size = o.getLong("size");
        v.sha256 = o.getString("sha256").toLowerCase(Locale.ROOT);
        if (v.sha256.length() != 64 || v.size <= 0) throw new JSONException("Bad hash or size");
        v.changelog = o.optString("changelog", "");
        v.updatedMillis = date.parse(o.getString("updated")).getTime();
        strings(o, "gameVersions", v.gameVersions);
        strings(o, "mirrors", v.mirrors);
        for (String m : v.mirrors) if (!m.startsWith("https://")) throw new JSONException("Mirror is not https");

        JSONObject s = o.getJSONObject("source");
        boolean github = s.getString("kind").equals("github");
        v.source = new Source(github, s.optString("repo"), s.optString("tag"), s.optString("asset"), s.optString("url"));
        if (!v.source.downloadUrl().startsWith("https://")) throw new JSONException("Source is not https");

        JSONArray deps = o.optJSONArray("dependencies");
        if (deps != null) {
            for (int i = 0; i < deps.length(); i++) {
                JSONObject d = deps.getJSONObject(i);
                String min = d.optString("min", "");
                String max = d.optString("max", "");
                v.dependencies.add(new Dependency(d.getString("id"), min.isEmpty() ? null : min, max.isEmpty() ? null : max));
            }
        }

        JSONObject a = o.getJSONObject("audited");
        v.audit = new Audit(a.getString("version"), a.getString("date"), a.getString("by"));
        // An audit covers one exact version; anything else is not shown as audited, so it is not offered at all.
        if (!v.audit.version.equals(v.version)) throw new JSONException("Not audited at this version");
        return v;
    }

    /** A repository-relative image path under images/, or null for anything else (so it is never fetched). */
    private static String image(String path) {
        if (path.isEmpty() || !path.startsWith("images/") || path.contains("..") || path.contains("//")) return null;
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp")
                ? path : null;
    }

    private static void strings(JSONObject o, String key, List<String> into) throws JSONException {
        JSONArray a = o.optJSONArray(key);
        if (a != null) for (int i = 0; i < a.length(); i++) into.add(a.getString(i));
    }
}
