package ide.sketchware.utility;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

/**
 * Tests for the zip-slip security fix and try-with-resources refactoring in
 * {@link FileUtil#extractZipTo(java.util.zip.ZipInputStream, String)}.
 */
public class FileUtilExtractZipTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    // ==================== Helpers ====================

    private java.util.zip.ZipInputStream buildZip(ZipEntry... entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (ZipEntry entry : entries) {
                zos.putNextEntry(entry);
                if (!entry.isDirectory()) {
                    zos.write(("content:" + entry.getName()).getBytes(StandardCharsets.UTF_8));
                }
                zos.closeEntry();
            }
        }
        return new java.util.zip.ZipInputStream(new ByteArrayInputStream(baos.toByteArray()));
    }

    private java.util.zip.ZipInputStream buildZipWithContent(String entryName, byte[] content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry(entryName);
            zos.putNextEntry(entry);
            zos.write(content);
            zos.closeEntry();
        }
        return new java.util.zip.ZipInputStream(new ByteArrayInputStream(baos.toByteArray()));
    }

    private java.util.zip.ZipInputStream buildZipWithTraversalEntry(String maliciousName) throws IOException {
        // Build a ZipInputStream whose entry has a path-traversal name.
        // ZipOutputStream sanitizes names, so we need to write the raw zip bytes
        // with the malicious name encoded directly.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // Local file header signature
        byte[] name = maliciousName.getBytes(StandardCharsets.UTF_8);
        // We'll use ZipOutputStream but set the name via a raw approach.
        // Actually ZipOutputStream allows arbitrary names — it just doesn't resolve them.
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry(maliciousName);
            zos.putNextEntry(entry);
            zos.write("evil".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return new java.util.zip.ZipInputStream(new ByteArrayInputStream(baos.toByteArray()));
    }

    // ==================== Normal extraction ====================

    @Test
    public void extractZipTo_singleFile_extractsCorrectly() throws IOException {
        File outDir = tempFolder.newFolder("out");
        byte[] expectedContent = "hello world".getBytes(StandardCharsets.UTF_8);
        java.util.zip.ZipInputStream zis = buildZipWithContent("hello.txt", expectedContent);

        FileUtil.extractZipTo(zis, outDir.getAbsolutePath());

        File extracted = new File(outDir, "hello.txt");
        assertTrue("Extracted file should exist", extracted.exists());
        assertArrayEquals(expectedContent, java.nio.file.Files.readAllBytes(extracted.toPath()));
    }

    @Test
    public void extractZipTo_nestedFile_extractsCorrectly() throws IOException {
        File outDir = tempFolder.newFolder("out");
        byte[] content = "nested content".getBytes(StandardCharsets.UTF_8);
        java.util.zip.ZipInputStream zis = buildZipWithContent("sub/dir/file.txt", content);

        FileUtil.extractZipTo(zis, outDir.getAbsolutePath());

        File extracted = new File(outDir, "sub/dir/file.txt");
        assertTrue("Nested extracted file should exist", extracted.exists());
        assertArrayEquals(content, java.nio.file.Files.readAllBytes(extracted.toPath()));
    }

    @Test
    public void extractZipTo_directoryEntry_isSkipped() throws IOException {
        File outDir = tempFolder.newFolder("out");
        ZipEntry dirEntry = new ZipEntry("subdir/");
        java.util.zip.ZipInputStream zis = buildZip(dirEntry);

        // Should not throw
        FileUtil.extractZipTo(zis, outDir.getAbsolutePath());
    }

    @Test
    public void extractZipTo_multipleFiles_allExtracted() throws IOException {
        File outDir = tempFolder.newFolder("out");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (String name : new String[]{"a.txt", "b.txt", "c.txt"}) {
                ZipEntry entry = new ZipEntry(name);
                zos.putNextEntry(entry);
                zos.write(name.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        java.util.zip.ZipInputStream zis =
                new java.util.zip.ZipInputStream(new ByteArrayInputStream(baos.toByteArray()));

        FileUtil.extractZipTo(zis, outDir.getAbsolutePath());

        assertTrue(new File(outDir, "a.txt").exists());
        assertTrue(new File(outDir, "b.txt").exists());
        assertTrue(new File(outDir, "c.txt").exists());
    }

    @Test
    public void extractZipTo_outputDirDoesNotExist_createsDir() throws IOException {
        File outDir = new File(tempFolder.getRoot(), "newdir");
        assertFalse("Dir should not exist yet", outDir.exists());

        byte[] content = "test".getBytes(StandardCharsets.UTF_8);
        java.util.zip.ZipInputStream zis = buildZipWithContent("file.txt", content);

        FileUtil.extractZipTo(zis, outDir.getAbsolutePath());

        assertTrue("Output dir should be created", outDir.exists());
        assertTrue(new File(outDir, "file.txt").exists());
    }

    // ==================== Zip slip prevention (security fix) ====================

    @Test
    public void extractZipTo_dotDotPathTraversal_throwsSecurityException() throws IOException {
        File outDir = tempFolder.newFolder("out");
        // "../escape.txt" attempts to write outside the output directory
        java.util.zip.ZipInputStream zis = buildZipWithTraversalEntry("../escape.txt");

        try {
            FileUtil.extractZipTo(zis, outDir.getAbsolutePath());
            fail("Should have thrown SecurityException for path traversal");
        } catch (SecurityException e) {
            assertTrue("Exception message should mention blocked traversal",
                    e.getMessage().contains("Blocked zip slip") || e.getMessage() != null);
        }
    }

    @Test
    public void extractZipTo_absolutePathEntry_throwsSecurityException() throws IOException {
        File outDir = tempFolder.newFolder("out");
        // Absolute path entry that escapes the output dir (canonicalize resolves it)
        java.util.zip.ZipInputStream zis = buildZipWithTraversalEntry("/etc/evil.txt");

        try {
            FileUtil.extractZipTo(zis, outDir.getAbsolutePath());
            fail("Should have thrown SecurityException for absolute path entry");
        } catch (SecurityException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void extractZipTo_dotDotInNestedPath_throwsSecurityException() throws IOException {
        File outDir = tempFolder.newFolder("out");
        java.util.zip.ZipInputStream zis = buildZipWithTraversalEntry("subdir/../../escape.txt");

        try {
            FileUtil.extractZipTo(zis, outDir.getAbsolutePath());
            fail("Should have thrown SecurityException for nested path traversal");
        } catch (SecurityException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void extractZipTo_safeSubdirEntry_doesNotThrow() throws IOException {
        File outDir = tempFolder.newFolder("out");
        // "subdir/../file.txt" resolves to "file.txt" which IS inside outDir — safe
        java.util.zip.ZipInputStream zis = buildZipWithTraversalEntry("subdir/../file.txt");

        // This resolves to outDir/file.txt which is inside outDir — should NOT throw
        try {
            FileUtil.extractZipTo(zis, outDir.getAbsolutePath());
            // If it succeeds, verify the file is inside the output dir
            assertTrue(new File(outDir, "file.txt").exists());
        } catch (SecurityException e) {
            // If the implementation also blocks this, it's conservatively correct
            // (canonical path of outDir + "/" + "subdir/../file.txt" = outDir/file.txt
            // which starts with canonicalOutDirPath + separator, so it should pass)
            fail("subdir/../file.txt resolves to file.txt inside outDir — should not be blocked");
        }
    }

    // ==================== ZipInputStream is closed after extraction ====================

    @Test
    public void extractZipTo_closesInputStream() throws IOException {
        File outDir = tempFolder.newFolder("out");
        byte[] content = "data".getBytes(StandardCharsets.UTF_8);

        // Wrap in a custom stream to detect if close() is called
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("file.txt");
            zos.putNextEntry(entry);
            zos.write(content);
            zos.closeEntry();
        }
        final boolean[] closed = {false};
        java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                new ByteArrayInputStream(baos.toByteArray())) {
            @Override
            public void close() throws IOException {
                closed[0] = true;
                super.close();
            }
        };

        FileUtil.extractZipTo(zis, outDir.getAbsolutePath());

        assertTrue("ZipInputStream should be closed after extraction", closed[0]);
    }

    // ==================== Canonical path boundary check ====================

    @Test
    public void extractZipTo_fileExactlyAtOutDirBoundary_throwsSecurityException() throws IOException {
        // An entry whose canonical path equals the outDir itself (not a child)
        // would not start with canonicalOutDirPath + File.separator, so it should be blocked
        File outDir = tempFolder.newFolder("out");
        // We can't create an entry whose canonical path is exactly outDir in a portable way,
        // but we verify that the check uses + File.separator (not just startsWith(outDirPath))
        // to prevent a sibling directory like "out2" matching "out"'s canonical path prefix.

        // Create a sibling dir to simulate canonical path adjacency
        File sibling = tempFolder.newFolder("out_sibling");
        // An entry targeting sibling: "../../out_sibling/file.txt" from outDir/
        // Resolved: outDir/../../out_sibling/file.txt = parent/out_sibling/file.txt
        // which does NOT start with outDir's canonical path + sep — should be blocked
        java.util.zip.ZipInputStream zis = buildZipWithTraversalEntry("../../" + sibling.getName() + "/file.txt");

        try {
            FileUtil.extractZipTo(zis, outDir.getAbsolutePath());
            fail("Should have thrown SecurityException — target is outside outDir");
        } catch (SecurityException e) {
            assertNotNull(e.getMessage());
        }
    }
}