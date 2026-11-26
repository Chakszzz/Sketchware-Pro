package mod.hey.studios.project.backup;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BackupFactoryCodeProjectTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void backupAndRestoreRoundTripPreservesSourcesAndExcludesBuildArtifacts() throws Exception {
        File projectDir = tempFolder.newFolder("mysc_601");
        File mainJava = new File(projectDir, "app/src/main/java/com/example/MainActivity.java");
        File depsTxt = new File(projectDir, "app/src/main/dependencies.txt");
        File manualJar = new File(projectDir, "app/libs/manual.jar");
        File resolvedJar = new File(projectDir, "app/libs/resolved/cached.jar");
        File resolvedAar = new File(projectDir, "app/libs/resolved_aar/lib/res/values/attrs.xml");
        File binOutput = new File(projectDir, "bin/signed.apk");
        File genR = new File(projectDir, "gen/com/example/R.java");

        writeText(mainJava, "public class MainActivity {}");
        writeText(depsTxt, "implementation com.google.code.gson:gson:2.10.1\n");
        writeText(manualJar, "manual-jar-bytes");
        writeText(resolvedJar, "cached-jar-bytes");
        writeText(resolvedAar, "<resources/>");
        writeText(binOutput, "apk-bytes");
        writeText(genR, "public final class R {}");

        File stagingBackupDir = tempFolder.newFolder("staging");
        File codeProjectStage = new File(stagingBackupDir, BackupFactory.CODE_PROJECT_ENTRY_NAME);
        BackupFactory.copyCodeProjectSourcesForBackup(projectDir, codeProjectStage);

        File zipFile = new File(tempFolder.getRoot(), "project.swb");
        BackupFactory.zipFolder(stagingBackupDir, zipFile);

        File unzipDir = tempFolder.newFolder("unzipped");
        assertTrue(BackupFactory.unzip(zipFile, unzipDir));

        File restoredProjectDir = tempFolder.newFolder("mysc_602");
        BackupFactory.restoreCodeProjectSources(
                new File(unzipDir, BackupFactory.CODE_PROJECT_ENTRY_NAME),
                restoredProjectDir
        );

        File restoredMain = new File(restoredProjectDir, "app/src/main/java/com/example/MainActivity.java");
        File restoredDeps = new File(restoredProjectDir, "app/src/main/dependencies.txt");
        File restoredManualJar = new File(restoredProjectDir, "app/libs/manual.jar");

        assertTrue(restoredMain.isFile());
        assertEquals("public class MainActivity {}", readText(restoredMain));
        assertTrue(restoredDeps.isFile());
        assertTrue(restoredManualJar.isFile());

        assertFalse(new File(restoredProjectDir, "app/libs/resolved/cached.jar").exists());
        assertFalse(new File(restoredProjectDir, "app/libs/resolved_aar").exists());
        assertFalse(new File(restoredProjectDir, "bin/signed.apk").exists());
        assertFalse(new File(restoredProjectDir, "gen/com/example/R.java").exists());
    }

    @Test
    public void unzipRejectsZipSlipTraversalEntry() throws Exception {
        File maliciousZip = new File(tempFolder.getRoot(), "malicious.swb");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(maliciousZip))) {
            zos.putNextEntry(new ZipEntry("../../escaped.txt"));
            zos.write("bad".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        File targetDir = tempFolder.newFolder("extract_target");
        assertFalse(BackupFactory.unzip(maliciousZip, targetDir));
        assertFalse(new File(tempFolder.getRoot(), "escaped.txt").exists());
    }

    private void writeText(File file, String content) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private String readText(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
