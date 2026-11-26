package ide.sketchware.codeproject.build;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

public class CodeProjectManifestMergerTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testMergesAarPermissionsComponentsAndResolvesPlaceholders() throws Exception {
        File appManifest = tempFolder.newFile("AndroidManifest.xml");
        String appXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"com.example.myapp\">\n"
                + "    <uses-permission android:name=\"android.permission.INTERNET\" />\n"
                + "    <application android:label=\"MyApp\">\n"
                + "        <activity android:name=\".MainActivity\" android:exported=\"true\" />\n"
                + "    </application>\n"
                + "</manifest>\n";
        Files.write(appManifest.toPath(), appXml.getBytes(StandardCharsets.UTF_8));

        File aarManifest = tempFolder.newFile("aar_AndroidManifest.xml");
        String aarXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.vendor.sdk\">\n"
                + "    <uses-permission android:name=\"android.permission.INTERNET\" />\n"
                + "    <uses-permission android:name=\"android.permission.ACCESS_NETWORK_STATE\" />\n"
                + "    <application>\n"
                + "        <provider\n"
                + "            android:name=\".InitProvider\"\n"
                + "            android:authorities=\"${applicationId}.vendor-init\"\n"
                + "            android:exported=\"false\" />\n"
                + "        <service android:name=\"com.vendor.sdk.SyncService\" android:exported=\"false\" />\n"
                + "        <meta-data android:name=\"com.vendor.sdk.VERSION\" android:value=\"2\" />\n"
                + "        <service android:name=\"com.vendor.sdk.RemovedService\" tools:node=\"remove\" />\n"
                + "    </application>\n"
                + "</manifest>\n";
        Files.write(aarManifest.toPath(), aarXml.getBytes(StandardCharsets.UTF_8));

        File mergedFile = new File(tempFolder.getRoot(), "bin/merged_AndroidManifest.xml");
        CodeProjectManifestMerger.merge(
                appManifest,
                Collections.singletonList(aarManifest),
                "com.example.myapp",
                mergedFile
        );

        assertTrue(mergedFile.isFile());
        String merged = new String(Files.readAllBytes(mergedFile.toPath()), StandardCharsets.UTF_8);

        // App permission kept once (not duplicated), AAR permission added
        assertEquals(1, countOccurrences(merged, "android.permission.INTERNET"));
        assertTrue(merged.contains("android.permission.ACCESS_NETWORK_STATE"));

        // Relative AAR component name resolved against AAR package, ${applicationId} resolved
        assertTrue(merged.contains("com.vendor.sdk.InitProvider"));
        assertTrue(merged.contains("com.example.myapp.vendor-init"));
        assertTrue(merged.contains("com.vendor.sdk.SyncService"));
        assertTrue(merged.contains("com.vendor.sdk.VERSION"));

        // tools:node="remove" skipped
        assertEquals(0, countOccurrences(merged, "RemovedService"));
    }

    private int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
