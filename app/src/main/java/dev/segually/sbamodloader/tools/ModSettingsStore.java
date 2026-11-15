package dev.segually.sbamodloader.tools;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dev.segually.sbamodloader.TargetGame;

/**
 * Finds the settings a mod declares and keeps the values the player chose.
 *
 * <p>Values live in {@code ModSettings/<id>.json} next to the loader's other data. Whenever one changes (and before each
 * launch) they are also written the ways mods can read them: {@code ModSettings/<id>.settings}, plain text for native
 * mods (see {@code sba_get_setting_*} in the native API), and, for a plugin that names its GUID, a BepInEx config file.
 * Changes reach the game the next time it starts.
 */
public final class ModSettingsStore {
    private static final String TAG = "ModSettingsStore";
    private static final String CFG_MARKER = "## Settings file written by SBAModLoader";
    private static final Map<String, ModSettings> CACHE = new HashMap<>();
    private static final Map<String, Boolean> HAS_NONE = new HashMap<>();

    private ModSettingsStore() {
    }

    public static File dir() {
        return Utilities.getExternalSBAModLoaderDirectory(TargetGame.PACKAGE_NAME, "ModSettings");
    }

    /** The settings {@code mod} declares, or null. A pack declares them in sbapack.json, other files in a sidecar. */
    public static synchronized ModSettings schemaFor(File mod) {
        String key = mod.getAbsolutePath() + "|" + mod.length() + "|" + mod.lastModified();
        if (HAS_NONE.containsKey(key)) return null;
        ModSettings cached = CACHE.get(key);
        if (cached != null) return cached;
        ModSettings found = null;
        try {
            String base = ModPacks.baseName(mod);
            String fallback = base.contains(".") ? base.substring(0, base.lastIndexOf('.')) : base;
            JSONObject json = null;
            if (base.endsWith(ModPacks.PACK_EXTENSION)) {
                try (ZipFile zip = new ZipFile(mod)) {
                    ZipEntry entry = zip.getEntry("sbapack.json");
                    if (entry != null) {
                        try (InputStream in = zip.getInputStream(entry)) {
                            json = new JSONObject(new String(in.readNBytes(1 << 16), StandardCharsets.UTF_8));
                        }
                    }
                }
            } else {
                // Both "Plugin.dll.sbasettings.json" and "Plugin.sbasettings.json" are accepted.
                File sidecar = new File(mod.getParentFile(), base + ".sbasettings.json");
                if (!sidecar.isFile()) sidecar = new File(mod.getParentFile(), fallback + ".sbasettings.json");
                if (sidecar.isFile() && sidecar.length() <= (1 << 16)) {
                    json = new JSONObject(new String(Files.readAllBytes(sidecar.toPath()), StandardCharsets.UTF_8));
                }
            }
            if (json != null) found = ModSettings.parse(json, fallback);
        } catch (IOException | JSONException | RuntimeException e) {
            Log.w(TAG, "Could not read the settings of " + mod.getName(), e);
        }
        if (found == null) HAS_NONE.put(key, true);
        else CACHE.put(key, found);
        return found;
    }

    private static File valuesFile(ModSettings s) {
        return new File(dir(), s.id + ".json");
    }

    /** The values the player chose (only those that differ from the default are kept, but any may be present). */
    public static Map<String, String> load(ModSettings s) {
        Map<String, String> out = new HashMap<>();
        File f = valuesFile(s);
        if (!f.isFile() || f.length() > (1 << 16)) return out;
        try {
            JSONObject o = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            java.util.Iterator<String> keys = o.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                ModSettings.Field field = s.field(k);
                String v = field == null ? null : field.coerce(o.optString(k, null));
                if (v != null) out.put(k, v);
            }
        } catch (IOException | JSONException e) {
            Log.w(TAG, "Unreadable settings for " + s.id, e);
        }
        return out;
    }

    /** The current value of a field: the chosen one, or its default. */
    public static String value(ModSettings s, Map<String, String> chosen, String key) {
        ModSettings.Field f = s.field(key);
        if (f == null) return null;
        String v = chosen.get(key);
        return v != null ? v : f.defaultValue;
    }

    /** Saves one value (made valid first) and writes the files mods read. Returns the value that was saved. */
    public static synchronized String set(ModSettings s, String key, String text) throws IOException {
        ModSettings.Field field = s.field(key);
        if (field == null) throw new IOException("Unknown setting " + key);
        String v = field.coerce(text);
        if (v == null) v = field.defaultValue;
        Map<String, String> values = load(s);
        values.put(key, v);
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, String> e : values.entrySet()) o.put(e.getKey(), e.getValue());
            writeAtomic(valuesFile(s), o.toString(2));
        } catch (JSONException e) {
            throw new IOException(e);
        }
        writeOutputs(s, values);
        return v;
    }

    /** Puts every field back to its default. */
    public static synchronized void reset(ModSettings s) throws IOException {
        //noinspection ResultOfMethodCallIgnored
        valuesFile(s).delete();
        writeOutputs(s, new HashMap<>());
    }

    /** Writes the plain text for native mods and the BepInEx config of a plugin. */
    private static void writeOutputs(ModSettings s, Map<String, String> values) throws IOException {
        writeAtomic(new File(dir(), s.id + ".settings"), s.toNativeText(values));
        String cfg = s.toBepInExConfig(values);
        if (!cfg.isEmpty()) {
            File config = new File(new File(Utilities.getExternalSBAModLoaderDirectory(TargetGame.PACKAGE_NAME, "BepInEx"), "config"),
                    s.guid + ".cfg");
            // A config the plugin wrote itself is left alone, so its other entries are not lost.
            boolean ours = !config.isFile() || new String(Files.readAllBytes(config.toPath()), StandardCharsets.UTF_8).startsWith(CFG_MARKER);
            if (ours) writeAtomic(config, cfg);
            else Log.i(TAG, config.getName() + " was written by the plugin; its values are not replaced");
        }
    }

    /** Before a launch: writes the files for every enabled mod that has settings, so they are there when the game starts. */
    public static void syncAll(Context context) {
        try {
            List<File> mods = ModPacks.orderedMods(context);
            File plugins = ModFiles.pluginsDir();
            File[] dlls = plugins.listFiles();
            java.util.ArrayList<File> all = new java.util.ArrayList<>(mods);
            if (dlls != null) for (File f : dlls) if (f.getName().endsWith(".dll")) all.add(f);
            for (File mod : all) {
                if (mod.getName().endsWith(ModPacks.DISABLED)) continue;
                ModSettings s = schemaFor(mod);
                if (s != null) writeOutputs(s, load(s));
            }
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Could not write the settings for this launch", e);
        }
    }

    private static void writeAtomic(File target, String text) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Cannot create " + parent);
        }
        File tmp = new File(target.getPath() + ".tmp");
        Files.write(tmp.toPath(), text.getBytes(StandardCharsets.UTF_8));
        Files.move(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
