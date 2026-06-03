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
 *
 * Only the static parsing logic is covered here, which has no Android dependencies.
 */
public class DependencyResolverTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    private File writeFile(String content) throws IOException {
        File file = tempFolder.newFile("dependencies.txt");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
        return file;
    }

    // ------------------------------------------------------------------
    // Null / missing file
    // ------------------------------------------------------------------

    @Test
    public void parseDependenciesFile_nullFile_returnsEmptyList() {
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void parseDependenciesFile_nonExistentFile_returnsEmptyList() {
        File nonExistent = new File(tempFolder.getRoot(), "does_not_exist.txt");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(nonExistent);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ------------------------------------------------------------------
    // Empty / comment-only files
    // ------------------------------------------------------------------

    @Test
    public void parseDependenciesFile_emptyFile_returnsEmptyList() throws IOException {
        File file = writeFile("");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertTrue(result.isEmpty());
    }

    @Test
    public void parseDependenciesFile_onlyHashComments_returnsEmptyList() throws IOException {
        File file = writeFile("# This is a comment\n# Another comment\n");
        assertTrue(DependencyResolver.parseDependenciesFile(file).isEmpty());
    }

    @Test
    public void parseDependenciesFile_onlyDoubleSlashComments_returnsEmptyList() throws IOException {
        File file = writeFile("// comment 1\n// comment 2\n");
        assertTrue(DependencyResolver.parseDependenciesFile(file).isEmpty());
    }

    @Test
    public void parseDependenciesFile_onlyBlankLines_returnsEmptyList() throws IOException {
        File file = writeFile("\n\n   \n\t\n");
        assertTrue(DependencyResolver.parseDependenciesFile(file).isEmpty());
    }

    // ------------------------------------------------------------------
    // Bare notation: "groupId:artifactId:version"
    // ------------------------------------------------------------------

    @Test
    public void parseDependenciesFile_bareNotation_parsedCorrectly() throws IOException {
        File file = writeFile("com.google.code.gson:gson:2.10.1\n");
        List<DependencyDeclaration> deps = DependencyResolver.parseDependenciesFile(file);
        assertEquals(1, deps.size());
        assertEquals("com.google.code.gson", deps.get(0).getGroupId());
        assertEquals("gson", deps.get(0).getArtifactId());
        assertEquals("2.10.1", deps.get(0).getVersion());
    }

    @Test
    public void parseDependenciesFile_multipleBareNotations_allParsed() throws IOException {
        File file = writeFile(
                "com.google.code.gson:gson:2.10.1\n"
                + "androidx.core:core-ktx:1.12.0\n"
                + "org.jetbrains.kotlin:kotlin-stdlib:1.9.0\n");
        List<DependencyDeclaration> deps = DependencyResolver.parseDependenciesFile(file);
        assertEquals(3, deps.size());
        assertEquals("gson", deps.get(0).getArtifactId());
        assertEquals("core-ktx", deps.get(1).getArtifactId());
        assertEquals("kotlin-stdlib", deps.get(2).getArtifactId());
    }

    // ------------------------------------------------------------------
    // "implementation" prefix
    // ------------------------------------------------------------------

    @Test
    public void parseDependenciesFile_implementationPrefix_parsedCorrectly() throws IOException {
        File file = writeFile("implementation com.example:mylib:1.0.0\n");
        List<DependencyDeclaration> deps = DependencyResolver.parseDependenciesFile(file);
        assertEquals(1, deps.size());
        assertEquals("com.example", deps.get(0).getGroupId());
        assertEquals("mylib", deps.get(0).getArtifactId());
        assertEquals("1.0.0", deps.get(0).getVersion());
    }

    @Test
    public void parseDependenciesFile_implementationWithSingleQuotes_parsedCorrectly() throws IOException {
        File file = writeFile("implementation 'com.example:mylib:1.0.0'\n");
        List<DependencyDeclaration> deps = DependencyResolver.parseDependenciesFile(file);
        assertEquals(1, deps.size());
        assertEquals("com.example", deps.get(0).getGroupId());
    }

    @Test
    public void parseDependenciesFile_implementationWithDoubleQuotes_parsedCorrectly() throws IOException {
        File file = writeFile("implementation \"com.example:mylib:1.0.0\"\n");
        List<DependencyDeclaration> deps = DependencyResolver.parseDependenciesFile(file);
        assertEquals(1, deps.size());
        assertEquals("mylib", deps.get(0).getArtifactId());
    }

    // ------------------------------------------------------------------
    // "api" prefix
    // ------------------------------------------------------------------

    @Test
    public void parseDependenciesFile_apiPrefix_parsedCorrectly() throws IOException {
        File file = writeFile("api com.example:apilib:2.0\n");
        List<DependencyDeclaration> deps = DependencyResolver.parseDependenciesFile(file);
        assertEquals(1, deps.size());
        assertEquals("apilib", deps.get(0).getArtifactId());
    }

    @Test
    public void parseDependenciesFile_apiWithSingleQuotes_parsedCorrectly() throws IOException {
        File file = writeFile("api 'com.example:apilib:2.0'\n");
        List<DependencyDeclaration> deps = DependencyResolver.parseDependenciesFile(file);
        assertEquals(1, deps.size());
        assertEquals("apilib", deps.get(0).getArtifactId());
    }

    // ------------------------------------------------------------------
    // "compileOnly" prefix
    // ------------------------------------------------------------------

    @Test
    public void parseDependenciesFile_compileOnlyPrefix_parsedCorrectly() throws IOException {
        File file = writeFile("compileOnly org.example:annotations:1.0\n");
        List<DependencyDeclaration> deps = DependencyResolver.parseDependenciesFile(file);
        assertEquals(1, deps.size());
        assertEquals("annotations", deps.get(0).getArtifactId());
    }

    @Test
    public void parseDependenciesFile_compileOnlyWithDoubleQuotes_parsedCorrectly() throws IOException {
        File file = writeFile("compileOnly \"org.example:annotations:1.0\"\n");
        List<DependencyDeclaration> deps = DependencyResolver.parseDependenciesFile(file);
        assertEquals(1, deps.size());
        assertEquals("annotations", deps.get(0).getArtifactId());
    }

    // ------------------------------------------------------------------
    // Mixed content: comments, blank lines, valid and invalid entries
    // ------------------------------------------------------------------

    @Test
    public void parseDependenciesFile_mixedContent_onlyValidLinesIncluded() throws IOException {
        File file = writeFile(
                "# Comment at top\n"
                + "\n"
                + "com.google.code.gson:gson:2.10.1\n"
                + "// another comment\n"
                + "\n"
                + "implementation androidx.core:core-ktx:1.12.0\n"
                + "not-valid-at-all\n"
                + "implementation 'org.jetbrains.kotlin:kotlin-stdlib:1.9.0'\n");
        List<DependencyDeclaration> deps = DependencyResolver.parseDependenciesFile(file);
        assertEquals(3, deps.size());
        assertEquals("gson", deps.get(0).getArtifactId());
        assertEquals("core-ktx", deps.get(1).getArtifactId());
        assertEquals("kotlin-stdlib", deps.get(2).getArtifactId());
    }

    // ------------------------------------------------------------------
    // Invalid lines are silently skipped
    // ------------------------------------------------------------------

    @Test
    public void parseDependenciesFile_invalidNotation_skipped() throws IOException {
        File file = writeFile("not:valid\n");  // only 2 parts
        List<DependencyDeclaration> deps = DependencyResolver.parseDependenciesFile(file);
        assertTrue(deps.isEmpty());
    }

    @Test
    public void parseDependenciesFile_fourPartNotation_skipped() throws IOException {
        File file = writeFile("g:a:v:classifier\n");
        assertTrue(DependencyResolver.parseDependenciesFile(file).isEmpty());
    }

    @Test
    public void parseDependenciesFile_unsafeCoordinatesWithSlash_skipped() throws IOException {
        File file = writeFile("../evil:artifact:1.0\n");
        assertTrue(DependencyResolver.parseDependenciesFile(file).isEmpty());
    }

    @Test
    public void parseDependenciesFile_emptyGroupInNotation_skipped() throws IOException {
        File file = writeFile(":artifact:1.0\n");
        assertTrue(DependencyResolver.parseDependenciesFile(file).isEmpty());
    }

    @Test
    public void parseDependenciesFile_emptyVersionInNotation_skipped() throws IOException {
        File file = writeFile("group:artifact:\n");
        assertTrue(DependencyResolver.parseDependenciesFile(file).isEmpty());
    }

    // ------------------------------------------------------------------
    // Generated template content matches expected format
    // ------------------------------------------------------------------

    @Test
    public void parseDependenciesFile_templateFileContent_returnsEmptyList() throws IOException {
        // The template file contains only comments; no active declarations
        File file = writeFile(
                "# Add dependencies here, one per line\n"
                + "# Format: implementation groupId:artifactId:version\n"
                + "# Example: implementation com.google.code.gson:gson:2.10.1\n"
                + "#\n"
                + "# Run \"Sync Dependencies\" from the IDE menu to download\n");
        List<DependencyDeclaration> deps = DependencyResolver.parseDependenciesFile(file);
        assertTrue("Template-only file should produce no declarations", deps.isEmpty());
    }

    // ------------------------------------------------------------------
    // Regression: leading/trailing whitespace around declarations
    // ------------------------------------------------------------------

    @Test
    public void parseDependenciesFile_lineWithLeadingWhitespace_parsedCorrectly() throws IOException {
        File file = writeFile("   com.example:lib:1.0\n");
        List<DependencyDeclaration> deps = DependencyResolver.parseDependenciesFile(file);
        assertEquals(1, deps.size());
        assertEquals("com.example", deps.get(0).getGroupId());
    }

    @Test
    public void parseDependenciesFile_windowsLineEndings_parsedCorrectly() throws IOException {
        File file = writeFile("com.example:lib:1.0\r\ncom.other:other:2.0\r\n");
        List<DependencyDeclaration> deps = DependencyResolver.parseDependenciesFile(file);
        assertEquals(2, deps.size());
    }

    // ------------------------------------------------------------------
    // Boundary: single valid declaration file
    // ------------------------------------------------------------------

    @Test
    public void parseDependenciesFile_singleValidLine_returnsOneDeclaration() throws IOException {
        File file = writeFile("org.example:myartifact:3.14\n");
        List<DependencyDeclaration> deps = DependencyResolver.parseDependenciesFile(file);
        assertEquals(1, deps.size());
        assertEquals("org.example:myartifact:3.14", deps.get(0).toString());
    }
}