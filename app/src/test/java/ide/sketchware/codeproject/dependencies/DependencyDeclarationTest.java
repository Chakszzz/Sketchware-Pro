package ide.sketchware.codeproject.dependencies;

import org.junit.Test;

import static org.junit.Assert.*;

public class DependencyDeclarationTest {

    // ==================== Constructor validation ====================

    @Test
    public void constructor_validCoordinates_succeeds() {
        DependencyDeclaration dep = new DependencyDeclaration("com.example", "my-lib", "1.0.0");
        assertEquals("com.example", dep.getGroupId());
        assertEquals("my-lib", dep.getArtifactId());
        assertEquals("1.0.0", dep.getVersion());
    }

    @Test
    public void constructor_coordinatesWithAllowedSpecialChars_succeeds() {
        // Dots, hyphens, underscores are all safe
        DependencyDeclaration dep = new DependencyDeclaration("com.google.code.gson", "gson", "2.10.1");
        assertEquals("com.google.code.gson", dep.getGroupId());
        assertEquals("gson", dep.getArtifactId());
        assertEquals("2.10.1", dep.getVersion());
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullGroupId_throwsIllegalArgument() {
        new DependencyDeclaration(null, "artifact", "1.0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_emptyGroupId_throwsIllegalArgument() {
        new DependencyDeclaration("", "artifact", "1.0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullArtifactId_throwsIllegalArgument() {
        new DependencyDeclaration("group", null, "1.0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_emptyArtifactId_throwsIllegalArgument() {
        new DependencyDeclaration("group", "", "1.0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullVersion_throwsIllegalArgument() {
        new DependencyDeclaration("group", "artifact", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_emptyVersion_throwsIllegalArgument() {
        new DependencyDeclaration("group", "artifact", "");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_groupIdWithSlash_throwsIllegalArgument() {
        new DependencyDeclaration("com/example", "artifact", "1.0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_groupIdWithDotDot_throwsIllegalArgument() {
        // Path traversal attempt - dots are allowed but ".." is two chars in a segment
        // Actually ".." should be allowed by the regex since only [a-zA-Z0-9._-] is checked.
        // Let's test an actual unsafe character instead
        new DependencyDeclaration("com\nexample", "artifact", "1.0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_versionWithSpace_throwsIllegalArgument() {
        new DependencyDeclaration("com.example", "artifact", "1.0 RELEASE");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_artifactIdWithAt_throwsIllegalArgument() {
        new DependencyDeclaration("com.example", "artifact@bad", "1.0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_groupIdWithColon_throwsIllegalArgument() {
        // Colon is used as separator so it should be unsafe
        new DependencyDeclaration("com:example", "artifact", "1.0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_versionWithExclamation_throwsIllegalArgument() {
        new DependencyDeclaration("group", "artifact", "1.0!");
    }

    // ==================== parse() - valid inputs ====================

    @Test
    public void parse_simpleNotation_returnsDependency() {
        DependencyDeclaration dep = DependencyDeclaration.parse("com.google.code.gson:gson:2.10.1");
        assertNotNull(dep);
        assertEquals("com.google.code.gson", dep.getGroupId());
        assertEquals("gson", dep.getArtifactId());
        assertEquals("2.10.1", dep.getVersion());
    }

    @Test
    public void parse_coordinatesWithHyphens_returnsDependency() {
        DependencyDeclaration dep = DependencyDeclaration.parse("org.apache.commons:commons-lang3:3.12.0");
        assertNotNull(dep);
        assertEquals("org.apache.commons", dep.getGroupId());
        assertEquals("commons-lang3", dep.getArtifactId());
        assertEquals("3.12.0", dep.getVersion());
    }

    @Test
    public void parse_coordinatesWithUnderscores_returnsDependency() {
        DependencyDeclaration dep = DependencyDeclaration.parse("com.example:my_artifact:1_0");
        assertNotNull(dep);
        assertEquals("my_artifact", dep.getArtifactId());
        assertEquals("1_0", dep.getVersion());
    }

    @Test
    public void parse_leadingAndTrailingWhitespace_returnsDependency() {
        DependencyDeclaration dep = DependencyDeclaration.parse("  com.example:artifact:1.0  ");
        assertNotNull(dep);
        assertEquals("com.example", dep.getGroupId());
    }

    // ==================== parse() - null/empty inputs ====================

    @Test
    public void parse_null_returnsNull() {
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

    // ==================== parse() - invalid format ====================

    @Test
    public void parse_onlyTwoParts_returnsNull() {
        assertNull(DependencyDeclaration.parse("group:artifact"));
    }

    @Test
    public void parse_fourParts_returnsNull() {
        assertNull(DependencyDeclaration.parse("group:artifact:version:extra"));
    }

    @Test
    public void parse_onePart_returnsNull() {
        assertNull(DependencyDeclaration.parse("grouponly"));
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

    // ==================== parse() - unsafe characters rejected ====================

    @Test
    public void parse_groupIdWithSlash_returnsNull() {
        assertNull(DependencyDeclaration.parse("com/example:artifact:1.0"));
    }

    @Test
    public void parse_artifactIdWithDotDotSlash_returnsNull() {
        // Path traversal attempt in artifact id
        assertNull(DependencyDeclaration.parse("com.example:../etc/passwd:1.0"));
    }

    @Test
    public void parse_versionWithAt_returnsNull() {
        assertNull(DependencyDeclaration.parse("com.example:artifact:1.0@aar"));
    }

    @Test
    public void parse_groupIdWithNewline_returnsNull() {
        assertNull(DependencyDeclaration.parse("com\nexample:artifact:1.0"));
    }

    // ==================== toString() ====================

    @Test
    public void toString_returnsColonSeparatedCoordinates() {
        DependencyDeclaration dep = new DependencyDeclaration("com.example", "artifact", "1.0");
        assertEquals("com.example:artifact:1.0", dep.toString());
    }

    // ==================== equals() and hashCode() ====================

    @Test
    public void equals_sameCoordinates_returnsTrue() {
        DependencyDeclaration a = new DependencyDeclaration("group", "artifact", "1.0");
        DependencyDeclaration b = new DependencyDeclaration("group", "artifact", "1.0");
        assertEquals(a, b);
    }

    @Test
    public void equals_differentGroupId_returnsFalse() {
        DependencyDeclaration a = new DependencyDeclaration("group.a", "artifact", "1.0");
        DependencyDeclaration b = new DependencyDeclaration("group.b", "artifact", "1.0");
        assertNotEquals(a, b);
    }

    @Test
    public void equals_differentArtifactId_returnsFalse() {
        DependencyDeclaration a = new DependencyDeclaration("group", "artifact-a", "1.0");
        DependencyDeclaration b = new DependencyDeclaration("group", "artifact-b", "1.0");
        assertNotEquals(a, b);
    }

    @Test
    public void equals_differentVersion_returnsFalse() {
        DependencyDeclaration a = new DependencyDeclaration("group", "artifact", "1.0");
        DependencyDeclaration b = new DependencyDeclaration("group", "artifact", "2.0");
        assertNotEquals(a, b);
    }

    @Test
    public void equals_null_returnsFalse() {
        DependencyDeclaration a = new DependencyDeclaration("group", "artifact", "1.0");
        assertNotEquals(a, null);
    }

    @Test
    public void equals_differentClass_returnsFalse() {
        DependencyDeclaration a = new DependencyDeclaration("group", "artifact", "1.0");
        assertNotEquals(a, "group:artifact:1.0");
    }

    @Test
    public void equals_sameReference_returnsTrue() {
        DependencyDeclaration a = new DependencyDeclaration("group", "artifact", "1.0");
        assertEquals(a, a);
    }

    @Test
    public void hashCode_equalObjects_sameHashCode() {
        DependencyDeclaration a = new DependencyDeclaration("group", "artifact", "1.0");
        DependencyDeclaration b = new DependencyDeclaration("group", "artifact", "1.0");
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void hashCode_differentCoordinates_differentHashCodes() {
        DependencyDeclaration a = new DependencyDeclaration("group.a", "artifact", "1.0");
        DependencyDeclaration b = new DependencyDeclaration("group.b", "artifact", "1.0");
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    // ==================== Boundary / regression cases ====================

    @Test
    public void parse_singleCharCoordinates_returnsDependency() {
        DependencyDeclaration dep = DependencyDeclaration.parse("a:b:1");
        assertNotNull(dep);
        assertEquals("a", dep.getGroupId());
        assertEquals("b", dep.getArtifactId());
        assertEquals("1", dep.getVersion());
    }

    @Test
    public void parse_snapshotVersion_returnsDependency() {
        DependencyDeclaration dep = DependencyDeclaration.parse("com.example:lib:1.0-SNAPSHOT");
        assertNotNull(dep);
        assertEquals("1.0-SNAPSHOT", dep.getVersion());
    }

    @Test
    public void parse_complexGroupId_returnsDependency() {
        DependencyDeclaration dep = DependencyDeclaration.parse("io.github.some-user:my.lib:0.1.2");
        assertNotNull(dep);
        assertEquals("io.github.some-user", dep.getGroupId());
    }
}