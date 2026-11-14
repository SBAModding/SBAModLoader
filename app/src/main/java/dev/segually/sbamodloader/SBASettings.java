package dev.segually.sbamodloader;

import android.content.Context;
import android.content.SharedPreferences;

public final class SBASettings {
    private static final String PREFS_NAME = "sba_settings";
    private static final String KEY_MOD_ORDER = "mod_order";
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
