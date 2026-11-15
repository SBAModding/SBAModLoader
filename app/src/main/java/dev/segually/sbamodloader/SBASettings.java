package dev.segually.sbamodloader;

import android.content.Context;
import android.content.SharedPreferences;

public final class SBASettings {
    private static final String PREFS_NAME = "sba_settings";
    private static final String KEY_MOD_ORDER = "mod_order";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_MATERIAL_YOU = "material_you";
    private static final String KEY_ACCENT_COLOR = "accent_color";
    private static final String KEY_DEVELOPER_FEATURES = "developer_features";
    private static final String KEY_CUSTOM_PATCH_URL = "custom_patch_url";
    private static final String KEY_DOWNLOAD_UNSTRIPPED_LIBUNITY = "download_unstripped_libunity";
    private static final String KEY_FUSION_ENABLED = "fusion_enabled";
    private static final String KEY_FUSION_WARNING_SHOWN = "fusion_warning_shown";
    private static final String KEY_SKIP_FUSION_WHEN_UNUSED = "skip_fusion_when_unused";
    private static final String KEY_AUTO_UPDATE = "auto_update";
    private static final String KEY_UPDATE_PRERELEASES = "update_prereleases";
    private static final String KEY_LAST_UPDATE_CHECK = "last_update_check";
    private static final String KEY_UPDATE_LATER = "update_later_version";

    private SBASettings() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean getUseUnstrippedLibUnity(Context context) {
        return prefs(context).getBoolean(KEY_DOWNLOAD_UNSTRIPPED_LIBUNITY, true);
    }

    public static void setUseUnstrippedLibUnity(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_DOWNLOAD_UNSTRIPPED_LIBUNITY, enabled).apply();
    }

    /**
     * Whether Fusion (.dll mods) may run at all. Until the user chooses, it follows the device check: on for
     * devices that can take it, off for low-memory ones.
     */
    public static boolean getFusionEnabled(Context context) {
        SharedPreferences p = prefs(context);
        if (p.contains(KEY_FUSION_ENABLED)) return p.getBoolean(KEY_FUSION_ENABLED, true);
        return dev.segually.sbamodloader.tools.DeviceSupport.fusionSupported(context);
    }

    public static void setFusionEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_FUSION_ENABLED, enabled).apply();
    }

    public static boolean getFusionWarningShown(Context context) {
        return prefs(context).getBoolean(KEY_FUSION_WARNING_SHOWN, false);
    }

    public static void setFusionWarningShown(Context context, boolean shown) {
        prefs(context).edit().putBoolean(KEY_FUSION_WARNING_SHOWN, shown).apply();
    }

    /** When on (the default), the .NET runtime and BepInEx are not started unless a Fusion mod is enabled. */
    public static boolean getSkipFusionWhenUnused(Context context) {
        return prefs(context).getBoolean(KEY_SKIP_FUSION_WHEN_UNUSED, true);
    }

    public static void setSkipFusionWhenUnused(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_SKIP_FUSION_WHEN_UNUSED, enabled).apply();
    }

    /** Whether the app looks for a newer version of itself on GitHub when it opens. On by default. */
    public static boolean getAutoUpdate(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_UPDATE, true);
    }

    public static void setAutoUpdate(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_AUTO_UPDATE, enabled).apply();
    }

    /** Whether updates may be pre-releases (early test versions). Off by default. */
    public static boolean getUpdatePrereleases(Context context) {
        return prefs(context).getBoolean(KEY_UPDATE_PRERELEASES, false);
    }

    public static void setUpdatePrereleases(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_UPDATE_PRERELEASES, enabled).apply();
    }

    public static long getLastUpdateCheck(Context context) {
        return prefs(context).getLong(KEY_LAST_UPDATE_CHECK, 0);
    }

    public static void setLastUpdateCheck(Context context, long time) {
        prefs(context).edit().putLong(KEY_LAST_UPDATE_CHECK, time).apply();
    }

    /** The version the user chose "Later" for, so the automatic prompt does not repeat for it. */
    public static String getUpdateLater(Context context) {
        return prefs(context).getString(KEY_UPDATE_LATER, "");
    }

    public static void setUpdateLater(Context context, String version) {
        prefs(context).edit().putString(KEY_UPDATE_LATER, version).apply();
    }

    /** Which layout to use: decided from the device, or always the phone or the desktop one. */
    public static final int LAYOUT_AUTO = 0;
    public static final int LAYOUT_PHONE = 1;
    public static final int LAYOUT_DESKTOP = 2;

    public static int getLayoutMode(Context context) {
        int mode = prefs(context).getInt("layout_mode", LAYOUT_AUTO);
        return mode == LAYOUT_PHONE || mode == LAYOUT_DESKTOP ? mode : LAYOUT_AUTO;
    }

    public static void setLayoutMode(Context context, int mode) {
        prefs(context).edit().putInt("layout_mode", mode).apply();
    }

    // ---- appearance -------------------------------------------------------------------------------

    /** Follow the system's light/dark setting. */
    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    public static int getThemeMode(Context context) {
        int mode = prefs(context).getInt(KEY_THEME_MODE, THEME_SYSTEM);
        return mode == THEME_LIGHT || mode == THEME_DARK ? mode : THEME_SYSTEM;
    }

    public static void setThemeMode(Context context, int mode) {
        prefs(context).edit().putInt(KEY_THEME_MODE, mode).apply();
    }

    /** Colours from the wallpaper (Android 12 and newer). On by default, like it was before there was a switch. */
    public static boolean getMaterialYou(Context context) {
        return prefs(context).getBoolean(KEY_MATERIAL_YOU, true);
    }

    public static void setMaterialYou(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_MATERIAL_YOU, enabled).apply();
    }

    /** The accent colour as ARGB, or 0 for the app's own green. Used when Material You is off. */
    public static int getAccentColor(Context context) {
        return prefs(context).getInt(KEY_ACCENT_COLOR, 0);
    }

    public static void setAccentColor(Context context, int argb) {
        prefs(context).edit().putInt(KEY_ACCENT_COLOR, argb).apply();
    }

    /** The mod repositories the player added, in order. The official repository is always there and is not in this list. */
    public static java.util.List<dev.segually.sbamodloader.browser.RepoSource> getExtraRepos(Context context) {
        java.util.List<dev.segually.sbamodloader.browser.RepoSource> out = new java.util.ArrayList<>();
        try {
            org.json.JSONArray a = new org.json.JSONArray(prefs(context).getString("extra_repos", "[]"));
            for (int i = 0; i < a.length(); i++) {
                org.json.JSONObject o = a.getJSONObject(i);
                out.add(new dev.segually.sbamodloader.browser.RepoSource(o.optString("name", "Repository"),
                        o.getString("url"), o.optBoolean("enabled", true)));
            }
        } catch (org.json.JSONException e) {
            // A damaged list is treated as empty; the official repository still works.
        }
        return out;
    }

    public static void setExtraRepos(Context context, java.util.List<dev.segually.sbamodloader.browser.RepoSource> repos) {
        org.json.JSONArray a = new org.json.JSONArray();
        try {
            for (dev.segually.sbamodloader.browser.RepoSource r : repos) {
                a.put(new org.json.JSONObject().put("name", r.name).put("url", r.url).put("enabled", r.enabled));
            }
        } catch (org.json.JSONException e) {
            throw new IllegalStateException(e);
        }
        prefs(context).edit().putString("extra_repos", a.toString()).apply();
    }

    /** Debug builds only: read the mod index from this URL instead of the official one ("" for the official one). */
    public static String getDebugIndexUrl(Context context) {
        return prefs(context).getString("debug_index_url", "");
    }

    public static void setDebugIndexUrl(Context context, String url) {
        prefs(context).edit().putString("debug_index_url", url).apply();
    }

    public static boolean getDeveloperFeatures(Context context) {
        return prefs(context).getBoolean(KEY_DEVELOPER_FEATURES, false);
    }

    public static void setDeveloperFeatures(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_DEVELOPER_FEATURES, enabled).apply();
    }

    public static String getCustomPatchUrl(Context context) {
        return prefs(context).getString(KEY_CUSTOM_PATCH_URL, "");
    }

    public static void setCustomPatchUrl(Context context, String url) {
        prefs(context).edit().putString(KEY_CUSTOM_PATCH_URL, url).apply();
    }

    /** Names of native mods and packs in load order. */
    public static java.util.List<String> getModOrder(Context context) {
        String joined = prefs(context).getString(KEY_MOD_ORDER, "");
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String n : joined.split("\n")) if (!n.isEmpty()) names.add(n);
        return names;
    }

    public static void setModOrder(Context context, java.util.List<String> names) {
        prefs(context).edit().putString(KEY_MOD_ORDER, String.join("\n", names)).apply();
    }
}
