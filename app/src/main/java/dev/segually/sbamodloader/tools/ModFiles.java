package dev.segually.sbamodloader.tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import dev.segually.sbamodloader.TargetGame;

/** Where BepInEx plugins live, and the file operations shared by manual imports and the mod browser. */
public final class ModFiles {
    private ModFiles() {
    }

    public static File pluginsDir() {
        return new File(Utilities.getExternalSBAModLoaderDirectory(TargetGame.PACKAGE_NAME, "BepInEx"), "plugins");
    }

    public static void copy(InputStream in, File out) throws IOException {
        File parent = out.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create " + parent);
        try (FileOutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) os.write(buf, 0, n);
        }
    }

    /** Unpacks a zip into {@code dir}, refusing entries that would land outside it. Created files go into {@code created}. */
    public static void unzipInto(InputStream in, File dir, List<File> created) throws IOException {
        String root = dir.getCanonicalPath() + File.separator;
        try (ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                File out = new File(dir, e.getName());
                if (!out.getCanonicalPath().startsWith(root)) continue; // zip-slip guard
                if (e.isDirectory()) {
                    //noinspection ResultOfMethodCallIgnored
                    out.mkdirs();
                } else {
                    copy(zis, out);
                    if (created != null) created.add(out);
                }
            }
        }
    }

    /** True when at least one enabled Fusion mod (a ".dll" that is not switched off) is installed. */
    public static boolean hasEnabledFusionMods() {
        File bepInEx = Utilities.getExternalSBAModLoaderDirectory(TargetGame.PACKAGE_NAME, "BepInEx");
        return containsDll(new File(bepInEx, "plugins")) || containsDll(new File(bepInEx, "patchers"));
    }

    private static boolean containsDll(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return false;
        for (File f : files) {
            if (f.isDirectory()) {
                if (containsDll(f)) return true;
            } else if (f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".dll")) {
                return true;
            }
        }
        return false;
    }
}
