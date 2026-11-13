package dev.segually.sbamodloader.tools;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lays overlay files over an APK without rewriting it. The APK is copied, the new or replacement entries are
 * appended as uncompressed ("stored") entries where the old central directory used to start, and a new central
 * directory is written that points at them. Entries that an overlay replaces stay in the file as dead data but
 * are no longer listed, so every reader of the zip sees one APK that simply contains the overlay's files.
 *
 * Plain Java, no Android classes, so it can be tested on a desktop JVM.
 */
public final class AssetOverlayBuilder {
    /** One file to lay over the APK. */
    public interface Entry {
        /** Path inside the APK, with '/' separators and no leading slash. */
        String name();

        /** Uncompressed size in bytes. */
        long size();

        /** CRC-32 of the uncompressed data. */
        long crc();

        InputStream open() throws IOException;
    }

    private static final int EOCD_SIGNATURE = 0x06054b50;
    private static final int CENTRAL_SIGNATURE = 0x02014b50;
    private static final int LOCAL_SIGNATURE = 0x04034b50;
    private static final int DOS_TIME = 0;
    private static final int DOS_DATE = (46 << 9) | (1 << 5) | 1; // 2026-01-01, a fixed value keeps output stable
    private static final int ALIGNMENT = 4;

    private AssetOverlayBuilder() {
    }

    /**
     * Writes {@code output}: {@code baseApk} with {@code overlays} laid over it. Later entries replace earlier
     * ones that have the same name.
     */
    public static void build(File baseApk, File output, List<? extends Entry> overlays) throws IOException {
        Map<String, Entry> merged = new LinkedHashMap<>();
        for (Entry e : overlays) {
            String name = clean(e.name());
            if (name.isEmpty() || name.endsWith("/")) continue;
            merged.put(name, e);
        }

        File tmp = new File(output.getParentFile(), output.getName() + ".tmp");
        try (InputStream in = new java.io.FileInputStream(baseApk);
             java.io.FileOutputStream out = new java.io.FileOutputStream(tmp)) {
            byte[] copyBuffer = new byte[1 << 17];
            int count;
            while ((count = in.read(copyBuffer)) != -1) out.write(copyBuffer, 0, count);
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new IOException("Cannot copy " + baseApk + " to " + tmp + ": " + e.getMessage(), e);
        }
        try (RandomAccessFile raf = new RandomAccessFile(tmp, "rw")) {
            FileChannel ch = raf.getChannel();

            long eocdPos = findEocd(raf);
            ByteBuffer eocd = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN);
            readFully(ch, eocd, eocdPos);
            eocd.flip();
            eocd.getInt(); // signature
            eocd.getShort(); // disk
            eocd.getShort(); // disk with central directory
            int entriesOnDisk = eocd.getShort() & 0xFFFF;
            int totalEntries = eocd.getShort() & 0xFFFF;
            long centralSize = eocd.getInt() & 0xFFFFFFFFL;
            long centralOffset = eocd.getInt() & 0xFFFFFFFFL;
            if (totalEntries == 0xFFFF || centralSize == 0xFFFFFFFFL || centralOffset == 0xFFFFFFFFL
                    || entriesOnDisk != totalEntries) {
                throw new IOException("Zip64 and multi-disk archives are not supported");
            }

            // Existing central directory records, keyed by entry name, in their original order.
            ByteBuffer central = ByteBuffer.allocate((int) centralSize);
            readFully(ch, central, centralOffset);
            Map<String, byte[]> records = new LinkedHashMap<>();
            central.flip();
            central.order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < totalEntries; i++) {
                int start = central.position();
                if (central.getInt() != CENTRAL_SIGNATURE) throw new IOException("Corrupt central directory");
                central.position(start + 28);
                int nameLen = central.getShort() & 0xFFFF;
                int extraLen = central.getShort() & 0xFFFF;
                int commentLen = central.getShort() & 0xFFFF;
                int end = start + 46 + nameLen + extraLen + commentLen;
                byte[] record = new byte[end - start];
                central.position(start);
                central.get(record);
                String name = new String(record, 46, nameLen, StandardCharsets.UTF_8);
                records.put(name, record);
            }

            // New entries start where the old central directory began.
            long position = centralOffset;
            raf.seek(position);
            byte[] buffer = new byte[1 << 16];
            for (Entry entry : merged.values()) {
                byte[] name = entry.name().getBytes(StandardCharsets.UTF_8);
                long headerLength = 30 + name.length;
                int pad = (int) ((ALIGNMENT - ((position + headerLength) % ALIGNMENT)) % ALIGNMENT);
                int extraLen = pad == 0 ? 0 : pad + ALIGNMENT; // a padding field needs room for its 4 byte header
                long size = entry.size();
                if (size > 0xFFFFFFFFL) throw new IOException("Entry too large: " + entry.name());
                int flags = isAscii(name) ? 0 : 0x0800;

                ByteBuffer local = ByteBuffer.allocate((int) headerLength + extraLen).order(ByteOrder.LITTLE_ENDIAN);
                local.putInt(LOCAL_SIGNATURE).putShort((short) 20).putShort((short) flags).putShort((short) 0);
                local.putShort((short) DOS_TIME).putShort((short) DOS_DATE);
                local.putInt((int) entry.crc()).putInt((int) size).putInt((int) size);
                local.putShort((short) name.length).putShort((short) extraLen);
                local.put(name);
                if (extraLen > 0) {
                    local.putShort((short) 0xD935).putShort((short) pad); // zipalign's padding field id
                    local.put(new byte[pad]);
                }
                raf.write(local.array());

                long copied = 0;
                try (InputStream in = entry.open()) {
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        raf.write(buffer, 0, n);
                        copied += n;
                    }
                }
                if (copied != size) throw new IOException("Size of " + entry.name() + " changed while copying");

                ByteBuffer record = ByteBuffer.allocate(46 + name.length).order(ByteOrder.LITTLE_ENDIAN);
                record.putInt(CENTRAL_SIGNATURE).putShort((short) 20).putShort((short) 20)
                        .putShort((short) flags).putShort((short) 0);
                record.putShort((short) DOS_TIME).putShort((short) DOS_DATE);
                record.putInt((int) entry.crc()).putInt((int) size).putInt((int) size);
                record.putShort((short) name.length).putShort((short) 0).putShort((short) 0); // name, extra, comment
                record.putShort((short) 0).putShort((short) 0).putInt(0); // disk, internal attrs, external attrs
                record.putInt((int) position);
                record.put(name);
                records.put(entry.name(), record.array());

                position += local.capacity() + size;
            }

            if (records.size() > 0xFFFE) throw new IOException("Too many entries for a plain zip");
            long newCentralOffset = position;
            long newCentralSize = 0;
            for (byte[] record : records.values()) {
                raf.write(record);
                newCentralSize += record.length;
            }
            ByteBuffer end = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN);
            end.putInt(EOCD_SIGNATURE).putShort((short) 0).putShort((short) 0)
                    .putShort((short) records.size()).putShort((short) records.size())
                    .putInt((int) newCentralSize).putInt((int) newCentralOffset).putShort((short) 0);
            raf.write(end.array());
            raf.setLength(raf.getFilePointer());
        } catch (IOException | RuntimeException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw e;
        }
        Files.move(tmp.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void readFully(FileChannel ch, ByteBuffer buffer, long position) throws IOException {
        while (buffer.hasRemaining()) {
            int n = ch.read(buffer, position + buffer.position());
            if (n < 0) throw new IOException("Unexpected end of file");
        }
    }

    private static long findEocd(RandomAccessFile raf) throws IOException {
        long length = raf.length();
        int window = (int) Math.min(length, 22 + 0xFFFF);
        byte[] tail = new byte[window];
        raf.seek(length - window);
        raf.readFully(tail);
        for (int i = window - 22; i >= 0; i--) {
            if (tail[i] == 0x50 && tail[i + 1] == 0x4b && tail[i + 2] == 0x05 && tail[i + 3] == 0x06) {
                return length - window + i;
            }
        }
        throw new IOException("Not a zip file");
    }

    private static String clean(String name) {
        String n = name.replace('\\', '/');
        while (n.startsWith("/")) n = n.substring(1);
        return n;
    }

    private static boolean isAscii(byte[] bytes) {
        for (byte b : bytes) if (b < 0) return false;
        return true;
    }

    /** Convenience for tests and callers that already hold a list of entries. */
    public static List<Entry> list(Entry... entries) {
        return new ArrayList<>(java.util.Arrays.asList(entries));
    }
}
