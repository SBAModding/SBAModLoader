package dev.segually.sbamodloader.tools;

import android.content.Context;
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import dev.segually.sbamodloader.TargetGame;

/**
 * Developer tool: packs the interop assemblies this device generated into the two files a release of the
 * AssemblyRepository needs ({@code manifest.json} and {@code interop.zip}), so a game version can be published from a
 * device that had enough memory to generate them. The export is one zip that holds both.
 */
public final class InteropExport {
    private InteropExport() {
    }

    // Versions the published sets are generated with (see the AssemblyRepository README).
    private static final String INTEROP_GENERATOR = "1.5.2.0";
    private static final String CPP2IL_GENERATOR = "2022.1.0.0";

    private static File bepInExDir() {
        return Utilities.getExternalSBAModLoaderDirectory(TargetGame.PACKAGE_NAME, "BepInEx");
    }

    private static File interopDir() {
        return new File(bepInExDir(), "interop");
    }

    private static File[] dlls() {
        File[] files = interopDir().listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".dll"));
        return files == null ? new File[0] : files;
    }

    /** Whether there is a set of assemblies on this device and its hash file to export. */
    public static boolean available() {
        return dlls().length > 0 && new File(interopDir(), "assembly-hash.txt").isFile();
    }

    /** Whether the set was generated here, as opposed to downloaded from the AssemblyRepository. */
    public static boolean generatedHere(Context context) {
        if (!available()) return false;
        try {
            String hash = new String(java.nio.file.Files.readAllBytes(new File(interopDir(), "assembly-hash.txt").toPath()),
                    StandardCharsets.UTF_8).trim();
            String downloaded = context.getSharedPreferences("sba_interop_guard", Context.MODE_PRIVATE)
                    .getString("downloadedHash", "");
            return downloaded.isEmpty() || !downloaded.equalsIgnoreCase(hash);
        } catch (IOException e) {
            return false;
        }
    }

    /** The game version this export is for (the installed game's), or an empty string. */
    public static String gameVersion(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(TargetGame.PACKAGE_NAME, 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    public static String suggestedName(Context context) {
        return "assemblies-" + gameVersion(context) + ".zip";
    }

    /** Writes the export to {@code target}. Call from a background thread. */
    public static void export(Context context, Uri target) throws IOException {
        if (!available()) throw new IOException("There are no assemblies on this device yet");
        File cache = context.getCacheDir();
        File interopZip = new File(cache, "interop.zip");
        File[] files = dlls();
        Arrays.sort(files);
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(interopZip))) {
            for (File f : files) add(zip, f.getName(), f);
            add(zip, "assembly-hash.txt", new File(interopDir(), "assembly-hash.txt"));
        }

        File dataOnSdCard = Utilities.getExternalSBAModLoaderDirectory(TargetGame.PACKAGE_NAME, null);
        File copiedData = new File(dataOnSdCard, "Assets/bin/Data");
        File metadata = new File(copiedData, "Managed/Metadata/" + GlobalMetadata.FILE_NAME);
        String unity = "";
        try {
            String v = UnityUtils.TryGetVersion(copiedData);
            if (v != null) unity = v;
        } catch (RuntimeException ignored) {
        }
        String assemblyHash = new String(java.nio.file.Files.readAllBytes(new File(interopDir(), "assembly-hash.txt").toPath()),
                StandardCharsets.UTF_8).trim();
        PatchManager.ActivePatch patch = PatchManager.getActive(context);
        File il2cpp = patch != null && patch.libDir != null ? new File(patch.libDir, "libil2cpp.so") : null;

        try {
            JSONObject manifest = new JSONObject()
                    .put("format", 1)
                    .put("gameVersion", gameVersion(context))
                    .put("unityVersion", unity)
                    .put("metadataSha256", metadata.isFile() ? sha256(metadata) : "")
                    .put("gameAssemblySha256", il2cpp != null && il2cpp.isFile() ? sha256(il2cpp) : "")
                    .put("assemblyHash", assemblyHash)
                    .put("generators", new JSONObject().put("il2cppInterop", INTEROP_GENERATOR).put("cpp2il", CPP2IL_GENERATOR))
                    .put("interop", new JSONObject()
                            .put("file", "interop.zip")
                            .put("size", interopZip.length())
                            .put("sha256", sha256(interopZip))
                            .put("files", files.length + 1));
            try (OutputStream out = context.getContentResolver().openOutputStream(target);
                 ZipOutputStream zip = new ZipOutputStream(out)) {
                zip.putNextEntry(new ZipEntry("manifest.json"));
                zip.write(manifest.toString(2).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
                add(zip, "interop.zip", interopZip);
            }
        } catch (JSONException | java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            interopZip.delete();
        }
    }

    private static void add(ZipOutputStream zip, String name, File file) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) != -1) zip.write(buf, 0, n);
        }
        zip.closeEntry();
    }

    private static String sha256(File f) throws IOException, java.security.NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format(Locale.ROOT, "%02x", b));
        return sb.toString();
    }
}
