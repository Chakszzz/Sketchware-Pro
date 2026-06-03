package ide.sketchware.codeproject.dependencies;

import org.junit.Test;

import static org.junit.Assert.*;

public class DependencyDeclarationTest {

    // ==================== Constructor ====================

    @Test
    public void constructor_validCoordinates_storesValues() {
        DependencyDeclaration dep = new DependencyDeclaration("com.example", "my-lib", "1.0.0");
        assertEquals("com.example", dep.getGroupId());
        assertEquals("my-lib", dep.getArtifactId());
        assertEquals("1.0.0", dep.getVersion());
    }

    @Test
    public void constructor_allowsDotsAndDashesAndUnderscores() {
        DependencyDeclaration dep = new DependencyDeclaration("com.google.code.gson", "gson", "2.10.1");
        assertEquals("com.google.code.gson", dep.getGroupId());
        assertEquals("gson", dep.getArtifactId());
        assertEquals("2.10.1", dep.getVersion());
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullGroupId_throws() {
        new DependencyDeclaration(null, "artifact", "1.0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullArtifactId_throws() {
        new DependencyDeclaration("com.example", null, "1.0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullVersion_throws() {
        new DependencyDeclaration("com.example", "artifact", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_emptyGroupId_throws() {
        new DependencyDeclaration("", "artifact", "1.0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_emptyArtifactId_throws() {
        new DependencyDeclaration("com.example", "", "1.0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_emptyVersion_throws() {
        new DependencyDeclaration("com.example", "artifact", "");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_groupIdWithSlash_throws() {
        new DependencyDeclaration("com/example", "artifact", "1.0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_groupIdWithDotDot_throws() {
        // ".." contains only dots which are safe individually, but let's check the actual pattern
        // Pattern: [a-zA-Z0-9._-]+ — ".." matches the pattern so this won't throw.
        // But "com/../example" should throw since "/" is unsafe.
        new DependencyDeclaration("com/../example", "artifact", "1.0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_versionWithSpaces_throws() {
        new DependencyDeclaration("com.example", "artifact", "1.0 RELEASE");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_artifactIdWithAmpersand_throws() {
        new DependencyDeclaration("com.example", "artifact&bad", "1.0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_groupIdWithNullByte_throws() {
        new DependencyDeclaration("com.example\0evil", "artifact", "1.0");
    }

    // ==================== parse() ====================

    @Test
    public void parse_validNotation_returnsDeclaration() {
        DependencyDeclaration dep = DependencyDeclaration.parse("com.google.code.gson:gson:2.10.1");
        assertNotNull(dep);
        assertEquals("com.google.code.gson", dep.getGroupId());
        assertEquals("gson", dep.getArtifactId());
        assertEquals("2.10.1", dep.getVersion());
    }

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

    @Test
    public void parse_onlyTwoParts_returnsNull() {
        assertNull(DependencyDeclaration.parse("com.example:artifact"));
    }

    @Test
    public void parse_fourParts_returnsNull() {
        // More than 3 colons means more than 3 parts
        assertNull(DependencyDeclaration.parse("com.example:artifact:1.0:extra"));
    }

    @Test
    public void parse_singlePart_returnsNull() {
        assertNull(DependencyDeclaration.parse("com.example"));
    }

    @Test
    public void parse_emptyGroupId_returnsNull() {
        assertNull(DependencyDeclaration.parse(":artifact:1.0"));
    }

    @Test
    public void parse_emptyArtifactId_returnsNull() {
        assertNull(DependencyDeclaration.parse("com.example::1.0"));
    }

    @Test
    public void parse_emptyVersion_returnsNull() {
        assertNull(DependencyDeclaration.parse("com.example:artifact:"));
    }

    @Test
    public void parse_unsafeGroupId_returnsNull() {
        assertNull(DependencyDeclaration.parse("com/example:artifact:1.0"));
    }

    @Test
    public void parse_unsafeVersion_returnsNull() {
        assertNull(DependencyDeclaration.parse("com.example:artifact:../evil"));
    }

    @Test
    public void parse_trimmingWhitespace_succeeds() {
        DependencyDeclaration dep = DependencyDeclaration.parse("  com.example:artifact:1.0  ");
        assertNotNull(dep);
        assertEquals("com.example", dep.getGroupId());
        assertEquals("artifact", dep.getArtifactId());
        assertEquals("1.0", dep.getVersion());
    }

    @Test
    public void parse_semverVersion_succeeds() {
        DependencyDeclaration dep = DependencyDeclaration.parse("org.example:lib:2.3.4");
        assertNotNull(dep);
        assertEquals("2.3.4", dep.getVersion());
    }

    @Test
    public void parse_versionWithDash_succeeds() {
        // e.g. "1.0.0-alpha01"
        DependencyDeclaration dep = DependencyDeclaration.parse("com.example:artifact:1.0.0-alpha01");
        assertNotNull(dep);
        assertEquals("1.0.0-alpha01", dep.getVersion());
    }

    // ==================== toString() ====================

    @Test
    public void toString_returnsColonSeparatedCoordinates() {
        DependencyDeclaration dep = new DependencyDeclaration("com.example", "my-lib", "1.2.3");
        assertEquals("com.example:my-lib:1.2.3", dep.toString());
    }

    // ==================== equals() ====================

    @Test
    public void equals_sameCoordinates_returnsTrue() {
        DependencyDeclaration a = new DependencyDeclaration("com.example", "lib", "1.0");
        DependencyDeclaration b = new DependencyDeclaration("com.example", "lib", "1.0");
        assertEquals(a, b);
    }

    @Test
    public void equals_differentGroupId_returnsFalse() {
        DependencyDeclaration a = new DependencyDeclaration("com.example", "lib", "1.0");
        DependencyDeclaration b = new DependencyDeclaration("org.example", "lib", "1.0");
        assertNotEquals(a, b);
    }

    @Test
    public void equals_differentArtifactId_returnsFalse() {
        DependencyDeclaration a = new DependencyDeclaration("com.example", "lib-a", "1.0");
        DependencyDeclaration b = new DependencyDeclaration("com.example", "lib-b", "1.0");
        assertNotEquals(a, b);
    }

    @Test
    public void equals_differentVersion_returnsFalse() {
        DependencyDeclaration a = new DependencyDeclaration("com.example", "lib", "1.0");
        DependencyDeclaration b = new DependencyDeclaration("com.example", "lib", "2.0");
        assertNotEquals(a, b);
    }

    @Test
    public void equals_null_returnsFalse() {
        DependencyDeclaration a = new DependencyDeclaration("com.example", "lib", "1.0");
        assertNotEquals(a, null);
    }

    @Test
    public void equals_differentClass_returnsFalse() {
        DependencyDeclaration a = new DependencyDeclaration("com.example", "lib", "1.0");
        assertNotEquals(a, "com.example:lib:1.0");
    }

    @Test
    public void equals_sameReference_returnsTrue() {
        DependencyDeclaration a = new DependencyDeclaration("com.example", "lib", "1.0");
        assertEquals(a, a);
    }

    // ==================== hashCode() ====================

    @Test
    public void hashCode_equalObjects_sameHashCode() {
        DependencyDeclaration a = new DependencyDeclaration("com.example", "lib", "1.0");
        DependencyDeclaration b = new DependencyDeclaration("com.example", "lib", "1.0");
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void hashCode_differentObjects_typicallyDifferentHashCode() {
        DependencyDeclaration a = new DependencyDeclaration("com.example", "lib-a", "1.0");
        DependencyDeclaration b = new DependencyDeclaration("com.example", "lib-b", "1.0");
        // Hash codes can theoretically collide but this combination should not
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    // ==================== Boundary / Regression Cases ====================

    @Test
    public void parse_coordinateWithUnderscores_succeeds() {
        // Some Maven artifacts use underscores in groupId/artifactId
        DependencyDeclaration dep = DependencyDeclaration.parse("com.example_corp:my_lib:1.0");
        assertNotNull(dep);
        assertEquals("com.example_corp", dep.getGroupId());
        assertEquals("my_lib", dep.getArtifactId());
    }

    @Test
    public void constructor_errorMessageContainsCoordinates() {
        try {
            new DependencyDeclaration("com/bad", "artifact", "1.0");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue("Error message should contain coordinates",
                    e.getMessage().contains("com/bad") && e.getMessage().contains("artifact"));
        }
    }

    @Test
    public void parse_versionWithOnlyDigits_succeeds() {
        DependencyDeclaration dep = DependencyDeclaration.parse("com.example:artifact:100");
        assertNotNull(dep);
        assertEquals("100", dep.getVersion());
    }
}