package dev.segually.sbamodloader.tools;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.app.PendingIntent;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.segually.sbamodloader.BuildConfig;

/**
 * Updates the loader itself from the GitHub releases of {@link #REPO}. Only the release APK
 * ({@code SBAModLoader-<version>.apk}, never the debug one) of a release in that repository is considered.
 * Android always asks the user to confirm an install, and refuses an APK that is signed with a different
 * key than the installed app, so a wrong file can not replace the loader even if it got this far.
 */
public final class AppUpdater {
    public static final String REPO = "SBAModding/SBAModLoader";
    public static final String REPO_URL = "https://github.com/" + REPO;
    public static final String DISCORD_URL = "https://discord.gg/jEdfR5kgAc";
    public static final String INSTALL_ACTION = "dev.segually.sbamodloader.UPDATE_INSTALL_RESULT";

    private static final String API_URL = "https://api.github.com/repos/" + REPO + "/releases?per_page=20";
    private static final String DOWNLOAD_PREFIX = REPO_URL + "/releases/download/";
    private static final long MAX_APK_BYTES = 300L * 1024 * 1024;
    /**
     * A version is numbers, an optional letter, and an optional "-marker" naming a track of builds that is kept apart
     * from the main releases (0.0.5a-compose, 0.0.6-beta.2...): digits, then letters, then the marker.
     */
    private static final Pattern VERSION = Pattern.compile("^v?(\\d+(?:\\.\\d+)*)([a-z]*)(?:-([a-z0-9][a-z0-9.\\-]*))?$");

    private AppUpdater() {
    }

    public static final class Release {
        public final String version;
        public final String apkUrl;
        public final long size;
        /** Lower-case hex SHA-256 reported by GitHub for the asset, or null. */
        public final String sha256;

        Release(String version, String apkUrl, long size, String sha256) {
            this.version = version;
            this.apkUrl = apkUrl;
            this.size = size;
            this.sha256 = sha256;
        }
    }

    public interface Progress {
        void onProgress(long done, long total);
    }

    /** Looks for a release newer than the running version. Returns null when there is none. */
    public static Release findNewer(boolean includePrereleases) throws IOException {
        String json = httpGetString(API_URL);
        Release best = null;
        try {
            JSONArray releases = new JSONArray(json);
            for (int i = 0; i < releases.length(); i++) {
                JSONObject r = releases.getJSONObject(i);
                if (r.optBoolean("draft") || (r.optBoolean("prerelease") && !includePrereleases)) continue;
                String version = r.optString("tag_name", "").replaceFirst("^v", "");
                if (!isOffered(version, BuildConfig.VERSION_NAME)) continue;
                if (best != null && compareVersions(version, best.version) <= 0) continue;
                String wanted = "SBAModLoader-" + version + ".apk";
                JSONArray assets = r.optJSONArray("assets");
                if (assets == null) continue;
                for (int a = 0; a < assets.length(); a++) {
                    JSONObject asset = assets.getJSONObject(a);
                    String url = asset.optString("browser_download_url", "");
                    if (!wanted.equals(asset.optString("name")) || !url.startsWith(DOWNLOAD_PREFIX)) continue;
                    String digest = asset.optString("digest", "");
                    String sha = digest.startsWith("sha256:") ? digest.substring(7).toLowerCase(Locale.ROOT) : null;
                    best = new Release(version, url, asset.optLong("size", -1), sha);
                    break;
                }
            }
        } catch (org.json.JSONException e) {
            throw new IOException("Unreadable release list", e);
        }
        return best;
    }

    /** The marker of a version ("compose" in 0.0.5a-compose), or "" for a main release or something unreadable. */
    static String marker(String version) {
        Matcher m = VERSION.matcher(version);
        return m.matches() && m.group(3) != null ? m.group(3) : "";
    }

    /**
     * Whether a release is an update for the running build. A main build (no marker) only sees main releases, so a
     * track of test builds never reaches it. A build with a marker sees newer releases of its own marker and newer main
     * releases, so when the main release it was leading up to ships, it gets that; releases with another marker are not
     * its business.
     */
    static boolean isOffered(String candidate, String running) {
        if (!VERSION.matcher(candidate).matches()) return false;
        String mine = marker(running);
        String theirs = marker(candidate);
        if (!theirs.isEmpty() && !theirs.equals(mine)) return false;
        return compareVersions(candidate, running) > 0;
    }

    /**
     * Orders versions like 0.0.1a &lt; 0.0.1b &lt; 0.0.1 &lt; 0.0.2: the numbers first, then a version without a
     * letter suffix is newer than the same numbers with one. A "-marker" is not part of the order. Something unreadable
     * counts as older.
     */
    static int compareVersions(String a, String b) {
        Matcher ma = VERSION.matcher(a), mb = VERSION.matcher(b);
        if (!ma.matches()) return -1;
        if (!mb.matches()) return 1;
        String[] pa = ma.group(1).split("\\."), pb = mb.group(1).split("\\.");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            long x = i < pa.length ? Long.parseLong(pa[i]) : 0, y = i < pb.length ? Long.parseLong(pb[i]) : 0;
            if (x != y) return Long.compare(x, y);
        }
        String sa = ma.group(2), sb = mb.group(2);
        if (sa.isEmpty() != sb.isEmpty()) return sa.isEmpty() ? 1 : -1;
        return sa.compareTo(sb);
    }

    /** Downloads and checks the APK. Returns the file; throws IOException when anything about it is wrong. */
    public static File download(Context context, Release release, Progress progress) throws IOException {
        File dir = new File(context.getCacheDir(), "update");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        File[] old = dir.listFiles();
        if (old != null) for (File f : old) f.delete();
        File tmp = new File(dir, "download.tmp");
        File apk = new File(dir, "SBAModLoader-" + release.version + ".apk");

        HttpURLConnection c = open(release.apkUrl);
        long total = c.getContentLengthLong();
        if (total > MAX_APK_BYTES) throw new IOException("Update is too large");
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        long done = 0;
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                md.update(buf, 0, n);
                done += n;
                if (done > MAX_APK_BYTES) throw new IOException("Update is too large");
                if (progress != null) progress.onProgress(done, total);
            }
        } finally {
            c.disconnect();
        }
        if (release.size > 0 && done != release.size) throw new IOException("Download is incomplete");
        if (release.sha256 != null) {
            StringBuilder hex = new StringBuilder();
            for (byte b : md.digest()) hex.append(String.format("%02x", b));
            if (!hex.toString().equals(release.sha256)) throw new IOException("Download does not match its checksum");
        }
        if (!tmp.renameTo(apk)) throw new IOException("Cannot save the update");

        PackageInfo info = context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
        if (info == null || !context.getPackageName().equals(info.packageName)
                || androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info) <= BuildConfig.VERSION_CODE) {
            apk.delete();
            throw new IOException("Downloaded file is not a newer version of this app");
        }
        return apk;
    }

    /** Hands the APK to Android's installer. The result arrives at {@link UpdateInstallReceiver}. */
    public static void install(Context context, File apk) throws IOException {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        if (Build.VERSION.SDK_INT >= 31) {
            // Once the user has allowed this app to install, later updates can go through without another prompt.
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        }
        int id = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(id)) {
            try (InputStream in = new FileInputStream(apk); OutputStream out = session.openWrite("update.apk", 0, apk.length())) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                session.fsync(out);
            }
            Intent result = new Intent(context, UpdateInstallReceiver.class).setAction(INSTALL_ACTION);
            PendingIntent pending = PendingIntent.getBroadcast(context, id, result,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            session.commit(pending.getIntentSender());
        } catch (IOException | RuntimeException e) {
            installer.abandonSession(id);
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "SBAModLoader");
        c.setRequestProperty("Accept", "application/vnd.github+json");
        if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
        return c;
    }

    private static String httpGetString(String url) throws IOException {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream()) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > (4 << 20)) throw new IOException("Response too large");
            }
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }
}
