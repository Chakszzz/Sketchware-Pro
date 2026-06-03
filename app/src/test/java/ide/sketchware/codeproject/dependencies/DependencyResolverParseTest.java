package ide.sketchware.codeproject.dependencies;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link DependencyResolver#parseDependenciesFile(File)}.
 * This method is static and performs pure file I/O with no Android dependencies.
 */
public class DependencyResolverParseTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File writeFile(String content) throws IOException {
        File file = tempFolder.newFile("dependencies.txt");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
        return file;
    }

    // ==================== Null / missing file ====================

    @Test
    public void parseDependenciesFile_nullFile_returnsEmptyList() {
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void parseDependenciesFile_nonExistentFile_returnsEmptyList() {
        File nonExistent = new File(tempFolder.getRoot(), "no_such_file.txt");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(nonExistent);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== Empty / comment-only files ====================

    @Test
    public void parseDependenciesFile_emptyFile_returnsEmptyList() throws IOException {
        File file = writeFile("");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertTrue(result.isEmpty());
    }

    @Test
    public void parseDependenciesFile_onlyHashComments_returnsEmptyList() throws IOException {
        File file = writeFile("# This is a comment\n# Another comment\n");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertTrue(result.isEmpty());
    }

    @Test
    public void parseDependenciesFile_onlySlashSlashComments_returnsEmptyList() throws IOException {
        File file = writeFile("// Comment line 1\n// Comment line 2\n");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertTrue(result.isEmpty());
    }

    @Test
    public void parseDependenciesFile_onlyBlankLines_returnsEmptyList() throws IOException {
        File file = writeFile("\n\n   \n\n");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertTrue(result.isEmpty());
    }

    // ==================== Bare notation ====================

    @Test
    public void parseDependenciesFile_bareNotation_parsesSuccessfully() throws IOException {
        File file = writeFile("com.google.code.gson:gson:2.10.1\n");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertEquals(1, result.size());
        assertEquals("com.google.code.gson", result.get(0).getGroupId());
        assertEquals("gson", result.get(0).getArtifactId());
        assertEquals("2.10.1", result.get(0).getVersion());
    }

    // ==================== Keyword prefixes ====================

    @Test
    public void parseDependenciesFile_implementationPrefix_stripsPrefix() throws IOException {
        File file = writeFile("implementation com.squareup.okhttp3:okhttp:4.12.0\n");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertEquals(1, result.size());
        assertEquals("com.squareup.okhttp3", result.get(0).getGroupId());
        assertEquals("okhttp", result.get(0).getArtifactId());
        assertEquals("4.12.0", result.get(0).getVersion());
    }

    @Test
    public void parseDependenciesFile_apiPrefix_stripsPrefix() throws IOException {
        File file = writeFile("api org.example:mylib:1.0.0\n");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertEquals(1, result.size());
        assertEquals("org.example", result.get(0).getGroupId());
        assertEquals("mylib", result.get(0).getArtifactId());
        assertEquals("1.0.0", result.get(0).getVersion());
    }

    @Test
    public void parseDependenciesFile_compileOnlyPrefix_stripsPrefix() throws IOException {
        File file = writeFile("compileOnly org.example:annotations:1.0\n");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertEquals(1, result.size());
        assertEquals("org.example", result.get(0).getGroupId());
        assertEquals("annotations", result.get(0).getArtifactId());
    }

    // ==================== Quoted notation ====================

    @Test
    public void parseDependenciesFile_implementationWithSingleQuotes_stripsQuotes() throws IOException {
        File file = writeFile("implementation 'com.google.code.gson:gson:2.10.1'\n");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertEquals(1, result.size());
        assertEquals("com.google.code.gson", result.get(0).getGroupId());
        assertEquals("gson", result.get(0).getArtifactId());
        assertEquals("2.10.1", result.get(0).getVersion());
    }

    @Test
    public void parseDependenciesFile_implementationWithDoubleQuotes_stripsQuotes() throws IOException {
        File file = writeFile("implementation \"com.google.code.gson:gson:2.10.1\"\n");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertEquals(1, result.size());
        assertEquals("com.google.code.gson", result.get(0).getGroupId());
    }

    @Test
    public void parseDependenciesFile_bareWithSingleQuotes_stripsQuotes() throws IOException {
        File file = writeFile("'org.example:lib:1.0'\n");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertEquals(1, result.size());
        assertEquals("org.example", result.get(0).getGroupId());
    }

    @Test
    public void parseDependenciesFile_bareWithDoubleQuotes_stripsQuotes() throws IOException {
        File file = writeFile("\"org.example:lib:1.0\"\n");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertEquals(1, result.size());
        assertEquals("org.example", result.get(0).getGroupId());
    }

    // ==================== Invalid / skipped lines ====================

    @Test
    public void parseDependenciesFile_invalidNotation_skipsLine() throws IOException {
        File file = writeFile("not-a-valid-dep\n");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertTrue(result.isEmpty());
    }

    @Test
    public void parseDependenciesFile_twoPartNotation_skipsLine() throws IOException {
        File file = writeFile("com.example:artifact\n");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertTrue(result.isEmpty());
    }

    @Test
    public void parseDependenciesFile_unsafeCoordinates_skipsLine() throws IOException {
        File file = writeFile("com/evil:artifact:1.0\n");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertTrue(result.isEmpty());
    }

    // ==================== Mixed content ====================

    @Test
    public void parseDependenciesFile_mixedContent_parsesOnlyValidLines() throws IOException {
        String content =
                "# Comment at top\n"
                + "// Another comment\n"
                + "\n"
                + "com.google.code.gson:gson:2.10.1\n"
                + "implementation com.squareup.okhttp3:okhttp:4.12.0\n"
                + "bad-line\n"
                + "api 'org.example:lib:1.0'\n"
                + "\n"
                + "# End comment\n";
        File file = writeFile(content);
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertEquals(3, result.size());
        assertEquals("com.google.code.gson:gson:2.10.1", result.get(0).toString());
        assertEquals("com.squareup.okhttp3:okhttp:4.12.0", result.get(1).toString());
        assertEquals("org.example:lib:1.0", result.get(2).toString());
    }

    @Test
    public void parseDependenciesFile_multipleValidDeps_returnsAll() throws IOException {
        String content =
                "implementation 'com.google.code.gson:gson:2.10.1'\n"
                + "implementation 'com.squareup.okhttp3:okhttp:4.12.0'\n"
                + "implementation 'org.example:mylib:3.0'\n";
        File file = writeFile(content);
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertEquals(3, result.size());
    }

    // ==================== Template file format ====================

    @Test
    public void parseDependenciesFile_generatedTemplateFile_returnsEmptyList() throws IOException {
        // This is the exact content generated by CodeProjectTemplate.generateDependenciesFile()
        String templateContent =
                "# Add dependencies here, one per line\n"
                + "# Format: implementation groupId:artifactId:version\n"
                + "# Example: implementation com.google.code.gson:gson:2.10.1\n"
                + "#\n"
                + "# Run \"Sync Dependencies\" from the IDE menu to download\n";
        File file = writeFile(templateContent);
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        // All lines are comments, so no dependencies should be parsed
        assertTrue(result.isEmpty());
    }

    // ==================== Boundary / Regression Cases ====================

    @Test
    public void parseDependenciesFile_lineWithLeadingAndTrailingSpaces_parsesCorrectly() throws IOException {
        File file = writeFile("  com.example:artifact:1.0  \n");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertEquals(1, result.size());
        assertEquals("com.example:artifact:1.0", result.get(0).toString());
    }

    @Test
    public void parseDependenciesFile_hashCommentNotAtLineStart_skipsLine() throws IOException {
        // A line like "#comment" starting with # should be skipped
        File file = writeFile("#com.example:artifact:1.0\n");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertTrue(result.isEmpty());
    }

    @Test
    public void parseDependenciesFile_slashCommentNotAtLineStart_skipsLine() throws IOException {
        File file = writeFile("//com.example:artifact:1.0\n");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertTrue(result.isEmpty());
    }

    @Test
    public void parseDependenciesFile_noNewlineAtEnd_parsesLastLine() throws IOException {
        // File without trailing newline
        File file = writeFile("com.example:artifact:1.0");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertEquals(1, result.size());
    }

    @Test
    public void parseDependenciesFile_versionWithDash_parsesCorrectly() throws IOException {
        File file = writeFile("com.example:artifact:1.0.0-alpha01\n");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertEquals(1, result.size());
        assertEquals("1.0.0-alpha01", result.get(0).getVersion());
    }
}