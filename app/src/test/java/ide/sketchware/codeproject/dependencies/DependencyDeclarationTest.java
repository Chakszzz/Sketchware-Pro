package ide.sketchware.codeproject.dependencies;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link DependencyDeclaration}.
 *
 * Covers constructor validation, {@link DependencyDeclaration#parse(String)},
 * and {@code equals}/{@code hashCode}/{@code toString} contracts.
 */
public class DependencyDeclarationTest {

    // ======================================================================
    // Constructor – valid inputs
    // ======================================================================

    @Test
    public void constructor_validCoordinates_storesValues() {
        DependencyDeclaration dep = new DependencyDeclaration(
                "com.example", "my-library", "1.0.0");
        assertEquals("com.example", dep.getGroupId());
        assertEquals("my-library", dep.getArtifactId());
        assertEquals("1.0.0", dep.getVersion());
    }

    @Test
    public void constructor_coordinatesWithDots_accepted() {
        DependencyDeclaration dep = new DependencyDeclaration(
                "com.google.code.gson", "gson", "2.10.1");
        assertEquals("com.google.code.gson", dep.getGroupId());
    }

    @Test
    public void constructor_coordinatesWithHyphens_accepted() {
        DependencyDeclaration dep = new DependencyDeclaration(
                "org.jetbrains.kotlin", "kotlin-stdlib", "1.9.0");
        assertEquals("kotlin-stdlib", dep.getArtifactId());
    }

    @Test
    public void constructor_coordinatesWithUnderscores_accepted() {
        DependencyDeclaration dep = new DependencyDeclaration(
                "com.example_corp", "my_lib", "1_0");
        assertEquals("com.example_corp", dep.getGroupId());
    }

    @Test
    public void constructor_alphanumericOnly_accepted() {
        DependencyDeclaration dep = new DependencyDeclaration("org", "lib", "100");
        assertEquals("org", dep.getGroupId());
        assertEquals("lib", dep.getArtifactId());
        assertEquals("100", dep.getVersion());
    }

    // ======================================================================
    // Constructor – invalid inputs
    // ======================================================================

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullGroupId_throws() {
        new DependencyDeclaration(null, "artifact", "1.0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_emptyGroupId_throws() {
        new DependencyDeclaration("", "artifact", "1.0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullArtifactId_throws() {
        new DependencyDeclaration("group", null, "1.0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_emptyArtifactId_throws() {
        new DependencyDeclaration("group", "", "1.0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullVersion_throws() {
        new DependencyDeclaration("group", "artifact", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_emptyVersion_throws() {
        new DependencyDeclaration("group", "artifact", "");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_groupIdWithSlash_throwsPathTraversalGuard() {
        new DependencyDeclaration("com/../evil", "artifact", "1.0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_groupIdWithBackslash_throws() {
        new DependencyDeclaration("com\\evil", "artifact", "1.0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_versionWithSpaces_throws() {
        new DependencyDeclaration("group", "artifact", "1.0 SNAPSHOT");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_groupIdWithAtSign_throws() {
        new DependencyDeclaration("@evil", "artifact", "1.0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_artifactIdWithColon_throws() {
        // Colons in a coordinate would break split-based parsing downstream
        new DependencyDeclaration("group", "art:ifact", "1.0");
    }

    // ======================================================================
    // parse() – valid notations
    // ======================================================================

    @Test
    public void parse_validNotation_returnsCorrectFields() {
        DependencyDeclaration dep = DependencyDeclaration.parse("com.example:library:1.0.0");
        assertNotNull(dep);
        assertEquals("com.example", dep.getGroupId());
        assertEquals("library", dep.getArtifactId());
        assertEquals("1.0.0", dep.getVersion());
    }

    @Test
    public void parse_notationWithLeadingWhitespace_trimmed() {
        DependencyDeclaration dep = DependencyDeclaration.parse("  com.example:lib:2.3  ");
        assertNotNull(dep);
        assertEquals("com.example", dep.getGroupId());
        assertEquals("2.3", dep.getVersion());
    }

    @Test
    public void parse_gsonNotation_parsedCorrectly() {
        DependencyDeclaration dep = DependencyDeclaration.parse("com.google.code.gson:gson:2.10.1");
        assertNotNull(dep);
        assertEquals("com.google.code.gson", dep.getGroupId());
        assertEquals("gson", dep.getArtifactId());
        assertEquals("2.10.1", dep.getVersion());
    }

    @Test
    public void parse_hyphenatedArtifact_parsedCorrectly() {
        DependencyDeclaration dep = DependencyDeclaration.parse("androidx.core:core-ktx:1.12.0");
        assertNotNull(dep);
        assertEquals("core-ktx", dep.getArtifactId());
    }

    // ======================================================================
    // parse() – null and empty inputs
    // ======================================================================

    @Test
    public void parse_nullNotation_returnsNull() {
        assertNull(DependencyDeclaration.parse(null));
    }

    @Test
    public void parse_emptyString_returnsNull() {
        assertNull(DependencyDeclaration.parse(""));
    }

    @Test
    public void parse_whitespaceOnly_returnsNull() {
        assertNull(DependencyDeclaration.parse("   "));
    }

    // ======================================================================
    // parse() – malformed notations
    // ======================================================================

    @Test
    public void parse_onlyTwoParts_returnsNull() {
        assertNull(DependencyDeclaration.parse("com.example:library"));
    }

    @Test
    public void parse_fourParts_returnsNull() {
        // "groupId:artifactId:version:classifier" is not supported
        assertNull(DependencyDeclaration.parse("com.example:lib:1.0:sources"));
    }

    @Test
    public void parse_emptyGroupId_returnsNull() {
        assertNull(DependencyDeclaration.parse(":artifact:1.0"));
    }

    @Test
    public void parse_emptyArtifactId_returnsNull() {
        assertNull(DependencyDeclaration.parse("group::1.0"));
    }

    @Test
    public void parse_emptyVersion_returnsNull() {
        assertNull(DependencyDeclaration.parse("group:artifact:"));
    }

    @Test
    public void parse_unsafeGroupIdWithSlash_returnsNull() {
        assertNull(DependencyDeclaration.parse("../evil:artifact:1.0"));
    }

    @Test
    public void parse_unsafeVersionWithSpaces_returnsNull() {
        assertNull(DependencyDeclaration.parse("group:artifact:1.0 SNAPSHOT"));
    }

    @Test
    public void parse_notationWithNewlineInVersion_returnsNull() {
        assertNull(DependencyDeclaration.parse("group:artifact:1.0\n2.0"));
    }

    @Test
    public void parse_plainTextNoColons_returnsNull() {
        assertNull(DependencyDeclaration.parse("justaplainword"));
    }

    // ======================================================================
    // toString()
    // ======================================================================

    @Test
    public void toString_formatsAsColonSeparated() {
        DependencyDeclaration dep = new DependencyDeclaration("com.example", "lib", "1.2.3");
        assertEquals("com.example:lib:1.2.3", dep.toString());
    }

    // ======================================================================
    // equals() and hashCode()
    // ======================================================================

    @Test
    public void equals_sameCoordinates_returnsTrue() {
        DependencyDeclaration a = new DependencyDeclaration("com.example", "lib", "1.0");
        DependencyDeclaration b = new DependencyDeclaration("com.example", "lib", "1.0");
        assertEquals(a, b);
    }

    @Test
    public void equals_differentVersion_returnsFalse() {
        DependencyDeclaration a = new DependencyDeclaration("com.example", "lib", "1.0");
        DependencyDeclaration b = new DependencyDeclaration("com.example", "lib", "2.0");
        assertNotEquals(a, b);
    }

    @Test
    public void equals_differentGroupId_returnsFalse() {
        DependencyDeclaration a = new DependencyDeclaration("com.example", "lib", "1.0");
        DependencyDeclaration b = new DependencyDeclaration("org.example", "lib", "1.0");
        assertNotEquals(a, b);
    }

    @Test
    public void equals_differentArtifactId_returnsFalse() {
        DependencyDeclaration a = new DependencyDeclaration("com.example", "libA", "1.0");
        DependencyDeclaration b = new DependencyDeclaration("com.example", "libB", "1.0");
        assertNotEquals(a, b);
    }

    @Test
    public void equals_reflexive_returnsTrueForSameInstance() {
        DependencyDeclaration dep = new DependencyDeclaration("g", "a", "v");
        assertEquals(dep, dep);
    }

    @Test
    public void equals_nullComparison_returnsFalse() {
        DependencyDeclaration dep = new DependencyDeclaration("g", "a", "v");
        assertNotEquals(dep, null);
    }

    @Test
    public void equals_differentType_returnsFalse() {
        DependencyDeclaration dep = new DependencyDeclaration("g", "a", "v");
        assertNotEquals(dep, "g:a:v");
    }

    @Test
    public void hashCode_equalObjects_sameHashCode() {
        DependencyDeclaration a = new DependencyDeclaration("com.example", "lib", "1.0");
        DependencyDeclaration b = new DependencyDeclaration("com.example", "lib", "1.0");
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void hashCode_differentVersions_differentHashCode() {
        DependencyDeclaration a = new DependencyDeclaration("com.example", "lib", "1.0");
        DependencyDeclaration b = new DependencyDeclaration("com.example", "lib", "2.0");
        // Technically not guaranteed, but practically different strings should yield different hashes
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    // ======================================================================
    // Regression: boundary characters at the edge of the safe whitelist
    // ======================================================================

    @Test
    public void constructor_versionWithDotDashNumbers_accepted() {
        // Versions like "1.0-SNAPSHOT" or "2.0-rc1" are common and must be allowed
        DependencyDeclaration dep = new DependencyDeclaration("g", "a", "2.0-rc1");
        assertEquals("2.0-rc1", dep.getVersion());
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_groupIdWithExclamationMark_throws() {
        new DependencyDeclaration("com!evil", "artifact", "1.0");
    }

    @Test
    public void parse_versionWithDashSnapshot_parsedCorrectly() {
        DependencyDeclaration dep = DependencyDeclaration.parse("com.example:lib:1.0-SNAPSHOT");
        assertNotNull(dep);
        assertEquals("1.0-SNAPSHOT", dep.getVersion());
    }
}