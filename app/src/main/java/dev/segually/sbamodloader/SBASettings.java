package dev.segually.sbamodloader;

import android.content.Context;
import android.content.SharedPreferences;

public final class SBASettings {
    private static final String PREFS_NAME = "sba_settings";
    private static final String KEY_MOD_ORDER = "mod_order";
    private static final String KEY_DEVELOPER_FEATURES = "developer_features";
    private static final String KEY_CUSTOM_PATCH_URL = "custom_patch_url";
    private static final String KEY_DOWNLOAD_UNSTRIPPED_LIBUNITY = "download_unstripped_libunity";

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
