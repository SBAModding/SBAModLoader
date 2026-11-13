package dev.segually.sbamodloader.browser;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** What the mod browser has installed: which version, and exactly which files, so they can be updated and removed. */
public final class InstalledMods {
    public static final class Record {
        public final String id;
        public final String version;
        public final List<File> files;

        Record(String id, String version, List<File> files) {
            this.id = id;
            this.version = version;
            this.files = files;
        }
    }

    private final SharedPreferences prefs;

    public InstalledMods(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("sba_installed_mods", Context.MODE_PRIVATE);
    }

    private JSONObject all() {
        try {
            return new JSONObject(prefs.getString("records", "{}"));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    public Record get(String id) {
        JSONObject o = all().optJSONObject(id);
        if (o == null) return null;
        List<File> files = new ArrayList<>();
        JSONArray a = o.optJSONArray("files");
        if (a != null) for (int i = 0; i < a.length(); i++) files.add(new File(a.optString(i)));
        return new Record(id, o.optString("version", ""), files);
    }

    public void put(String id, String version, List<File> files) {
        try {
            JSONObject root = all();
            JSONArray a = new JSONArray();
            for (File f : files) a.put(f.getAbsolutePath());
            root.put(id, new JSONObject().put("version", version).put("files", a));
            prefs.edit().putString("records", root.toString()).apply();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    public void remove(String id) {
        JSONObject root = all();
        root.remove(id);
        prefs.edit().putString("records", root.toString()).apply();
    }
}
