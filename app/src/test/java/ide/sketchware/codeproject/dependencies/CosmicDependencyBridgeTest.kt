package ide.sketchware.codeproject.dependencies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CosmicDependencyBridgeTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testCompareReleaseVsBeta() {
        assertTrue(CosmicDependencyBridge.compareVersions("1.0.0", "1.0.0-beta") > 0)
    }

    @Test
    fun testCompareBetaVsRelease() {
        assertTrue(CosmicDependencyBridge.compareVersions("1.0.0-beta", "1.0.0") < 0)
    }

    @Test
    fun testCompareReleaseVsSnapshot() {
        assertTrue(CosmicDependencyBridge.compareVersions("1.0.0", "1.0.0-SNAPSHOT") > 0)
    }

    @Test
    fun testCompareSnapshotVsRelease() {
        assertTrue(CosmicDependencyBridge.compareVersions("1.0.0-SNAPSHOT", "1.0.0") < 0)
    }

    @Test
    fun testCompareShortVsLongNumeric() {
        assertTrue(CosmicDependencyBridge.compareVersions("1.0", "1.0.1") < 0)
    }

    @Test
    fun testCompareLongVsShortNumeric() {
        assertTrue(CosmicDependencyBridge.compareVersions("1.0.1", "1.0") > 0)
    }

    @Test
    fun testCompareEqualVersions() {
        assertEquals(0, CosmicDependencyBridge.compareVersions("1.0.0", "1.0.0"))
    }

    @Test
    fun testCompareBetaVsAlpha() {
        assertTrue(CosmicDependencyBridge.compareVersions("1.0.0-beta", "1.0.0-alpha") > 0)
    }

    @Test
    fun testCompareBetaVsBetaDotOne() {
        assertTrue(CosmicDependencyBridge.compareVersions("1.0.0-beta", "1.0.0-beta.1") < 0)
    }

    @Test
    fun testExtractAarArtifactExtractsResourcesAssetsJniManifestAndBlocksZipSlip() {
        val aarFile = tempFolder.newFile("sample.aar")
        ZipOutputStream(FileOutputStream(aarFile)).use { zos ->
            fun add(name: String, text: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(text.toByteArray())
                zos.closeEntry()
            }
            add("classes.jar", "jar-bytes")
            add("AndroidManifest.xml", "<manifest package=\"com.example.lib\"/>")
            add("proguard.txt", "-keep class com.example.lib.** { *; }")
            add("res/values/values.xml", "<resources><string name=\"lib_name\">Lib</string></resources>")
            add("assets/sample.txt", "asset-data")
            add("jni/arm64-v8a/libsample.so", "elf-bytes")
            add("res/../../escaped.txt", "evil")
        }

        val outJar = File(tempFolder.newFolder("resolved"), "sample.jar")
        val aarExtractDir = File(tempFolder.newFolder("resolved_aar"), "sample")

        val jar = CosmicDependencyBridge.extractAarArtifact(aarFile, outJar, aarExtractDir)
        assertNotNull(jar)
        assertTrue(outJar.isFile)
        assertTrue(File(aarExtractDir, "AndroidManifest.xml").isFile)
        assertTrue(File(aarExtractDir, "proguard.txt").isFile)
        assertTrue(File(aarExtractDir, "res/values/values.xml").isFile)
        assertTrue(File(aarExtractDir, "assets/sample.txt").isFile)
        assertTrue(File(aarExtractDir, "jni/arm64-v8a/libsample.so").isFile)
        assertFalse(File(aarExtractDir.parentFile, "escaped.txt").exists())
    }
}
