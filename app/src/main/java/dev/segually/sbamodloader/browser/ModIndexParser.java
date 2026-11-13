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
import dev.segually.sbamodloader.browser.BrowserModels.Entry;
import dev.segually.sbamodloader.browser.BrowserModels.Kind;
import dev.segually.sbamodloader.browser.BrowserModels.Source;
import dev.segually.sbamodloader.browser.BrowserModels.Sub;

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
        e.version = o.getString("version");
        e.size = o.getLong("size");
        e.sha256 = o.getString("sha256").toLowerCase(Locale.ROOT);
        if (e.sha256.length() != 64 || e.size <= 0) throw new JSONException("Bad hash or size");
        // Statistics are optional: the repository does not carry them.
        e.downloads = o.optLong("downloads", 0);
        e.rating = (float) o.optDouble("rating", 0);
        e.ratingCount = o.optInt("ratingCount", 0);
        e.license = o.optString("license", "");
        e.openSource = o.getBoolean("openSource");
        e.changelog = o.optString("changelog", "");
        e.updatedMillis = date.parse(o.getString("updated")).getTime();

        JSONArray cats = o.getJSONArray("categories");
        for (int c = 0; c < cats.length(); c++) {
            String[] parts = cats.getString(c).split("/");
            e.categories.add(Sub.of(Category.of(parts[0]), parts[1]));
        }
        if (e.categories.isEmpty()) throw new JSONException("No categories");
        strings(o, "gameVersions", e.gameVersions);
        strings(o, "tags", e.tags);
        strings(o, "requires", e.requires);
        strings(o, "mirrors", e.mirrors);
        for (String m : e.mirrors) if (!m.startsWith("https://")) throw new JSONException("Mirror is not https");

        JSONObject s = o.getJSONObject("source");
        boolean github = s.getString("kind").equals("github");
        e.source = new Source(github, s.optString("repo"), s.optString("tag"), s.optString("asset"), s.optString("url"));
        if (!e.source.downloadUrl().startsWith("https://")) throw new JSONException("Source is not https");

        JSONObject a = o.getJSONObject("audited");
        e.audit = new Audit(a.getString("version"), a.getString("date"), a.getString("by"));
        // An audit covers one exact version; anything else is not shown as audited, so it is not offered at all.
        if (!e.audit.version.equals(e.version)) throw new JSONException("Not audited at this version");
        return e;
    }

    private static void strings(JSONObject o, String key, List<String> into) throws JSONException {
        JSONArray a = o.optJSONArray(key);
        if (a != null) for (int i = 0; i < a.length(); i++) into.add(a.getString(i));
    }
}
