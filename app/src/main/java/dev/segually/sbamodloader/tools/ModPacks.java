package dev.segually.sbamodloader.tools;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dev.segually.sbamodloader.SBASettings;
import dev.segually.sbamodloader.TargetGame;

/**
 * Native mods and asset packs.
 *
 * <ul>
 *   <li>A native mod is a {@code .so} file in the NativeMods folder.</li>
 *   <li>An {@code .sbapack} (a renamed zip) lives in the Packs folder. It may contain native mods
 *       ({@code .so} files at its root) and an {@code overlay/} folder whose files are laid over the game's
 *       assets: {@code overlay/assets/aa/Android/x.bundle} adds or replaces {@code assets/aa/Android/x.bundle}.
 *       Both parts are optional.</li>
 * </ul>
 *
 * Packs, native mods and the patch's own overlay all go through the same asset overlay: the loader builds one
 * APK that has the game's files plus the overlay files and presents it as the game's APK, so the game, Unity
 * and native mods see an ordinary game with every asset in place. Load order decides precedence: a later mod
 * replaces what an earlier one added, and native mods load in the same order.
 */
public final class ModPacks {
    private static final String TAG = "ModPacks";
    public static final String PACK_EXTENSION = ".sbapack";
    public static final String DISABLED = ".disabled";

    private ModPacks() {
    }

    public static File nativeModsDir() {
        return Utilities.getExternalSBAModLoaderDirectory(TargetGame.PACKAGE_NAME, "NativeMods");
    }

    public static File packsDir() {
        return Utilities.getExternalSBAModLoaderDirectory(TargetGame.PACKAGE_NAME, "Packs");
    }

    /** The mod's name without ".disabled", used as its identity in the saved load order. */
    public static String baseName(File f) {
        String n = f.getName();
        return n.endsWith(DISABLED) ? n.substring(0, n.length() - DISABLED.length()) : n;
    }

    public static boolean isOrderable(File f) {
        String n = baseName(f);
        return n.endsWith(".so") || n.endsWith(PACK_EXTENSION);
    }

    /** Every native mod and pack, enabled or not, in load order. */
    public static List<File> orderedMods(Context context) {
        List<File> all = new ArrayList<>();
        addAll(all, nativeModsDir());
        addAll(all, packsDir());
        return sort(context, all);
    }

    private static void addAll(List<File> into, File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) if (f.isFile() && isOrderable(f)) into.add(f);
    }

    /** Sorts by the saved order; mods that are not in it yet follow, alphabetically. */
    public static List<File> sort(Context context, List<File> mods) {
        List<String> saved = SBASettings.getModOrder(context);
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < saved.size(); i++) index.put(saved.get(i), i);
        List<File> sorted = new ArrayList<>(mods);
        sorted.sort((a, b) -> {
            int ia = index.getOrDefault(baseName(a), Integer.MAX_VALUE);
            int ib = index.getOrDefault(baseName(b), Integer.MAX_VALUE);
            if (ia != ib) return Integer.compare(ia, ib);
            return baseName(a).toLowerCase(Locale.ROOT).compareTo(baseName(b).toLowerCase(Locale.ROOT));
        });
        return sorted;
    }

    public static void saveOrder(Context context, List<File> inOrder) {
        List<String> names = new ArrayList<>();
        for (File f : inOrder) if (isOrderable(f)) names.add(baseName(f));
        SBASettings.setModOrder(context, names);
    }

    /** What a pack contains, for display. */
    public static String describe(File pack) {
        boolean natives = false;
        boolean assets = false;
        try (ZipFile zip = new ZipFile(pack)) {
            for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                ZipEntry entry = e.nextElement();
                String name = entry.getName();
                if (entry.isDirectory()) continue;
                if (isRootNative(name)) natives = true;
                else if (name.startsWith("overlay/")) assets = true;
            }
        } catch (IOException e) {
            return "";
        }
        if (natives && assets) return "both";
        return natives ? "native" : assets ? "assets" : "empty";
    }

    private static boolean isRootNative(String entryName) {
        return !entryName.contains("/") && entryName.endsWith(".so");
    }

    // ---- launch ------------------------------------------------------------------------------------

    /** The result of getting the mods ready for a launch. */
    public static final class Prepared {
        /** Absolute paths of native mods to load, in load order. */
        public final String[] natives;
        /** The APK to present as the game's, or null when there are no overlay files. */
        public final File overlayApk;

        Prepared(String[] natives, File overlayApk) {
            this.natives = natives;
            this.overlayApk = overlayApk;
        }
    }

    /**
     * Copies the enabled native mods next to the runtime files (external storage is mounted noexec) and builds
     * the overlay APK from the patch's overlay folder and every enabled pack.
     *
     * @param runtimeScoped the game's folder inside the loader's internal runtime storage
     * @param baseApk       the game's own APK
     */
    public static Prepared prepare(Context context, File runtimeScoped, File baseApk,
                                   PatchManager.ActivePatch patch) throws IOException {
        File nativeRoot = new File(runtimeScoped, "native-mods");
        Utilities.deleteRecursive(nativeRoot);
        List<String> natives = new ArrayList<>();
        List<AssetOverlayBuilder.Entry> overlay = new ArrayList<>();
        List<AutoCloseable> open = new ArrayList<>();
        StringBuilder signature = new StringBuilder();
        signature.append(baseApk.getAbsolutePath()).append('|').append(baseApk.length()).append('|')
                .append(baseApk.lastModified()).append('\n');

        try {
            if (patch != null && patch.overlayDir != null) {
                collectDirectory(patch.overlayDir, "", overlay, signature, "patch");
            }

            int index = 0;
            for (File mod : orderedMods(context)) {
                if (mod.getName().endsWith(DISABLED)) continue;
                String name = mod.getName();
                File dest = new File(nativeRoot, String.format(Locale.ROOT, "%02d", index++));
                if (name.endsWith(".so")) {
                    copyReadOnly(mod, new File(dest, name));
                    natives.add(new File(dest, name).getAbsolutePath());
                    Log.i(TAG, "Native mod: " + name);
                } else if (name.endsWith(PACK_EXTENSION)) {
                    signature.append(name).append('|').append(mod.length()).append('|').append(mod.lastModified()).append('\n');
                    ZipFile zip = new ZipFile(mod);
                    open.add(zip);
                    for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                        ZipEntry entry = e.nextElement();
                        String entryName = entry.getName();
                        if (entry.isDirectory()) continue;
                        if (isRootNative(entryName)) {
                            File out = new File(dest, entryName);
                            out.getParentFile().mkdirs();
                            try (InputStream in = zip.getInputStream(entry)) {
                                java.nio.file.Files.copy(in, out.toPath());
                            }
                            //noinspection ResultOfMethodCallIgnored
                            out.setReadOnly();
                            natives.add(out.getAbsolutePath());
                            Log.i(TAG, "Native mod from " + name + ": " + entryName);
                        } else if (entryName.startsWith("overlay/") && entryName.length() > "overlay/".length()) {
                            overlay.add(new ZipOverlayEntry(zip, entry, entryName.substring("overlay/".length())));
                        }
                    }
                }
            }

            File overlayApk = null;
            File dir = new File(runtimeScoped, "overlay");
            if (!overlay.isEmpty()) {
                String id = sha1(signature.toString());
                overlayApk = new File(dir, "overlay-" + id + ".apk");
                if (!overlayApk.isFile()) {
                    Utilities.deleteRecursive(dir);
                    if (!dir.mkdirs()) throw new IOException("Cannot create " + dir);
                    Log.i(TAG, "Building overlay APK with " + overlay.size() + " files");
                    AssetOverlayBuilder.build(baseApk, overlayApk, overlay);
                } else {
                    Log.i(TAG, "Reusing overlay APK " + overlayApk.getName());
                }
                //noinspection ResultOfMethodCallIgnored
                overlayApk.setReadOnly();
            } else {
                Utilities.deleteRecursive(dir);
            }
            return new Prepared(natives.toArray(new String[0]), overlayApk);
        } finally {
            for (AutoCloseable c : open) {
                try {
                    c.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static void copyReadOnly(File from, File to) throws IOException {
        to.getParentFile().mkdirs();
        java.nio.file.Files.copy(from.toPath(), to.toPath());
        // Android 14+ refuses to load writable native libraries.
        //noinspection ResultOfMethodCallIgnored
        to.setReadOnly();
    }

    private static void collectDirectory(File dir, String prefix, List<AssetOverlayBuilder.Entry> into,
                                         StringBuilder signature, String label) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        java.util.Arrays.sort(files);
        for (File f : files) {
            String name = prefix + f.getName();
            if (f.isDirectory()) {
                collectDirectory(f, name + "/", into, signature, label);
            } else {
                signature.append(label).append(':').append(name).append('|').append(f.length()).append('|')
                        .append(f.lastModified()).append('\n');
                into.add(new FileOverlayEntry(f, name));
            }
        }
    }

    private static String sha1(String text) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format(Locale.ROOT, "%02x", b));
            return sb.substring(0, 16);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** An overlay file read straight out of a pack's zip. */
    private static final class ZipOverlayEntry implements AssetOverlayBuilder.Entry {
        private final ZipFile zip;
        private final ZipEntry entry;
        private final String name;

        ZipOverlayEntry(ZipFile zip, ZipEntry entry, String name) {
            this.zip = zip;
            this.entry = entry;
            this.name = name;
        }

        @Override public String name() { return name; }
        @Override public long size() { return entry.getSize(); }
        @Override public long crc() { return entry.getCrc(); }
        @Override public InputStream open() throws IOException { return zip.getInputStream(entry); }
    }

    /** An overlay file read from disk (the patch's extracted overlay folder). */
    private static final class FileOverlayEntry implements AssetOverlayBuilder.Entry {
        private final File file;
        private final String name;
        private long crc = -1;

        FileOverlayEntry(File file, String name) {
            this.file = file;
            this.name = name;
        }

        @Override public String name() { return name; }
        @Override public long size() { return file.length(); }

        @Override
        public long crc() {
            if (crc < 0) {
                CRC32 c = new CRC32();
                try (InputStream in = new FileInputStream(file)) {
                    byte[] buf = new byte[1 << 16];
                    int n;
                    while ((n = in.read(buf)) != -1) c.update(buf, 0, n);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
                crc = c.getValue();
            }
            return crc;
        }

        @Override public InputStream open() throws IOException { return new FileInputStream(file); }
    }
}
