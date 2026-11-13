package dev.segually.sbamodloader.tools;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;

import org.json.JSONObject;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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
 * manifest.json                      {"id", "name", "patchVersion", "gameVersion", "gameHash",
 *                                      "skipHashVerification", "applicationClass"}
 * dex/*.dex                          replacement classes, searched before the game's own
 * lib/arm64-v8a/*.so                 replacement native libraries
 * overlay/**                         files laid over the game's assets (same as an .sbapack's overlay folder)
 * </pre>
 *
 * {@code gameVersion} must equal the installed game's version name, and {@code gameHash} must equal
 * the SHA-256 of the installed game's base APK. {@code skipHashVerification} turns the hash check
 * off, but only while developer features are enabled. {@code applicationClass} replaces the game's
 * manifest Application class (use "android.app.Application" when the patch removes a wrapper class).
 *
 * Patches are published in a patch repository (see {@link #DEFAULT_SOURCE_URL}). The loader reads the
 * repository's index.json, picks the entry matching the installed game's version and hash, downloads
 * it and checks its SHA-256. Developer features also allow a custom source URL (an index.json, or a
 * patch zip directly) and loading a patch zip from local storage.
 */
public final class PatchManager {
    private static final String TAG = "PatchManager";

    /** Index of the official patch repository. */
    public static final String DEFAULT_SOURCE_URL =
            "https://raw.githubusercontent.com/SBAModding/patches/main/index.json";

    private static final int MAX_INDEX_BYTES = 1 << 20;
    private static final long MAX_PATCH_BYTES = 200L * 1024 * 1024;

    public static final String ABI = "arm64-v8a";

    public enum SyncState {
        /** A patch matching the installed game is installed. */
        INSTALLED,
        /** The source has no patch for this build of the game. */
        NOT_AVAILABLE,
        /** The source could not be reached. */
        OFFLINE,
        /** A patch exists for this build but could not be installed. */
        FAILED
    }

    public static final class SyncResult {
        public final SyncState state;
        public final String message;

        SyncResult(SyncState state, String message) {
            this.state = state;
            this.message = message;
        }
    }

    public static final class Manifest {
        public String id = "";
        public int patchVersion = 1;
        public String name = "";
        public String gameVersion = "";
        public String gameHash = "";
        public boolean skipHashVerification;
        /**
         * Application class to use instead of the one in the installed game's manifest. Null keeps the
         * game's own; an empty string or "android.app.Application" means the game has none.
         */
        public String applicationClass;
    }

    /** An installed patch that has been verified against the installed game. */
    public static final class ActivePatch {
        public final Manifest manifest;
        public final List<File> dexFiles;
        /** Directory with replacement native libraries, or null. */
        public final File libDir;
        /** Directory of files to lay over the game's assets, or null. */
        public final File overlayDir;

        ActivePatch(Manifest manifest, List<File> dexFiles, File libDir, File overlayDir) {
            this.manifest = manifest;
            this.dexFiles = dexFiles;
            this.libDir = libDir;
            this.overlayDir = overlayDir;
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

    /**
     * Where the installed patch lives. This must be internal storage: Android 14+ refuses to load dex
     * files that are writable (permission bits cannot be changed on external storage), and external
     * storage is mounted noexec on real devices, which also blocks native libraries.
     */
    public static File patchDir(Context context) {
        File dir = new File(context.getFilesDir(), "patches/" + TargetGame.PACKAGE_NAME);
        removeLegacyPatchDir(context);
        return dir;
    }

    /** Earlier builds kept the patch in external storage. */
    private static void removeLegacyPatchDir(Context context) {
        File legacy = new File(Utilities.getExternalSBAModLoaderDirectory(TargetGame.PACKAGE_NAME, null), "Patch");
        if (legacy.exists()) Utilities.deleteRecursive(legacy);
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
            m.id = o.optString("id", "");
            m.patchVersion = o.optInt("patchVersion", 1);
            m.name = o.optString("name", "");
            m.gameVersion = o.getString("gameVersion");
            m.gameHash = o.optString("gameHash", "").toLowerCase(Locale.ROOT);
            m.skipHashVerification = o.optBoolean("skipHashVerification", false);
            if (o.has("applicationClass")) {
                m.applicationClass = o.isNull("applicationClass") ? "" : o.getString("applicationClass");
            }
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
        File overlay = new File(root, "overlay");
        return new ActivePatch(m, dex, lib.isDirectory() ? lib : null, overlay.isDirectory() ? overlay : null);
    }

    // ---- where the patch came from -------------------------------------------------------------

    private static final class Source {
        String url = "";
        String id = "";
        int patchVersion;
    }

    private static File sourceFile(Context context) {
        return new File(patchDir(context), "source.json");
    }

    /** Null for a patch loaded from local storage. */
    private static Source readSource(Context context) {
        File f = sourceFile(context);
        if (!f.isFile()) return null;
        try (InputStream in = new FileInputStream(f)) {
            JSONObject o = new JSONObject(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            Source r = new Source();
            r.url = o.optString("url", "");
            r.id = o.optString("id", "");
            r.patchVersion = o.optInt("patchVersion", 0);
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeSource(Context context, Source source) throws IOException {
        try (FileOutputStream out = new FileOutputStream(sourceFile(context))) {
            JSONObject o = new JSONObject();
            o.put("url", source.url);
            o.put("id", source.id);
            o.put("patchVersion", source.patchVersion);
            out.write(o.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (org.json.JSONException e) {
            throw new IOException(e);
        }
    }

    /** The configured source: the custom URL while developer features are on, else the official index. */
    public static String sourceUrl(Context context) {
        if (SBASettings.getDeveloperFeatures(context)) {
            String custom = SBASettings.getCustomPatchUrl(context).trim();
            if (!custom.isEmpty()) return custom;
        }
        return DEFAULT_SOURCE_URL;
    }

    // ---- remote sync ---------------------------------------------------------------------------

    private static final class Entry {
        String url;
        String sha256;
        long size;
        String id = "";
        int patchVersion;
    }

    /**
     * Makes sure a patch matching the installed game is installed, downloading it if needed.
     * Blocking: hashes the game APK and uses the network.
     */
    public static SyncResult sync(Context context) {
        ActivePatch active = getActive(context);
        Source recorded = readSource(context);
        // A patch loaded from local storage is never replaced by a downloaded one.
        if (active != null && recorded == null) return new SyncResult(SyncState.INSTALLED, "");

        String hash;
        String version;
        try {
            hash = gameHash(context);
            version = gameInfo(context).versionName;
        } catch (PatchException e) {
            return new SyncResult(SyncState.FAILED, e.getMessage());
        }

        String source = sourceUrl(context);
        Entry entry;
        try {
            entry = resolve(source, version, hash);
        } catch (IOException e) {
            Log.w(TAG, "Could not read patch source " + source, e);
            return active != null
                    ? new SyncResult(SyncState.INSTALLED, "")
                    : new SyncResult(SyncState.OFFLINE, e.getMessage());
        } catch (PatchException e) {
            return new SyncResult(SyncState.FAILED, e.getMessage());
        }
        if (entry == null) {
            return active != null
                    ? new SyncResult(SyncState.INSTALLED, "")
                    : new SyncResult(SyncState.NOT_AVAILABLE, "");
        }

        if (active != null && recorded != null) {
            boolean upToDate = entry.sha256 == null
                    ? entry.url.equals(recorded.url)
                    : entry.id.equals(recorded.id) && recorded.patchVersion >= entry.patchVersion;
            if (upToDate) return new SyncResult(SyncState.INSTALLED, "");
        }

        File tmpZip = new File(context.getCacheDir(), "patch-download.zip");
        try {
            download(entry, tmpZip);
            Source record = new Source();
            record.url = entry.url;
            Manifest installed = installZip(context, tmpZip, record, entry);
            Log.i(TAG, "Installed patch " + installed.id + " v" + installed.patchVersion + " from " + entry.url);
            return new SyncResult(SyncState.INSTALLED, "");
        } catch (PatchException | IOException e) {
            Log.e(TAG, "Patch download/install failed", e);
            return new SyncResult(SyncState.FAILED, e.getMessage());
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmpZip.delete();
        }
    }

    /** Finds the patch for this game build in the source, or null when there is none. */
    private static Entry resolve(String source, String version, String hash) throws IOException, PatchException {
        if (!source.startsWith("https://")) throw new PatchException("The patch source must be an https URL");
        if (source.toLowerCase(Locale.ROOT).split("[?#]")[0].endsWith(".zip")) {
            Entry direct = new Entry();
            direct.url = source;
            return direct;
        }
        JSONObject index;
        try {
            index = new JSONObject(new String(httpGet(source, MAX_INDEX_BYTES), java.nio.charset.StandardCharsets.UTF_8));
        } catch (org.json.JSONException e) {
            throw new PatchException("The patch index is not valid JSON");
        }
        org.json.JSONObject games = index.optJSONObject("games");
        org.json.JSONObject game = games == null ? null : games.optJSONObject(TargetGame.PACKAGE_NAME);
        org.json.JSONArray patches = game == null ? null : game.optJSONArray("patches");
        if (patches == null) return null;
        Entry best = null;
        for (int i = 0; i < patches.length(); i++) {
            JSONObject p = patches.optJSONObject(i);
            if (p == null) continue;
            if (!version.equals(p.optString("gameVersion"))) continue;
            if (!hash.equalsIgnoreCase(p.optString("gameHash"))) continue;
            int pv = p.optInt("patchVersion", 0);
            if (best != null && pv <= best.patchVersion) continue;
            Entry e = new Entry();
            e.id = p.optString("id", "");
            e.patchVersion = pv;
            e.sha256 = p.optString("sha256", "").toLowerCase(Locale.ROOT);
            e.size = p.optLong("size", -1);
            String url = p.optString("url", "");
            try {
                e.url = new java.net.URI(source).resolve(url).toString();
            } catch (java.net.URISyntaxException ex) {
                continue;
            }
            if (e.sha256.isEmpty() || !e.url.startsWith("https://")) continue;
            best = e;
        }
        return best;
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(30_000);
        c.setRequestProperty("User-Agent", "SBAModLoader");
        int code = c.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            c.disconnect();
            throw new IOException("HTTP " + code + " for " + url);
        }
        return c;
    }

    private static byte[] httpGet(String url, int maxBytes) throws IOException {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream()) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                if (out.size() > maxBytes) throw new IOException("Response too large: " + url);
            }
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    private static void download(Entry entry, File dest) throws IOException, PatchException {
        HttpURLConnection c = open(entry.url);
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        long total = 0;
        try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > MAX_PATCH_BYTES) throw new PatchException("The patch download is too large");
                md.update(buf, 0, n);
                out.write(buf, 0, n);
            }
        } finally {
            c.disconnect();
        }
        if (entry.sha256 != null && !entry.sha256.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format(Locale.ROOT, "%02x", b));
            if (!sb.toString().equals(entry.sha256)) throw new PatchException("The downloaded patch failed its integrity check");
            if (entry.size > 0 && total != entry.size) throw new PatchException("The downloaded patch has the wrong size");
        }
    }

    // ---- installing ----------------------------------------------------------------------------

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
        try {
            return installZip(context, tmpZip, null, null);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmpZip.delete();
        }
    }

    /**
     * Verifies and installs a patch zip, replacing any installed patch.
     *
     * @param source where it was downloaded from, or null for a locally loaded patch
     * @param entry  the index entry it was resolved from (its id and version must match the zip), or null
     */
    private static Manifest installZip(Context context, File zipFile, Source source, Entry entry) throws PatchException {
        File root = patchDir(context);
        File staging = new File(root.getParentFile(), TargetGame.PACKAGE_NAME + ".new");
        try (ZipFile zip = new ZipFile(zipFile)) {
            ZipEntry me = zip.getEntry("manifest.json");
            if (me == null) throw new PatchException("The zip has no manifest.json");
            Manifest manifest;
            try (InputStream in = zip.getInputStream(me)) {
                manifest = parseManifest(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
            verify(context, manifest);
            if (entry != null && !entry.id.isEmpty()
                    && (!entry.id.equals(manifest.id) || entry.patchVersion != manifest.patchVersion)) {
                throw new PatchException("The patch does not match its index entry");
            }

            Utilities.deleteRecursive(staging);
            if (!staging.mkdirs()) throw new PatchException("Could not create the patch folder");
            String stagingRoot = staging.getCanonicalPath() + File.separator;
            int files = 0;
            for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                ZipEntry ze = e.nextElement();
                String name = ze.getName();
                boolean wanted = name.equals("manifest.json")
                        || (name.startsWith("dex/") && name.endsWith(".dex"))
                        || (name.startsWith("lib/" + ABI + "/") && name.endsWith(".so"))
                        || name.startsWith("overlay/");
                if (ze.isDirectory() || !wanted) continue;
                File target = new File(staging, name);
                if (!target.getCanonicalPath().startsWith(stagingRoot)) continue; // zip-slip guard
                target.getParentFile().mkdirs();
                try (InputStream in = zip.getInputStream(ze); FileOutputStream out = new FileOutputStream(target)) {
                    in.transferTo(out);
                }
                // Android 14+ refuses to load writable dex files and native libraries.
                if (name.endsWith(".dex") || name.endsWith(".so")) target.setReadOnly();
                files++;
            }
            if (files <= 1) throw new PatchException("The patch contains no dex files, libraries or overlay files");
            Utilities.deleteRecursive(root);
            if (!staging.renameTo(root)) throw new PatchException("Could not install the patch");
            if (source != null) {
                source.id = manifest.id;
                source.patchVersion = manifest.patchVersion;
                writeSource(context, source);
            }
            return manifest;
        } catch (PatchException e) {
            Utilities.deleteRecursive(staging);
            throw e;
        } catch (Exception e) {
            Utilities.deleteRecursive(staging);
            throw new PatchException("Could not install the patch: " + e.getMessage(), e);
        }
    }

    public static void remove(Context context) {
        Utilities.deleteRecursive(patchDir(context));
    }
}
