package com.eurobuddha.pocketweb;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Runnable safety test for SiteExtractor (bare JDK). Exercises real attack payloads. */
public class SiteExtractorSelfTest {

    static int failures = 0;
    static void check(boolean c, String m) {
        System.out.println((c ? "  PASS  " : "  FAIL  ") + m);
        if (!c) failures++;
    }

    static byte[] zip(String[][] entries) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bos)) {
            for (String[] e : entries) {
                z.putNextEntry(new ZipEntry(e[0]));
                z.write(e[1].getBytes("UTF-8"));
                z.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    static File tmpDir() throws Exception { return Files.createTempDirectory("pw-site").toFile(); }

    static boolean rejects(byte[] z) throws Exception {
        File d = tmpDir();
        try { SiteExtractor.extract(z, d); return false; }
        catch (SiteExtractor.UnsafeZipException e) { return true; }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== SiteExtractor safety ==");

        // legit flat site
        byte[] good = zip(new String[][]{
                {"index.html", "<h1>hi</h1>"}, {"style.css", "body{}"}, {"img/logo.svg", "<svg/>"}});
        File d = tmpDir();
        List<String> w = SiteExtractor.extract(good, d);
        check(w.contains("index.html") && w.contains("img/logo.svg"), "legit zip extracts nested files");
        check(new File(d, "index.html").isFile(), "index.html written to disk");

        // zip-slip variants
        check(rejects(zip(new String[][]{{"../evil.html", "x"}})), "rejects ../ traversal");
        check(rejects(zip(new String[][]{{"a/../../evil.html", "x"}})), "rejects nested ../.. traversal");
        check(rejects(zip(new String[][]{{"/etc/evil", "x"}})), "rejects absolute path");
        check(rejects(zip(new String[][]{{"a\\..\\evil", "x"}})), "rejects backslash traversal");

        // zip-bomb: one 5 MB entry (> MAX_ENTRY_BYTES) — highly compressible, tiny on disk
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 5 * 1024 * 1024; i++) big.append('A');
        check(rejects(zip(new String[][]{{"big.bin", big.toString()}})), "rejects oversized entry (zip-bomb)");

        // real on-chain site payload
        byte[] real = Files.readAllBytes(new File("protocol-vectors/real-site.zip").toPath());
        File rd = tmpDir();
        List<String> rw = SiteExtractor.extract(real, rd);
        check(rw.stream().anyMatch(s -> s.endsWith("index.html")), "real on-chain site extracts (has index.html): " + rw);

        System.out.println();
        if (failures == 0) System.out.println("SiteExtractor: ALL SAFETY CHECKS PASSED.");
        else { System.out.println(failures + " FAILURE(S)."); System.exit(1); }
    }
}
