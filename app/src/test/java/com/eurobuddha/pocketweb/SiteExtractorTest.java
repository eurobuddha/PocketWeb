package com.eurobuddha.pocketweb;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** CI safety gate for SiteExtractor — zip-slip / zip-bomb rejection (self-contained, no fixtures). */
public class SiteExtractorTest {

    private static byte[] zip(String[][] entries) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bos)) {
            for (String[] e : entries) { z.putNextEntry(new ZipEntry(e[0])); z.write(e[1].getBytes("UTF-8")); z.closeEntry(); }
        }
        return bos.toByteArray();
    }

    private static File tmp() throws Exception { return Files.createTempDirectory("pw-site").toFile(); }

    private static void assertRejected(byte[] z) throws Exception {
        try { SiteExtractor.extract(z, tmp()); fail("expected UnsafeZipException"); }
        catch (SiteExtractor.UnsafeZipException expected) { /* good */ }
    }

    @Test public void legitZipExtractsNestedFiles() throws Exception {
        File d = tmp();
        List<String> w = SiteExtractor.extract(
                zip(new String[][]{{"index.html","<h1>hi</h1>"},{"img/logo.svg","<svg/>"}}), d);
        assertTrue(w.contains("index.html"));
        assertTrue(new File(d, "img/logo.svg").isFile());
    }

    @Test public void rejectsTraversal() throws Exception {
        assertRejected(zip(new String[][]{{"../evil.html","x"}}));
        assertRejected(zip(new String[][]{{"a/../../evil.html","x"}}));
        assertRejected(zip(new String[][]{{"/etc/evil","x"}}));
        assertRejected(zip(new String[][]{{"a\\..\\evil","x"}}));
    }

    @Test public void rejectsZipBomb() throws Exception {
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 5 * 1024 * 1024; i++) big.append('A');
        assertRejected(zip(new String[][]{{"big.bin", big.toString()}}));
    }
}
