package dev.segually.sbamodloader.browser;

import android.content.Context;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.segually.sbamodloader.browser.BrowserModels.Entry;
import dev.segually.sbamodloader.browser.BrowserModels.Version;
import dev.segually.sbamodloader.tools.ModFiles;
import dev.segually.sbamodloader.tools.ModPacks;

/**
 * Downloads a mod from the repository and installs it. The hash and size in the repository are the source of truth:
 * a file is only installed if it matches, whichever of the primary URL and the mirrors served it.
 */
public final class ModInstaller {
    private static final String TAG = "ModInstaller";

    public enum State { NOT_INSTALLED, INSTALLED, UPDATE_AVAILABLE }

    public interface Progress {
        /** {@code total} is 0 when unknown. Called from the installing thread. */
        void onProgress(Entry mod, long done, long total);
    }

    private ModInstaller() {
    }

    /** The installed version of {@code e}, or null when it is not installed (or its files were removed by hand). */
    public static String installedVersion(Context context, Entry e) {
        InstalledMods.Record r = new InstalledMods(context).get(e.id);
        if (r == null || r.files.isEmpty()) return null;
        for (File f : r.files) {
            if (!f.exists() && !new File(f.getPath() + ModPacks.DISABLED).exists()) return null; // removed by hand
        }
        return r.version;
    }

    /** The installed version of every mod in {@code all} that is installed, by id. */
    public static Map<String, String> installedVersions(Context context, Collection<Entry> all) {
        Map<String, String> out = new HashMap<>();
        for (Entry e : all) {
            String v = installedVersion(context, e);
            if (v != null) out.put(e.id, v);
        }
        return out;
    }

    /** Where {@code e} stands against its newest release. A newer copy than the repository lists counts as installed. */
    public static State state(Context context, Entry e) {
        String installed = installedVersion(context, e);
        if (installed == null) return State.NOT_INSTALLED;
        return ModVersions.compare(installed, e.latest().version) < 0 ? State.UPDATE_AVAILABLE : State.INSTALLED;
    }

    /**
     * Carries out a plan: every download in order (dependencies first), each checked against its hash and size.
     * Blocking: call it off the main thread. A plan that {@linkplain InstallPlanner.Plan#blocked() is blocked} is refused.
     */
    public static void install(Context context, InstallPlanner.Plan plan, Progress progress) throws IOException {
        if (plan.blocked()) throw new IOException("This install cannot go ahead");
        for (InstallPlanner.Step step : plan.steps) installOne(context, step.entry, step.version, progress);
    }

    private static void installOne(Context context, Entry e, Version v, Progress progress) throws IOException {
        File download = download(context, e, v, progress);
        try {
            List<File> files = place(e, v, download);
            InstalledMods store = new InstalledMods(context);
            InstalledMods.Record old = store.get(e.id);
            if (old != null) {
                for (File f : old.files) {
                    if (!files.contains(f)) deleteWithDisabled(f);
                }
            }
            store.put(e.id, v.version, files);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            download.delete();
        }
    }

    public static void remove(Context context, Entry e) {
        InstalledMods store = new InstalledMods(context);
        InstalledMods.Record r = store.get(e.id);
        if (r != null) for (File f : r.files) deleteWithDisabled(f);
        store.remove(e.id);
    }

    private static void deleteWithDisabled(File f) {
        //noinspection ResultOfMethodCallIgnored
        f.delete();
        //noinspection ResultOfMethodCallIgnored
        new File(f.getPath() + ModPacks.DISABLED).delete();
    }

    // ---- placing the file ------------------------------------------------------------------------

    /** Puts the downloaded file where its kind belongs and returns every file that was created. */
    private static List<File> place(Entry e, Version v, File download) throws IOException {
        String name = v.assetName().replaceAll("[^A-Za-z0-9._+-]", "_");
        String lower = name.toLowerCase(Locale.ROOT);
        List<File> created = new ArrayList<>();
        switch (e.kind) {
            case FUSION:
                if (lower.endsWith(".dll")) {
                    created.add(new File(ModFiles.pluginsDir(), name));
                } else if (lower.endsWith(".zip")) {
                    try (InputStream in = new FileInputStream(download)) {
                        ModFiles.unzipInto(in, ModFiles.pluginsDir(), created);
                    }
                    return created;
                } else {
                    throw new IOException("Unexpected file type for a Fusion mod: " + name);
                }
                break;
            case NATIVE:
                if (!lower.endsWith(".so")) throw new IOException("Unexpected file type for a native mod: " + name);
                created.add(new File(ModPacks.nativeModsDir(), name));
                break;
            case PACK:
                if (!lower.endsWith(ModPacks.PACK_EXTENSION)) throw new IOException("Unexpected file type for a pack: " + name);
                created.add(new File(ModPacks.packsDir(), name));
                break;
            default:
                throw new IOException("Unknown mod kind");
        }
        try (InputStream in = new FileInputStream(download)) {
            // A mod the user switched off stays switched off when it is updated.
            File target = created.get(0);
            File actual = new File(target.getPath() + ModPacks.DISABLED).exists() ? new File(target.getPath() + ModPacks.DISABLED) : target;
            ModFiles.copy(in, actual);
        }
        return created;
    }

    // ---- downloading -----------------------------------------------------------------------------

    private static File download(Context context, Entry e, Version v, Progress progress) throws IOException {
        File dir = new File(context.getCacheDir(), "mod-downloads");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        File out = new File(dir, e.id + "-" + v.version + ".part");
        IOException last = null;
        for (String url : v.downloadUrls()) {
            try {
                fetch(url, e, v, out, progress);
                return out;
            } catch (IOException ex) {
                Log.w(TAG, "Download from " + url + " failed: " + ex.getMessage());
                last = ex;
            }
        }
        //noinspection ResultOfMethodCallIgnored
        out.delete();
        throw last != null ? last : new IOException("No download address");
    }

    private static void fetch(String url, Entry e, Version v, File out, Progress progress) throws IOException {
        if (!url.startsWith("https://")) throw new IOException("Refusing a download that is not over https");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(30_000);
        c.setRequestProperty("User-Agent", "SBAModLoader");
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IOException(ex);
        }
        long done = 0;
        try {
            if (c.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); FileOutputStream os = new FileOutputStream(out)) {
                byte[] buf = new byte[1 << 16];
                int n;
                long lastReport = 0;
                while ((n = in.read(buf)) != -1) {
                    done += n;
                    // The repository states the exact size, so a longer download is already wrong.
                    if (done > v.size) throw new IOException("The download is larger than the repository says");
                    md.update(buf, 0, n);
                    os.write(buf, 0, n);
                    long now = System.nanoTime();
                    if (now - lastReport > 100_000_000L) {
                        lastReport = now;
                        progress.onProgress(e, done, v.size);
                    }
                }
            }
        } finally {
            c.disconnect();
        }
        progress.onProgress(e, done, v.size);
        StringBuilder hex = new StringBuilder();
        for (byte b : md.digest()) hex.append(String.format(Locale.ROOT, "%02x", b));
        if (done != v.size || !hex.toString().equals(v.sha256)) {
            //noinspection ResultOfMethodCallIgnored
            out.delete();
            throw new IOException("The download failed its integrity check");
        }
    }
}
