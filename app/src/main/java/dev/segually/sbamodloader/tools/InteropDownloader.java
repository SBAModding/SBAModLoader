package dev.segually.sbamodloader.tools;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Gets the interop assemblies BepInEx needs from the AssemblyRepository instead of letting BepInEx build them.
 *
 * Building them takes a minute or two and several gigabytes of memory, more than many phones have, and the result
 * only depends on the game build. The repository has one release per game version (the tag is the version name)
 * with a {@code manifest.json} and an {@code interop.zip}. The set is used only when:
 * BepInEx accepts an existing set only when the hash in {@code assembly-hash.txt} equals the one it computes: the
 * patched libil2cpp, every dll in {@code unity-libs} (in directory order, which differs between filesystems) and the
 * versions of its generator and Cpp2IL. On a device that has not built a set yet there is no {@code unity-libs}, so
 * the published hash covers only the first and last parts, and is the same on every device. The set is therefore
 * installed with {@code unity-libs} removed; BepInEx fetches those libraries again only if it has to regenerate.
 * <ul>
 *   <li>the manifest is for this game version and Unity version,</li>
 *   <li>its {@code metadataSha256} matches this device's copy of the game's global-metadata.dat, and</li>
 *   <li>the zip matches the size and SHA-256 in the manifest.</li>
 * </ul>
 * Anything else (no release, no network, a mismatch) leaves the folder empty, and BepInEx generates the assemblies
 * itself, exactly as before.
 *
 * A downloaded set is watched: if it fails three launches in a row (the process crashed or was killed before it had
 * run for a while) it is deleted and the version is switched to generating on the device. See {@link #prepare}.
 */
public final class InteropDownloader {
    private static final String TAG = "InteropDownloader";
    public static final String BASE_URL = "https://github.com/SBAModding/AssemblyRepository/releases/download/";
    /** The native side writes this file in the code cache once the process has run this long after BepInEx started. */
    public static final String HEALTHY_MARKER = "interop_ok";
    public static final int MAX_FAILURES = 3;

    private static final long MAX_MANIFEST_BYTES = 64 * 1024;
    private static final long MAX_ZIP_BYTES = 400L * 1024 * 1024;
    private static final String PREFS = "sba_interop_guard";

    private InteropDownloader() {
    }

    public interface Progress {
        void onProgress(long done, long total);
    }

    /**
     * Call before BepInEx starts. Settles how the last launch with a downloaded set went, applies the three strikes
     * rule, and downloads a set when there is none and the repository has one. Never throws.
     *
     * @param bepInExDir   the BepInEx folder (its {@code interop} child is what BepInEx reads and writes)
     * @param codeCacheDir the game's folder in the loader's internal runtime storage
     * @param metadataFile the device's copy of global-metadata.dat
     */
    public static void prepare(Context context, File bepInExDir, File codeCacheDir, File metadataFile,
                               String gameVersion, String unityVersion, Progress progress) {
        try {
            prepareChecked(context, bepInExDir, codeCacheDir, metadataFile, gameVersion, unityVersion, progress);
        } catch (Throwable t) {
            Log.e(TAG, "Preparing the interop assemblies failed; BepInEx will generate them", t);
            note(context, "failed: " + t);
        }
    }

    private static void prepareChecked(Context context, File bepInExDir, File codeCacheDir, File metadataFile,
                                       String gameVersion, String unityVersion, Progress progress) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        File interop = new File(bepInExDir, "interop");
        File hashFile = new File(interop, "assembly-hash.txt");
        File healthy = new File(codeCacheDir, HEALTHY_MARKER);

        String source = prefs.getString("downloadedHash", "");
        int failures = prefs.getInt("failures", 0);

        // 1. How did the last launch with a downloaded set go?
        if (prefs.getBoolean("pending", false)) {
            long since = prefs.getLong("pendingSince", 0);
            if (healthy.isFile()) {
                failures = 0;
                note(context, "the last launch ran fine");
            } else if (exitedBadlySince(context, since)) {
                failures++;
                note(context, "the last launch with the downloaded assemblies crashed (" + failures + " in a row)");
            } else {
                note(context, "the last launch ended early without a crash; not counted");
            }
        }
        //noinspection ResultOfMethodCallIgnored
        healthy.delete();

        // 2. A set BepInEx has since replaced with one it generated is no longer ours to blame.
        boolean ours = !source.isEmpty() && hashFile.isFile() && source.equals(readText(hashFile));

        // 3. Three strikes: throw the downloaded set away and generate on the device for this game version.
        if (ours && failures >= MAX_FAILURES) {
            note(context, "the downloaded assemblies failed " + failures + " launches in a row; generating on the device");
            Utilities.deleteRecursive(interop);
            prefs.edit().putString("blockedVersion", gameVersion).putString("downloadedHash", "")
                    .putInt("failures", 0).putBoolean("pending", false).commit();
            return;
        }

        if (hashFile.isFile()) {
            // Assemblies are already there (downloaded earlier, or generated by BepInEx). Keep watching a downloaded set.
            SharedPreferences.Editor e = prefs.edit().putInt("failures", ours ? failures : 0);
            if (ours) e.putBoolean("pending", true).putLong("pendingSince", System.currentTimeMillis());
            else e.putBoolean("pending", false).putString("downloadedHash", "");
            e.commit();
            return;
        }

        if (gameVersion.equals(prefs.getString("blockedVersion", ""))) {
            prefs.edit().putBoolean("pending", false).commit();
            note(context, "downloaded assemblies are switched off for " + gameVersion + "; generating on the device");
            return;
        }

        // 4. Nothing there yet: try the repository.
        String assemblyHash = download(context, interop, codeCacheDir, metadataFile, gameVersion, unityVersion, progress);
        if (assemblyHash == null) {
            prefs.edit().putBoolean("pending", false).commit();
            return;
        }
        prefs.edit().putString("downloadedHash", assemblyHash).putInt("failures", 0).putBoolean("pending", true)
                .putLong("pendingSince", System.currentTimeMillis()).commit();
        note(context, "installed the downloaded assemblies for " + gameVersion);
    }

    /** Downloads and installs the set into {@code interop}. Returns its assembly hash, or null when none was installed. */
    private static String download(Context context, File interop, File codeCacheDir, File metadataFile,
                                   String gameVersion, String unityVersion, Progress progress) {
        File zip = new File(codeCacheDir, "interop.zip.part");
        File staging = new File(interop.getParentFile(), "interop.tmp");
        File libs = new File(interop.getParentFile(), "unity-libs");
        try {
            String releaseUrl = BASE_URL + gameVersion.replaceAll("[^A-Za-z0-9._+-]", "_") + "/";
            byte[] manifestBytes = fetchSmall(releaseUrl + "manifest.json");
            if (manifestBytes == null) {
                note(context, "no assemblies published for " + gameVersion + "; generating on the device");
                return null;
            }
            JSONObject m = new JSONObject(new String(manifestBytes, StandardCharsets.UTF_8));
            JSONObject file = m.getJSONObject("interop");
            String name = file.getString("file");
            long size = file.getLong("size");
            String sha = file.getString("sha256").toLowerCase(Locale.ROOT);
            String assemblyHash = m.getString("assemblyHash").trim();
            if (m.getInt("format") != 1 || !gameVersion.equals(m.getString("gameVersion"))
                    || !unityVersion.equals(m.optString("unityVersion", ""))) {
                note(context, "the published assemblies are not for this game and Unity version; generating on the device");
                return null;
            }
            if (!name.matches("[A-Za-z0-9._-]+") || size <= 0 || size > MAX_ZIP_BYTES || assemblyHash.isEmpty()) {
                note(context, "the manifest is not valid; generating on the device");
                return null;
            }
            if (!metadataFile.isFile() || !m.getString("metadataSha256").equalsIgnoreCase(sha256(metadataFile))) {
                note(context, "this device's game data differs from what the assemblies were made from; generating on the device");
                return null;
            }

            note(context, "downloading " + name + " (" + (size >> 20) + " MB)");
            fetchFile(releaseUrl + name, zip, size, sha, progress);

            Utilities.deleteRecursive(staging);
            if (!staging.mkdirs()) throw new IOException("Cannot create " + staging);
            int count = unzipFlat(zip, staging, n -> n.endsWith(".dll") || n.equals("assembly-hash.txt"));
            File stagedHash = new File(staging, "assembly-hash.txt");
            if (count == 0 || !stagedHash.isFile() || !assemblyHash.equals(readText(stagedHash))) {
                throw new IOException("The downloaded set is incomplete or does not match its manifest");
            }
            Utilities.deleteRecursive(libs); // see the class comment: its dlls would change the hash BepInEx computes
            Utilities.deleteRecursive(interop);
            if (!staging.renameTo(interop)) throw new IOException("Cannot move the assemblies into place");
            return assemblyHash;
        } catch (Exception e) {
            Log.w(TAG, "Could not get the assemblies from the repository", e);
            note(context, "could not use the published assemblies (" + e + "); generating on the device");
            Utilities.deleteRecursive(staging);
            return null;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            zip.delete();
        }
    }

    // ---- crash detection ----------------------------------------------------------------------------

    /** True when the process died from a crash, an ANR or a kill since {@code sinceMillis}. */
    private static boolean exitedBadlySince(Context context, long sinceMillis) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true; // no exit records: assume the worst
        ActivityManager am = context.getSystemService(ActivityManager.class);
        if (am == null) return true;
        List<ApplicationExitInfo> exits = am.getHistoricalProcessExitReasons(context.getPackageName(), 0, 8);
        for (ApplicationExitInfo info : exits) {
            if (info.getTimestamp() < sinceMillis) continue;
            switch (info.getReason()) {
                case ApplicationExitInfo.REASON_CRASH:
                case ApplicationExitInfo.REASON_CRASH_NATIVE:
                case ApplicationExitInfo.REASON_ANR:
                case ApplicationExitInfo.REASON_SIGNALED:
                case ApplicationExitInfo.REASON_LOW_MEMORY:
                case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE:
                case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE:
                    return true;
                default:
                    break;
            }
        }
        return false;
    }

    // ---- small helpers ------------------------------------------------------------------------------

    private static byte[] fetchSmall(String url) throws IOException {
        HttpURLConnection c = open(url);
        try {
            int code = c.getResponseCode();
            if (code == HttpURLConnection.HTTP_NOT_FOUND) return null;
            if (code != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_MANIFEST_BYTES) throw new IOException("The manifest is too large");
                }
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    private static void fetchFile(String url, File out, long size, String sha256, Progress progress) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        HttpURLConnection c = open(url);
        long done = 0;
        try {
            if (c.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); FileOutputStream os = new FileOutputStream(out)) {
                byte[] buf = new byte[1 << 16];
                int n;
                long last = 0;
                while ((n = in.read(buf)) != -1) {
                    done += n;
                    if (done > size) throw new IOException("The download is larger than the manifest says");
                    md.update(buf, 0, n);
                    os.write(buf, 0, n);
                    long now = System.nanoTime();
                    if (progress != null && now - last > 100_000_000L) {
                        last = now;
                        progress.onProgress(done, size);
                    }
                }
            }
        } finally {
            c.disconnect();
        }
        if (progress != null) progress.onProgress(done, size);
        if (done != size || !hex(md.digest()).equals(sha256)) throw new IOException("The download failed its integrity check");
    }

    private static HttpURLConnection open(String url) throws IOException {
        if (!url.startsWith("https://")) throw new IOException("Refusing a download that is not over https");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(30_000);
        c.setRequestProperty("User-Agent", "SBAModLoader");
        return c;
    }

    /** Unpacks a zip of plain files into {@code dir}: no folders, no paths, and only names {@code allowed} accepts. */
    private static int unzipFlat(File zip, File dir, java.util.function.Predicate<String> allowed) throws IOException {
        int count = 0;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String name = e.getName();
                if (e.isDirectory() || !name.matches("[A-Za-z0-9._+-]+") || !allowed.test(name)) {
                    throw new IOException("Unexpected entry in the download: " + name);
                }
                try (FileOutputStream os = new FileOutputStream(new File(dir, name))) {
                    byte[] buf = new byte[1 << 16];
                    int n;
                    while ((n = zis.read(buf)) != -1) os.write(buf, 0, n);
                }
                count++;
            }
        }
        return count;
    }

    private static String readText(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
    }

    private static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
        }
        return hex(md.digest());
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format(Locale.ROOT, "%02x", b));
        return sb.toString();
    }

    /** One line in Logs/interop.log (kept short), for working out afterwards what happened on a device. */
    private static void note(Context context, String line) {
        Log.i(TAG, line);
        try {
            File log = new File(Utilities.getExternalSBAModLoaderDirectory(null, "Logs"), "interop.log");
            if (log.length() > 32 * 1024) //noinspection ResultOfMethodCallIgnored
                log.delete();
            Files.write(log.toPath(), (new java.util.Date() + "  " + line + "\n").getBytes(StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }
}
