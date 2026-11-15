package dev.segually.sbamodloader.tools;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
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
 *       ({@code .so} files at its root), an {@code overlay/} folder whose files are laid over the game's
 *       APK ({@code overlay/assets/aa/Android/x.bundle} adds or replaces {@code assets/aa/Android/x.bundle}), and
 *       libraries for the game's Java code in {@code lib/arm64-v8a/}. All parts are optional.
 *       Dex files at the root of the overlay ({@code overlay/classes.dex}, {@code overlay/classes2.dex}...) are
 *       renumbered when the mods are merged: they are put after the game's own dex files, in load order, keeping
 *       each pack's own order, so packs never replace the game's code or each other's. They are then picked up by
 *       the game's class loader like the rest of the APK.</li>
 *   <li>An optional {@code sbapack.json} at the root of a pack can name an {@code "activity"}: a class from the
 *       pack's dex files that replaces the game's launch activity (it should extend that activity).</li>
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
    /** File name of the merged APK; see {@link #prepare}. */
    public static final String OVERLAY_APK_NAME = "ovly.apk";

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

    /** What a pack says about itself in sbapack.json. Every field is null when the pack does not provide it. */
    public static final class Info {
        public final String name;
        public final String version;
        public final String author;

        Info(String name, String version, String author) {
            this.name = name;
            this.version = version;
            this.author = author;
        }

        static final Info NONE = new Info(null, null, null);
    }

    private static final Map<String, Info> INFO_CACHE = new HashMap<>();

    /** The name, version and author a pack declares. Cached until the file changes; reads the zip otherwise. */
    public static synchronized Info info(File pack) {
        String key = pack.getAbsolutePath() + "|" + pack.length() + "|" + pack.lastModified();
        Info cached = INFO_CACHE.get(key);
        if (cached != null) return cached;
        Info info = Info.NONE;
        try (ZipFile zip = new ZipFile(pack)) {
            ZipEntry entry = zip.getEntry("sbapack.json");
            if (entry != null) {
                try (InputStream in = zip.getInputStream(entry)) {
                    org.json.JSONObject o = new org.json.JSONObject(
                            new String(in.readNBytes(1 << 16), java.nio.charset.StandardCharsets.UTF_8));
                    info = new Info(text(o, "name"), text(o, "version"), text(o, "author"));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Unreadable sbapack.json in " + pack.getName(), e);
        }
        INFO_CACHE.put(key, info);
        return info;
    }

    /** A non-blank string field, trimmed and capped; null when missing or not a string. */
    private static String text(org.json.JSONObject o, String key) {
        Object v = o.opt(key);
        if (!(v instanceof String)) return null;
        String t = ((String) v).trim();
        if (t.isEmpty()) return null;
        return t.length() > 80 ? t.substring(0, 80) : t;
    }

    /** What a pack contains, for display: a combination of "native", "java" and "assets" (or empty). */
    public static List<String> describe(File pack) {
        boolean natives = false;
        boolean java = false;
        boolean assets = false;
        try (ZipFile zip = new ZipFile(pack)) {
            for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                ZipEntry entry = e.nextElement();
                String name = entry.getName();
                if (entry.isDirectory()) continue;
                if (isRootNative(name) || (name.startsWith("lib/") && name.endsWith(".so"))) natives = true;
                else if (name.startsWith("overlay/classes") && name.endsWith(".dex")) java = true;
                else if (name.startsWith("overlay/")) assets = true;
            }
        } catch (IOException e) {
            return Collections.emptyList();
        }
        List<String> parts = new ArrayList<>();
        if (natives) parts.add("native");
        if (java) parts.add("java");
        if (assets) parts.add("assets");
        return parts;
    }

    /**
     * The number in a dex file name at the root: 1 for {@code classes.dex}, 5 for {@code classes5.dex}. -1 when the
     * name is not a root dex file.
     */
    static int dexNumber(String name) {
        if (!name.startsWith("classes") || !name.endsWith(".dex")) return -1;
        String digits = name.substring("classes".length(), name.length() - ".dex".length());
        if (digits.isEmpty()) return 1;
        if (digits.length() > 4 || !digits.chars().allMatch(Character::isDigit) || digits.charAt(0) == '0') return -1;
        int n = Integer.parseInt(digits);
        return n >= 2 ? n : -1;
    }

    static String dexName(int number) {
        return number <= 1 ? "classes.dex" : "classes" + number + ".dex";
    }

    /** The highest dex number among the entries of the game's APK, so packs are numbered after it. */
    private static int highestDex(File apk) throws IOException {
        int highest = 1;
        try (ZipFile zip = new ZipFile(apk)) {
            for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                highest = Math.max(highest, dexNumber(e.nextElement().getName()));
            }
        }
        return highest;
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
        /** Directories with libraries for the game's Java code, highest priority first. */
        public final List<File> libDirs;
        /** Class name of a launch activity supplied by a pack, or null. */
        public final String activity;

        Prepared(String[] natives, File overlayApk, List<File> libDirs, String activity) {
            this.natives = natives;
            this.overlayApk = overlayApk;
            this.libDirs = libDirs;
            this.activity = activity;
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
        // Fresh, uniquely named folders for this launch: a leftover from an earlier launch that could not be
        // deleted (read-only files, a busy filesystem) can then never collide with the new copies.
        File[] stale = runtimeScoped.listFiles();
        if (stale != null) {
            for (File f : stale) {
                if (f.getName().startsWith("native-mods") || f.getName().startsWith("pack-libs")) {
                    Utilities.deleteRecursive(f);
                }
            }
        }
        String launchId = Long.toString(System.currentTimeMillis(), 36);
        File nativeRoot = new File(runtimeScoped, "native-mods-" + launchId);
        File libRoot = new File(runtimeScoped, "pack-libs-" + launchId);
        List<String> natives = new ArrayList<>();
        List<File> libDirs = new ArrayList<>();
        String activity = null;
        List<AssetOverlayBuilder.Entry> overlay = new ArrayList<>();
        List<AutoCloseable> open = new ArrayList<>();
        StringBuilder signature = new StringBuilder();
        signature.append(baseApk.getAbsolutePath()).append('|').append(baseApk.length()).append('|')
                .append(baseApk.lastModified()).append('\n');

        try {
            if (patch != null && patch.overlayDir != null) {
                collectDirectory(patch.overlayDir, "", overlay, signature, "patch");
            }
            // Dex files of packs are numbered after the game's own (and the patch's), in load order, so two packs
            // that both ship a classes5.dex, or one that numbers from 1, can never replace each other's code or the game's.
            int nextDex = highestDex(baseApk) + 1;
            for (AssetOverlayBuilder.Entry e : overlay) nextDex = Math.max(nextDex, dexNumber(e.name()) + 1);

            int index = 0;
            for (File mod : orderedMods(context)) {
                if (mod.getName().endsWith(DISABLED)) continue;
                String name = mod.getName();
                try {
                File dest = new File(nativeRoot, String.format(Locale.ROOT, "%02d", index++));
                if (name.endsWith(".so")) {
                    copyFile(mod, new File(dest, name));
                    natives.add(new File(dest, name).getAbsolutePath());
                    Log.i(TAG, "Native mod: " + name);
                } else if (name.endsWith(PACK_EXTENSION)) {
                    signature.append(name).append('|').append(mod.length()).append('|').append(mod.lastModified()).append('\n');
                    ZipFile zip = new ZipFile(mod);
                    open.add(zip);
                    java.util.TreeMap<Integer, ZipEntry> dexFiles = new java.util.TreeMap<>();
                    for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                        ZipEntry entry = e.nextElement();
                        String entryName = entry.getName();
                        if (entry.isDirectory()) continue;
                        if (entryName.equals("sbapack.json")) {
                            String declared = readActivity(zip, entry);
                            if (declared != null) {
                                if (activity != null) Log.w(TAG, name + " replaces the launch activity " + activity);
                                activity = declared;
                            }
                        } else if (entryName.startsWith("lib/arm64-v8a/") && entryName.endsWith(".so")
                                && entryName.indexOf('/', "lib/arm64-v8a/".length()) < 0) {
                            File libDir = new File(libRoot, String.format(Locale.ROOT, "%02d", index));
                            File out = new File(libDir, entryName.substring("lib/arm64-v8a/".length()));
                            try (InputStream in = zip.getInputStream(entry)) {
                                copyStream(in, out);
                            }
                            if (!libDirs.contains(libDir)) libDirs.add(libDir);
                            Log.i(TAG, "Library from " + name + ": " + out.getName());
                        } else if (isRootNative(entryName)) {
                            File out = new File(dest, entryName);
                            try (InputStream in = zip.getInputStream(entry)) {
                                copyStream(in, out);
                            }
                            natives.add(out.getAbsolutePath());
                            Log.i(TAG, "Native mod from " + name + ": " + entryName);
                        } else if (entryName.startsWith("overlay/") && dexNumber(entryName.substring("overlay/".length())) > 0) {
                            dexFiles.put(dexNumber(entryName.substring("overlay/".length())), entry);
                        } else if (entryName.startsWith("overlay/") && entryName.length() > "overlay/".length()) {
                            overlay.add(new ZipOverlayEntry(zip, entry, entryName.substring("overlay/".length())));
                        }
                    }
                    for (java.util.Map.Entry<Integer, ZipEntry> dex : dexFiles.entrySet()) {
                        String numbered = dexName(nextDex++);
                        overlay.add(new ZipOverlayEntry(zip, dex.getValue(), numbered));
                        Log.i(TAG, "Java code from " + name + ": " + dex.getValue().getName().substring("overlay/".length())
                                + " -> " + numbered);
                    }
                }
                } catch (IOException | RuntimeException e) {
                    throw new IOException("Could not load the mod \"" + name + "\": " + e, e);
                }
            }

            File overlayApk = null;
            File dir = new File(runtimeScoped, "overlay");
            if (!overlay.isEmpty()) {
                String id = sha1(signature.toString());
                // The file name is fixed and distinctive: native mods that look for the game's APK among the
                // process's mapped files can tell the overlay APK from the game's own base.apk.
                File idDir = new File(dir, id);
                overlayApk = new File(idDir, OVERLAY_APK_NAME);
                if (!overlayApk.isFile()) {
                    Utilities.deleteRecursive(dir);
                    if (!idDir.mkdirs()) throw new IOException("Cannot create " + idDir);
                    long needed = baseApk.length() + 32L * 1024 * 1024;
                    for (AssetOverlayBuilder.Entry e : overlay) needed += Math.max(0, e.size());
                    long free = runtimeScoped.getParentFile().getUsableSpace();
                    if (free < needed) {
                        Utilities.deleteRecursive(dir);
                        throw new IOException(String.format(Locale.ROOT,
                                "Not enough free storage to apply the mods: %d MB needed, %d MB free. Free some space and try again.",
                                needed >> 20, free >> 20));
                    }
                    Log.i(TAG, "Building overlay APK with " + overlay.size() + " files");
                    try {
                        AssetOverlayBuilder.build(baseApk, overlayApk, overlay);
                    } catch (IOException | RuntimeException e) {
                        Utilities.deleteRecursive(dir); // never leave a half-built APK behind
                        throw new IOException("Could not build the mod overlay (" + (free >> 20) + " MB free): " + e, e);
                    }
                } else {
                    Log.i(TAG, "Reusing overlay APK " + overlayApk.getName());
                }
                //noinspection ResultOfMethodCallIgnored
                overlayApk.setReadOnly();
            } else {
                Utilities.deleteRecursive(dir);
            }
            Collections.reverse(libDirs); // a later pack's libraries win over an earlier one's
            return new Prepared(natives.toArray(new String[0]), overlayApk, libDirs, activity);
        } finally {
            for (AutoCloseable c : open) {
                try {
                    c.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static String readActivity(ZipFile zip, ZipEntry entry) {
        try (InputStream in = zip.getInputStream(entry)) {
            String name = new org.json.JSONObject(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                    .optString("activity", "");
            return name.isEmpty() ? null : name;
        } catch (Exception e) {
            Log.w(TAG, "Unreadable sbapack.json", e);
            return null;
        }
    }

    private static void copyFile(File from, File to) throws IOException {
        try (InputStream in = new FileInputStream(from)) {
            copyStream(in, to);
        }
    }

    /** Plain stream copy that overwrites, reports exactly what failed, and leaves the result read-only. */
    private static void copyStream(InputStream in, File to) throws IOException {
        File parent = to.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Cannot create folder " + parent);
        }
        if (to.exists() && !to.delete()) {
            throw new IOException("Cannot replace existing file " + to);
        }
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(to)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            to.delete();
            throw new IOException("Cannot write " + to + ": " + e.getMessage(), e);
        }
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
