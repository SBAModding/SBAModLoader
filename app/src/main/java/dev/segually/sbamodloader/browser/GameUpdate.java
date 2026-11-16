package dev.segually.sbamodloader.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.content.pm.PackageInfoCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.segually.sbamodloader.TargetGame;
import dev.segually.sbamodloader.browser.BrowserModels.Entry;
import dev.segually.sbamodloader.browser.BrowserModels.Version;
import dev.segually.sbamodloader.tools.ModFiles;
import dev.segually.sbamodloader.tools.ModPacks;

/**
 * Notices that the game itself was updated and deals with the mods that were made for the old version.
 *
 * <p>Store mods whose installed version does not list the new game version are updated when the store has a release
 * that does, and switched off otherwise (a mod made for another version can crash the game). They are switched back on
 * when they become compatible. Mods added by hand cannot be judged and are only named. The game's copied data and the
 * interop assemblies are refreshed separately, at launch (see BootstrapActivity).
 */
public final class GameUpdate {
    private static final String TAG = "GameUpdate";
    private static final String PREFS = "sba_game_update";

    public enum Fit { COMPATIBLE, INCOMPATIBLE, UNKNOWN }

    public enum Action { NOTHING, UPDATE, DISABLE, ENABLE }

    /** What happened, shown to the player once. */
    public static final class Report {
        public String from = "", to = "";
        /** True when the game's version changed (as opposed to mods only being switched back on). */
        public boolean gameChanged;
        public final List<String> updated = new ArrayList<>();
        public final List<String> disabled = new ArrayList<>();
        public final List<String> disabledIds = new ArrayList<>();
        public final List<String> failed = new ArrayList<>();
        public final List<String> reenabled = new ArrayList<>();
        public final List<String> unchecked = new ArrayList<>();

        public boolean isEmpty() {
            return !gameChanged && reenabled.isEmpty();
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("from", from).put("to", to).put("gameChanged", gameChanged)
                    .put("updated", new JSONArray(updated)).put("disabled", new JSONArray(disabled))
                    .put("disabledIds", new JSONArray(disabledIds)).put("failed", new JSONArray(failed))
                    .put("reenabled", new JSONArray(reenabled)).put("unchecked", new JSONArray(unchecked));
        }

        static Report fromJson(JSONObject o) {
            Report r = new Report();
            r.from = o.optString("from");
            r.to = o.optString("to");
            r.gameChanged = o.optBoolean("gameChanged");
            fill(r.updated, o.optJSONArray("updated"));
            fill(r.disabled, o.optJSONArray("disabled"));
            fill(r.disabledIds, o.optJSONArray("disabledIds"));
            fill(r.failed, o.optJSONArray("failed"));
            fill(r.reenabled, o.optJSONArray("reenabled"));
            fill(r.unchecked, o.optJSONArray("unchecked"));
            return r;
        }

        private static void fill(List<String> into, JSONArray a) {
            if (a != null) for (int i = 0; i < a.length(); i++) into.add(a.optString(i));
        }
    }

    private GameUpdate() {
    }

    /** Whether the installed release of a mod lists {@code game}. A release the repository does not list is UNKNOWN. */
    public static Fit fit(Entry entry, String installedVersion, String game) {
        if (entry == null || game == null || installedVersion == null) return Fit.UNKNOWN;
        for (Version v : entry.versions) {
            if (v.version.equals(installedVersion)) return v.supportsGame(game) ? Fit.COMPATIBLE : Fit.INCOMPATIBLE;
        }
        return Fit.UNKNOWN;
    }

    /**
     * What to do with one mod.
     *
     * @param updateAvailable the store has a newer release of it that lists the new game version
     * @param disabledByUs    the loader switched it off earlier because of a game update
     * @param forced          the player chose to keep it on for this game version
     */
    public static Action decide(Fit fit, boolean updateAvailable, boolean disabledByUs, boolean forced) {
        switch (fit) {
            case COMPATIBLE:
                return disabledByUs ? Action.ENABLE : Action.NOTHING;
            case INCOMPATIBLE:
                if (forced) return Action.NOTHING;
                return updateAvailable ? Action.UPDATE : Action.DISABLE;
            default:
                return Action.NOTHING;
        }
    }

    // ---- the pass ------------------------------------------------------------------------------------------------

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static PackageInfo gameInfo(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(TargetGame.PACKAGE_NAME, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /** A key that changes when the game is updated: its version name and version code. */
    public static String gameKey(Context context) {
        PackageInfo info = gameInfo(context);
        return info == null ? "" : info.versionName + ":" + PackageInfoCompat.getLongVersionCode(info);
    }

    private static String nameOf(String key) {
        int colon = key.lastIndexOf(':');
        return colon < 0 ? key : key.substring(0, colon);
    }

    /**
     * Compares the game with the version last seen and deals with the mods. Blocking (network): call it off the main
     * thread. Returns a report when there is something to tell, and also saves it for {@link #takePending}.
     */
    public static synchronized Report reconcile(Context context) {
        Context app = context.getApplicationContext();
        String key = gameKey(app);
        if (key.isEmpty()) return null;
        SharedPreferences prefs = prefs(app);
        String last = prefs.getString("last", "");
        Set<String> disabled = new HashSet<>(prefs.getStringSet("disabled", new HashSet<>()));
        if (last.isEmpty()) {
            // First run with this feature: nothing to compare with yet.
            prefs.edit().putString("last", key).apply();
            if (disabled.isEmpty()) return null;
        }
        boolean changed = !last.isEmpty() && !last.equals(key);
        if (!changed && disabled.isEmpty()) return null;

        ModHealth health = ModHealth.check(app, true);
        if (health.index.isEmpty()) return null; // nothing to judge by yet; the next start tries again
        String game = nameOf(key);
        Set<String> forced = key.equals(prefs.getString("forcedFor", ""))
                ? new HashSet<>(prefs.getStringSet("forced", new HashSet<>())) : new HashSet<>();

        Map<String, String> versions = ModInstaller.installedVersions(app, health.index.values());
        Map<String, ModUpdates.Update> updates = new HashMap<>();
        for (ModUpdates.Update u : health.updates) updates.put(u.entry.id, u);
        InstalledMods records = new InstalledMods(app);

        Report report = new Report();
        report.from = nameOf(last);
        report.to = game;
        report.gameChanged = changed;
        for (Map.Entry<String, String> have : versions.entrySet()) {
            String id = have.getKey();
            if (!changed && !disabled.contains(id)) continue;
            Entry entry = health.index.get(id);
            InstalledMods.Record record = records.get(id);
            if (entry == null || record == null) continue;
            ModUpdates.Update update = updates.get(id);
            Action action = decide(fit(entry, have.getValue(), game), update != null, disabled.contains(id), forced.contains(id));
            switch (action) {
                case UPDATE:
                    try {
                        InstallPlanner.Plan plan = InstallPlanner.plan(update.entry, update.target, health.index, versions, game);
                        if (plan.blocked()) throw new IOException("The update cannot be installed");
                        ModInstaller.install(app, plan, (mod, done, total) -> { });
                        report.updated.add(entry.name);
                        disabled.remove(id);
                    } catch (IOException | RuntimeException e) {
                        Log.w(TAG, "Could not update " + id + " for game " + game, e);
                        report.failed.add(entry.name);
                        setEnabled(record, false);
                        disabled.add(id);
                        report.disabledIds.add(id);
                    }
                    break;
                case DISABLE:
                    setEnabled(record, false);
                    disabled.add(id);
                    report.disabled.add(entry.name);
                    report.disabledIds.add(id);
                    break;
                case ENABLE:
                    setEnabled(record, true);
                    disabled.remove(id);
                    report.reenabled.add(entry.name);
                    break;
                default:
                    break;
            }
        }
        if (changed) report.unchecked.addAll(handAdded(app, records));

        SharedPreferences.Editor edit = prefs.edit().putStringSet("disabled", disabled);
        if (changed) edit.putString("last", key);
        if (!report.isEmpty()) {
            try {
                edit.putString("report", report.toJson().toString());
            } catch (JSONException e) {
                Log.w(TAG, "Could not save the report", e);
            }
        }
        edit.apply();
        return report.isEmpty() ? null : report;
    }

    /** The report not yet shown, removed when read. */
    public static Report takePending(Context context) {
        SharedPreferences prefs = prefs(context);
        String text = prefs.getString("report", null);
        if (text == null) return null;
        prefs.edit().remove("report").apply();
        try {
            return Report.fromJson(new JSONObject(text));
        } catch (JSONException e) {
            return null;
        }
    }

    /** The player's choice to keep the mods that were switched off: turns them on and does not switch them off again for this game version. */
    public static synchronized void keepOn(Context context, List<String> ids) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = prefs(app);
        Set<String> disabled = new HashSet<>(prefs.getStringSet("disabled", new HashSet<>()));
        Set<String> forced = new HashSet<>();
        if (gameKey(app).equals(prefs.getString("forcedFor", ""))) forced.addAll(prefs.getStringSet("forced", new HashSet<>()));
        InstalledMods records = new InstalledMods(app);
        for (String id : ids) {
            InstalledMods.Record r = records.get(id);
            if (r != null) setEnabled(r, true);
            disabled.remove(id);
            forced.add(id);
        }
        prefs.edit().putStringSet("disabled", disabled).putStringSet("forced", forced)
                .putString("forcedFor", gameKey(app)).apply();
    }

    /** Debug builds only: set the game version the loader thinks it saw last ("clear" forgets it). */
    public static void debugSetLast(Context context, String key) {
        if (!dev.segually.sbamodloader.BuildConfig.DEBUG) return;
        SharedPreferences.Editor e = prefs(context).edit();
        if ("clear".equals(key)) e.clear(); else e.putString("last", key);
        e.apply();
    }

    private static void setEnabled(InstalledMods.Record record, boolean on) {
        for (File f : record.files) {
            File off = new File(f.getPath() + ModPacks.DISABLED);
            if (on) {
                if (off.exists() && !f.exists() && !off.renameTo(f)) Log.w(TAG, "Could not switch on " + f);
            } else if (f.exists() && !f.renameTo(off)) {
                Log.w(TAG, "Could not switch off " + f);
            }
        }
    }

    /** Names of the enabled mods that were added by hand: they cannot be checked against a game version. */
    private static List<String> handAdded(Context app, InstalledMods records) {
        List<File> files = new ArrayList<>(ModPacks.orderedMods(app));
        File[] dlls = ModFiles.pluginsDir().listFiles();
        if (dlls != null) for (File f : dlls) if (f.getName().endsWith(".dll")) files.add(f);
        List<String> out = new ArrayList<>();
        for (File f : files) {
            if (f.getName().endsWith(ModPacks.DISABLED)) continue;
            if (records.idForFile(f) == null) out.add(ModPacks.baseName(f));
        }
        return out;
    }
}
