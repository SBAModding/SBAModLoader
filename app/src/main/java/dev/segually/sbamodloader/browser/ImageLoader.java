package dev.segually.sbamodloader.browser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads the icons and screenshots of mods. They live in the mod repository under {@code images/<id>/}, and nothing
 * else is ever fetched: the index only names repository-relative paths, and those are checked here again.
 *
 * <p>Pictures are cached in memory and on disk, so the Browse tab keeps its pictures offline. A cached file is
 * refreshed after a day when there is a connection, and kept when there is not. No library is used, to keep the app
 * small and every request visible in one place.
 */
public final class ImageLoader {
    private static final String TAG = "ImageLoader";
    /** Where repository-relative image paths are resolved: next to the index. */
    private static final String BASE =
            RemoteModRepository.INDEX_URL.substring(0, RemoteModRepository.INDEX_URL.lastIndexOf('/') + 1);
    private static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final long REFRESH_AFTER_MS = 24L * 60 * 60 * 1000;

    public interface Callback {
        /** Called on the main thread. */
        void onLoaded(Bitmap bitmap);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(3);
    private static final LruCache<String, Bitmap> MEMORY = new LruCache<String, Bitmap>(
            (int) Math.max(4L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 16)) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private ImageLoader() {
    }

    /** The URL a repository-relative path is served from, or null when it is not a path this loader will fetch. */
    static String urlFor(String path) {
        return urlFor(BASE, path);
    }

    /** The same for a repository other than the official one: {@code base} is the folder of its index. */
    static String urlFor(String base, String path) {
        if (path == null || !path.startsWith("images/") || path.contains("..") || path.contains("//")) return null;
        if (base == null || !(base.startsWith("https://") || base.startsWith("http://127.0.0.1")) || !base.endsWith("/")) return null;
        return base + path;
    }

    /**
     * Loads {@code path} scaled to about {@code maxPixels} on its longest side and delivers it on the main thread.
     * Nothing is delivered when the image is not available (the caller keeps showing its placeholder).
     */
    public static void load(Context context, String path, int maxPixels, Callback callback) {
        load(context, BASE, path, maxPixels, callback);
    }

    /** Loads a picture of a mod from {@code base} (the folder of the repository it is in). */
    public static void load(Context context, String base, String path, int maxPixels, Callback callback) {
        final String url = urlFor(base == null ? BASE : base, path);
        if (url == null) return;
        final String key = url + "@" + maxPixels;
        Bitmap hit = MEMORY.get(key);
        if (hit != null) {
            callback.onLoaded(hit);
            return;
        }
        final File dir = new File(context.getApplicationContext().getCacheDir(), "browse-images");
        WORKERS.execute(() -> {
            Bitmap bitmap = null;
            try {
                bitmap = decode(fileFor(dir, url), url, maxPixels);
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, "Could not load " + path + ": " + e.getMessage());
            }
            if (bitmap == null) return;
            MEMORY.put(key, bitmap);
            final Bitmap result = bitmap;
            MAIN.post(() -> callback.onLoaded(result));
        });
    }

    private static File fileFor(File dir, String url) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder();
            for (byte b : digest) name.append(String.format(Locale.ROOT, "%02x", b));
            return new File(dir, name.toString());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static Bitmap decode(File cached, String url, int maxPixels) throws IOException {
        boolean stale = !cached.isFile() || System.currentTimeMillis() - cached.lastModified() > REFRESH_AFTER_MS;
        if (stale) {
            try {
                download(url, cached);
            } catch (IOException e) {
                // Offline: an old copy is better than none.
                if (!cached.isFile()) throw e;
            }
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = new FileInputStream(cached)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            //noinspection ResultOfMethodCallIgnored
            cached.delete();
            throw new IOException("Not an image");
        }
        int sample = 1;
        int longest = Math.max(bounds.outWidth, bounds.outHeight);
        while (maxPixels > 0 && longest / (sample * 2) >= maxPixels) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        try (InputStream in = new FileInputStream(cached)) {
            return BitmapFactory.decodeStream(in, null, opts);
        }
    }

    private static void download(String url, File target) throws IOException {
        if (!url.startsWith("https://")) throw new IOException("Refusing an image that is not over https");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(20_000);
        c.setRequestProperty("User-Agent", "SBAModLoader");
        try {
            if (c.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + c.getResponseCode());
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) {
                byte[] buf = new byte[1 << 14];
                int n;
                while ((n = in.read(buf)) != -1) {
                    buffer.write(buf, 0, n);
                    if (buffer.size() > MAX_BYTES) throw new IOException("The image is larger than " + MAX_BYTES + " bytes");
                }
            }
            File part = new File(target.getPath() + ".part");
            try (FileOutputStream out = new FileOutputStream(part)) {
                buffer.writeTo(out);
            }
            if (!part.renameTo(target)) {
                //noinspection ResultOfMethodCallIgnored
                part.delete();
                throw new IOException("Cannot store the image");
            }
        } finally {
            c.disconnect();
        }
    }
}
