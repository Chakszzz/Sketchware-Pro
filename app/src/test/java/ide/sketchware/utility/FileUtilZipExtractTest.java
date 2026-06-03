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
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link FileUtil#extractZipTo(ZipInputStream, String)} and
 * {@link FileUtil#writeBytes(File, byte[])}.
 *
 * Focuses on the security fix (zip-slip prevention) and the try-with-resources
 * resource management introduced in this PR.
 */
public class FileUtilZipExtractTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Builds a ZipInputStream from the given (entryName, content) pairs. */
    private ZipInputStream buildZip(String... entriesAndContents) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (int i = 0; i < entriesAndContents.length; i += 2) {
                String name = entriesAndContents[i];
                String content = entriesAndContents[i + 1];
                zos.putNextEntry(new ZipEntry(name));
                if (content != null) {
                    zos.write(content.getBytes(StandardCharsets.UTF_8));
                }
                zos.closeEntry();
            }
        }
        return new ZipInputStream(new ByteArrayInputStream(bos.toByteArray()));
    }

    /** Builds a ZipInputStream containing a single directory entry. */
    private ZipInputStream buildZipWithDirEntry(String dirName) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            // Directory entries have a trailing slash
            ZipEntry entry = new ZipEntry(dirName.endsWith("/") ? dirName : dirName + "/");
            zos.putNextEntry(entry);
            zos.closeEntry();
        }
        return new ZipInputStream(new ByteArrayInputStream(bos.toByteArray()));
    }

    // -----------------------------------------------------------------------
    // Normal extraction
    // -----------------------------------------------------------------------

    @Test
    public void extractZipTo_singleFile_extractedToOutputDir() throws IOException {
        File outDir = tempFolder.newFolder("out");
        ZipInputStream zip = buildZip("hello.txt", "Hello, World!");

        FileUtil.extractZipTo(zip, outDir.getAbsolutePath());

        File extracted = new File(outDir, "hello.txt");
        assertTrue("Extracted file should exist", extracted.exists());
        assertTrue("Extracted file should be a file", extracted.isFile());
    }

    @Test
    public void extractZipTo_fileContent_writtenCorrectly() throws IOException {
        File outDir = tempFolder.newFolder("out");
        String expectedContent = "test content 12345";
        ZipInputStream zip = buildZip("data.txt", expectedContent);

        FileUtil.extractZipTo(zip, outDir.getAbsolutePath());

        File extracted = new File(outDir, "data.txt");
        byte[] bytes = java.nio.file.Files.readAllBytes(extracted.toPath());
        assertEquals(expectedContent, new String(bytes, StandardCharsets.UTF_8));
    }

    @Test
    public void extractZipTo_multipleFiles_allExtracted() throws IOException {
        File outDir = tempFolder.newFolder("out");
        ZipInputStream zip = buildZip(
                "file1.txt", "content1",
                "file2.txt", "content2",
                "file3.txt", "content3");

        FileUtil.extractZipTo(zip, outDir.getAbsolutePath());

        assertTrue(new File(outDir, "file1.txt").exists());
        assertTrue(new File(outDir, "file2.txt").exists());
        assertTrue(new File(outDir, "file3.txt").exists());
    }

    @Test
    public void extractZipTo_nestedFileInSubdir_extractedToSubdir() throws IOException {
        File outDir = tempFolder.newFolder("out");
        ZipInputStream zip = buildZip("subdir/nested.txt", "nested content");

        FileUtil.extractZipTo(zip, outDir.getAbsolutePath());

        File extracted = new File(outDir, "subdir/nested.txt");
        assertTrue("Nested file should be extracted into subdirectory", extracted.exists());
    }

    @Test
    public void extractZipTo_outputDirDoesNotExist_createsItAndExtracts() throws IOException {
        File outDir = new File(tempFolder.getRoot(), "nonexistent/nested");
        assertFalse(outDir.exists());

        ZipInputStream zip = buildZip("file.txt", "data");
        FileUtil.extractZipTo(zip, outDir.getAbsolutePath());

        assertTrue(outDir.exists());
        assertTrue(new File(outDir, "file.txt").exists());
    }

    @Test
    public void extractZipTo_emptyZip_noFilesExtracted() throws IOException {
        File outDir = tempFolder.newFolder("out");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        new ZipOutputStream(bos).close();
        ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bos.toByteArray()));

        FileUtil.extractZipTo(zip, outDir.getAbsolutePath());

        String[] files = outDir.list();
        assertEquals("Output directory should remain empty", 0, files == null ? 0 : files.length);
    }

    // -----------------------------------------------------------------------
    // Directory entries inside zip
    // -----------------------------------------------------------------------

    @Test
    public void extractZipTo_directoryEntry_doesNotCreateFile() throws IOException {
        File outDir = tempFolder.newFolder("out");
        ZipInputStream zip = buildZipWithDirEntry("mydir");

        FileUtil.extractZipTo(zip, outDir.getAbsolutePath());

        // The directory should not be created as a file
        File potentialFile = new File(outDir, "mydir");
        // It either doesn't exist, or is a directory (not a file)
        if (potentialFile.exists()) {
            assertFalse("Directory entries must not produce regular files", potentialFile.isFile());
        }
    }

    // -----------------------------------------------------------------------
    // Zip-slip security: path traversal attempts must throw SecurityException
    // -----------------------------------------------------------------------

    @Test(expected = SecurityException.class)
    public void extractZipTo_dotDotPathTraversal_throwsSecurityException() throws IOException {
        File outDir = tempFolder.newFolder("out");
        ZipInputStream zip = buildZip("../evil.txt", "should not be written");

        FileUtil.extractZipTo(zip, outDir.getAbsolutePath());
    }

    @Test(expected = SecurityException.class)
    public void extractZipTo_nestedDotDotTraversal_throwsSecurityException() throws IOException {
        File outDir = tempFolder.newFolder("out");
        // safe/../../evil would resolve outside the output directory
        ZipInputStream zip = buildZip("safe/../../evil.txt", "malicious");

        FileUtil.extractZipTo(zip, outDir.getAbsolutePath());
    }

    @Test(expected = SecurityException.class)
    public void extractZipTo_absolutePathEntry_throwsSecurityException() throws IOException {
        File outDir = tempFolder.newFolder("out");
        // An absolute path entry (some zip files contain these) should be blocked
        ZipInputStream zip = buildZip("/etc/passwd", "blocked");

        FileUtil.extractZipTo(zip, outDir.getAbsolutePath());
    }

    @Test
    public void extractZipTo_safeSiblingName_allowedThroughFilter() throws IOException {
        // "classes2.dex" starts with "classes" and ends with ".dex" – must not be blocked
        File outDir = tempFolder.newFolder("out");
        ZipInputStream zip = buildZip("classes2.dex", "dexcontent");

        FileUtil.extractZipTo(zip, outDir.getAbsolutePath());

        assertTrue(new File(outDir, "classes2.dex").exists());
    }

    @Test
    public void extractZipTo_afterTraversalBlocked_evildFileNotWrittenOutsideDir() throws IOException {
        File outDir = tempFolder.newFolder("sandbox");
        File evilTarget = new File(outDir.getParentFile(), "evil.txt");
        ZipInputStream zip = buildZip("../evil.txt", "pwned");

        try {
            FileUtil.extractZipTo(zip, outDir.getAbsolutePath());
            fail("Expected SecurityException for path traversal");
        } catch (SecurityException e) {
            // expected
        }

        assertFalse("Traversal target must not have been written", evilTarget.exists());
    }

    // -----------------------------------------------------------------------
    // writeBytes – new resource-management via try-with-resources
    // -----------------------------------------------------------------------

    @Test
    public void writeBytes_writesDataToExistingFile() throws IOException {
        File target = tempFolder.newFile("output.bin");
        byte[] data = {0x01, 0x02, 0x03, (byte) 0xFF};

        FileUtil.writeBytes(target, data);

        byte[] read = java.nio.file.Files.readAllBytes(target.toPath());
        assertArrayEquals(data, read);
    }

    @Test
    public void writeBytes_createsParentDirectoriesIfAbsent() throws IOException {
        File target = new File(tempFolder.getRoot(), "a/b/c/file.bin");
        assertFalse(target.getParentFile().exists());

        FileUtil.writeBytes(target, new byte[]{42});

        assertTrue("Parent directories should have been created", target.getParentFile().exists());
        assertTrue("File should have been created", target.exists());
    }

    @Test
    public void writeBytes_emptyByteArray_createsEmptyFile() throws IOException {
        File target = tempFolder.newFile("empty.bin");
        FileUtil.writeBytes(target, new byte[0]);
        assertEquals(0, target.length());
    }

    @Test
    public void writeBytes_overwritesExistingContent() throws IOException {
        File target = tempFolder.newFile("overwrite.bin");
        FileUtil.writeBytes(target, "original".getBytes(StandardCharsets.UTF_8));
        FileUtil.writeBytes(target, "replaced".getBytes(StandardCharsets.UTF_8));

        byte[] read = java.nio.file.Files.readAllBytes(target.toPath());
        assertEquals("replaced", new String(read, StandardCharsets.UTF_8));
    }

    // -----------------------------------------------------------------------
    // Regression: zip entry named exactly the same as out-dir basename
    // -----------------------------------------------------------------------

    @Test
    public void extractZipTo_entryNamedSameAsOutDir_extractedSafely() throws IOException {
        File outDir = tempFolder.newFolder("sandbox");
        // A file named "sandbox" inside the zip should land inside the output directory
        ZipInputStream zip = buildZip("sandbox", "contents");

        FileUtil.extractZipTo(zip, outDir.getAbsolutePath());

        // The extracted file should be inside outDir, not replace outDir itself
        assertTrue(new File(outDir, "sandbox").isFile());
    }
}
