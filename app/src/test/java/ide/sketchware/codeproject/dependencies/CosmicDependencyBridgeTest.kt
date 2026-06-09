package ide.sketchware.codeproject.dependencies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CosmicDependencyBridgeTest {

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
}
