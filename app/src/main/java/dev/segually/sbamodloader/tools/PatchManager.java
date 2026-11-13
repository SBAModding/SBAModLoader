package dev.segually.sbamodloader.tools;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dev.segually.sbamodloader.SBASettings;
import dev.segually.sbamodloader.TargetGame;

/**
 * Mandatory patches are asset overlays (replacement dex files and native libraries) that the
 * game's protection requires. A patch is a zip file:
 *
 * <pre>
 * manifest.json                      {"name", "gameVersion", "gameHash", "skipHashVerification"}
 * dex/*.dex                          replacement classes, searched before the game's own
 * lib/arm64-v8a/*.so                 replacement native libraries
 * </pre>
 *
 * {@code gameVersion} must equal the installed game's version name, and {@code gameHash} must equal
 * the SHA-256 of the installed game's base APK. {@code skipHashVerification} turns the hash check
 * off, but only while developer features are enabled.
 *
 * Patches will be published as releases of a patch repository; until then they can only be loaded
 * from local storage when developer features are on.
 */
public final class PatchManager {
    /** Releases API endpoint of the patch repository. Empty until the repository exists. */
    public static final String RELEASES_URL = "";

    public static final String ABI = "arm64-v8a";

    public enum Status {
        /** No patch is installed and no patch source is configured, so none is required. */
        NO_SOURCE,
        /** A patch is required (or the installed one no longer matches the game). */
        MISSING,
        INSTALLED
    }

    public static final class Manifest {
        public String name = "";
        public String gameVersion = "";
        public String gameHash = "";
        public boolean skipHashVerification;
    }

    /** An installed patch that has been verified against the installed game. */
    public static final class ActivePatch {
        public final Manifest manifest;
        public final List<File> dexFiles;
        /** Directory with replacement native libraries, or null. */
        public final File libDir;

        ActivePatch(Manifest manifest, List<File> dexFiles, File libDir) {
            this.manifest = manifest;
            this.dexFiles = dexFiles;
            this.libDir = libDir;
        }
    }

    public static final class PatchException extends Exception {
        public PatchException(String message) {
            super(message);
        }

        public PatchException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private PatchManager() {
    }

    public static File patchDir(Context context) {
        return Utilities.getExternalSBAModLoaderDirectory(TargetGame.PACKAGE_NAME, "Patch");
    }

    private static PackageInfo gameInfo(Context context) throws PatchException {
        try {
            return context.getPackageManager().getPackageInfo(TargetGame.PACKAGE_NAME, 0);
        } catch (Exception e) {
            throw new PatchException("The game is not installed");
        }
    }

    /** SHA-256 (hex) of the installed game's base APK. Cached until the file changes. */
    public static String gameHash(Context context) throws PatchException {
        File apk = new File(gameInfo(context).applicationInfo.sourceDir);
        String key = apk.getAbsolutePath() + "|" + apk.length() + "|" + apk.lastModified();
        SharedPreferences prefs = context.getSharedPreferences("sba_patch_cache", Context.MODE_PRIVATE);
        if (key.equals(prefs.getString("key", null))) {
            String cached = prefs.getString("hash", null);
            if (cached != null) return cached;
        }
        String hash = sha256(apk);
        prefs.edit().putString("key", key).putString("hash", hash).apply();
        return hash;
    }

    private static String sha256(File file) throws PatchException {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format(Locale.ROOT, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new PatchException("Could not hash the installed game", e);
        }
    }

    private static Manifest parseManifest(String json) throws PatchException {
        try {
            JSONObject o = new JSONObject(json);
            Manifest m = new Manifest();
            m.name = o.optString("name", "");
            m.gameVersion = o.getString("gameVersion");
            m.gameHash = o.optString("gameHash", "").toLowerCase(Locale.ROOT);
            m.skipHashVerification = o.optBoolean("skipHashVerification", false);
            if (m.gameHash.isEmpty() && !m.skipHashVerification) {
                throw new PatchException("manifest.json has no gameHash");
            }
            return m;
        } catch (PatchException e) {
            throw e;
        } catch (Exception e) {
            throw new PatchException("manifest.json is invalid: " + e.getMessage(), e);
        }
    }

    private static void verify(Context context, Manifest m) throws PatchException {
        PackageInfo info = gameInfo(context);
        if (!m.gameVersion.equals(info.versionName)) {
            throw new PatchException("Patch is for game version " + m.gameVersion
                    + " but " + info.versionName + " is installed");
        }
        boolean skipHash = m.skipHashVerification && SBASettings.getDeveloperFeatures(context);
        if (!skipHash && !m.gameHash.equals(gameHash(context))) {
            throw new PatchException("Patch is for a different build of the game (hash mismatch)");
        }
    }

    /** The installed patch's manifest, or null when none is installed. */
    public static Manifest installedManifest(Context context) {
        File f = new File(patchDir(context), "manifest.json");
        if (!f.isFile()) return null;
        try (InputStream in = new FileInputStream(f)) {
            return parseManifest(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    /** The installed patch if it matches the installed game; null otherwise. May hash the game APK. */
    public static ActivePatch getActive(Context context) {
        Manifest m = installedManifest(context);
        if (m == null) return null;
        try {
            verify(context, m);
        } catch (PatchException e) {
            return null;
        }
        File root = patchDir(context);
        List<File> dex = new ArrayList<>();
        File[] dexFiles = new File(root, "dex").listFiles((d, n) -> n.endsWith(".dex"));
        if (dexFiles != null) {
            Collections.addAll(dex, dexFiles);
            dex.sort(java.util.Comparator.comparing(File::getName));
        }
        File lib = new File(root, "lib/" + ABI);
        return new ActivePatch(m, dex, lib.isDirectory() ? lib : null);
    }

    /** Blocking: may hash the game APK. */
    public static Status status(Context context) {
        if (getActive(context) != null) return Status.INSTALLED;
        if (installedManifest(context) != null) return Status.MISSING;
        return RELEASES_URL.isEmpty() ? Status.NO_SOURCE : Status.MISSING;
    }

    /** Installs a patch zip picked from local storage. Requires developer features. */
    public static Manifest installLocal(Context context, Uri uri) throws PatchException {
        if (!SBASettings.getDeveloperFeatures(context)) {
            throw new PatchException("Developer features are disabled");
        }
        File tmpZip = new File(context.getCacheDir(), "patch-import.zip");
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(tmpZip)) {
            if (in == null) throw new PatchException("Could not open the selected file");
            in.transferTo(out);
        } catch (IOException e) {
            throw new PatchException("Could not read the selected file", e);
        }
        File root = patchDir(context);
        File staging = new File(root.getParentFile(), "Patch.new");
        try (ZipFile zip = new ZipFile(tmpZip)) {
            ZipEntry me = zip.getEntry("manifest.json");
            if (me == null) throw new PatchException("The zip has no manifest.json");
            Manifest manifest;
            try (InputStream in = zip.getInputStream(me)) {
                manifest = parseManifest(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
            verify(context, manifest);

            Utilities.deleteRecursive(staging);
            if (!staging.mkdirs()) throw new PatchException("Could not create the patch folder");
            String stagingRoot = staging.getCanonicalPath() + File.separator;
            int files = 0;
            for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                ZipEntry entry = e.nextElement();
                String name = entry.getName();
                boolean wanted = name.equals("manifest.json")
                        || (name.startsWith("dex/") && name.endsWith(".dex"))
                        || (name.startsWith("lib/" + ABI + "/") && name.endsWith(".so"));
                if (entry.isDirectory() || !wanted) continue;
                File target = new File(staging, name);
                if (!target.getCanonicalPath().startsWith(stagingRoot)) continue; // zip-slip guard
                target.getParentFile().mkdirs();
                try (InputStream in = zip.getInputStream(entry); FileOutputStream out = new FileOutputStream(target)) {
                    in.transferTo(out);
                }
                // Android 14+ refuses to load writable dex files.
                if (name.endsWith(".dex")) target.setReadOnly();
                files++;
            }
            if (files <= 1) throw new PatchException("The patch contains no dex files or libraries");
            Utilities.deleteRecursive(root);
            if (!staging.renameTo(root)) throw new PatchException("Could not install the patch");
            return manifest;
        } catch (PatchException e) {
            Utilities.deleteRecursive(staging);
            throw e;
        } catch (Exception e) {
            Utilities.deleteRecursive(staging);
            throw new PatchException("Could not install the patch: " + e.getMessage(), e);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmpZip.delete();
        }
    }

    public static void remove(Context context) {
        Utilities.deleteRecursive(patchDir(context));
    }
}
