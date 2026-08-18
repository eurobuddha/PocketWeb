package com.eurobuddha.pocketweb;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts a site ZIP (the packet's `file` field) into a per-site directory, safely.
 *
 * Site zips come from OTHER users on-chain, so extraction is hostile-input territory. The JS
 * version (pocketWeb/js/ziputil.js) wrote each entry as `mdsfolder + "/" + relpath` with no
 * validation — a crafted entry `../../<otherdapp>/index.html` could escape the site folder
 * (zip-slip, bug #4) — and imposed no decompressed-size cap (zip-bomb, bug #10). Both are fixed
 * here: every entry is resolved against the destination's canonical path and rejected if it
 * escapes, and entry-count / total-byte ceilings abort a bomb before it fills storage.
 *
 * Pure java.io + java.util.zip (no Android) so it is unit-testable off-device.
 */
public final class SiteExtractor {

    private SiteExtractor() {}

    /** A published site is one coin, ≤ ~50 KB zipped; these ceilings are generous but bounded. */
    public static final int MAX_ENTRIES = 512;
    public static final long MAX_TOTAL_BYTES = 8L * 1024 * 1024;   // 8 MB decompressed
    public static final long MAX_ENTRY_BYTES = 4L * 1024 * 1024;

    public static final class UnsafeZipException extends IOException {
        public UnsafeZipException(String m) { super(m); }
    }

    /** Extract "0x"+hex zip data into destDir. Returns the relative paths written. */
    public static List<String> extractHex(String zipHex, File destDir) throws IOException {
        return extract(PocketProtocol.hexDecode(zipHex), destDir);
    }

    /**
     * Read a single entry's bytes from a zip without extracting to disk, applying the same
     * name-safety checks and the per-entry size cap. Returns null if the entry is absent.
     * Used for cross-site GetDataURI (loading one file from another verified site).
     */
    public static byte[] readEntry(byte[] zipBytes, String wantPath) throws IOException {
        String want = normalizeRel(wantPath);
        if (want == null) throw new UnsafeZipException("unsafe requested path: " + wantPath);
        byte[] buf = new byte[64 * 1024];
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) { zin.closeEntry(); continue; }
                String norm = normalizeRel(e.getName());
                if (norm == null || !norm.equals(want)) { zin.closeEntry(); continue; }
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                long entryBytes = 0; int n;
                while ((n = zin.read(buf)) != -1) {
                    entryBytes += n;
                    if (entryBytes > MAX_ENTRY_BYTES) throw new UnsafeZipException("entry too large: " + norm);
                    bos.write(buf, 0, n);
                }
                return bos.toByteArray();
            }
        }
        return null;
    }

    /** Normalise a zip/relative path to a clean forward-slash form, or null if it is unsafe. */
    static String normalizeRel(String name) {
        if (name == null || name.isEmpty()) return null;
        if (name.startsWith("/") || name.startsWith("\\") || name.contains("\\")
                || name.contains("..") || name.contains(":")) return null;
        StringBuilder out = new StringBuilder();
        for (String seg : name.split("/")) {
            if (seg.isEmpty() || seg.equals(".")) continue;
            if (seg.equals("..")) return null;
            if (out.length() > 0) out.append('/');
            out.append(seg);
        }
        return out.length() == 0 ? null : out.toString();
    }

    public static List<String> extract(byte[] zipBytes, File destDir) throws IOException {
        if (!destDir.exists() && !destDir.mkdirs())
            throw new IOException("cannot create " + destDir);
        String base = destDir.getCanonicalPath() + File.separator;

        List<String> written = new ArrayList<>();
        long total = 0;
        int count = 0;
        byte[] buf = new byte[64 * 1024];

        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                String name = e.getName();
                if (e.isDirectory()) { zin.closeEntry(); continue; }
                if (++count > MAX_ENTRIES) throw new UnsafeZipException("too many entries (>" + MAX_ENTRIES + ")");

                // Reject obvious traversal/absolute forms before touching the filesystem.
                if (name.startsWith("/") || name.startsWith("\\") || name.contains("\\")
                        || name.contains("..") || name.contains(":"))
                    throw new UnsafeZipException("unsafe zip entry name: " + name);

                File out = new File(destDir, name);
                String canon = out.getCanonicalPath();
                if (!canon.startsWith(base))     // definitive escape check
                    throw new UnsafeZipException("zip entry escapes site dir: " + name);

                File parent = out.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs())
                    throw new IOException("cannot create dir for " + name);

                long entryBytes = 0;
                try (OutputStream os = new FileOutputStream(out)) {
                    int n;
                    while ((n = zin.read(buf)) != -1) {
                        entryBytes += n;
                        total += n;
                        if (entryBytes > MAX_ENTRY_BYTES) throw new UnsafeZipException("entry too large: " + name);
                        if (total > MAX_TOTAL_BYTES) throw new UnsafeZipException("zip expands past " + MAX_TOTAL_BYTES + " bytes");
                        os.write(buf, 0, n);
                    }
                }
                written.add(name);
                zin.closeEntry();
            }
        }
        return written;
    }
}
