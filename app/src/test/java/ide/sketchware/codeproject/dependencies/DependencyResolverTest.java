package ide.sketchware.codeproject.dependencies;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

public class DependencyResolverTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    // ==================== parseDependenciesFile() - null/missing file ====================

    @Test
    public void parseDependenciesFile_nullFile_returnsEmptyList() {
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void parseDependenciesFile_nonExistentFile_returnsEmptyList() {
        File missing = new File(tempFolder.getRoot(), "nonexistent.txt");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(missing);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== parseDependenciesFile() - empty/comment-only files ====================

    @Test
    public void parseDependenciesFile_emptyFile_returnsEmptyList() throws IOException {
        File file = tempFolder.newFile("deps.txt");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertTrue(result.isEmpty());
    }

    @Test
    public void parseDependenciesFile_onlyHashComments_returnsEmptyList() throws IOException {
        File file = tempFolder.newFile("deps.txt");
        writeLines(file, "# This is a comment", "# Another comment", "# groupId:artifactId:version");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertTrue(result.isEmpty());
    }

    @Test
    public void parseDependenciesFile_onlySlashSlashComments_returnsEmptyList() throws IOException {
        File file = tempFolder.newFile("deps.txt");
        writeLines(file, "// This is a comment", "// Another comment");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertTrue(result.isEmpty());
    }

    @Test
    public void parseDependenciesFile_mixedCommentsAndBlanks_returnsEmptyList() throws IOException {
        File file = tempFolder.newFile("deps.txt");
        writeLines(file, "", "# comment", "   ", "// comment2", "");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertTrue(result.isEmpty());
    }

    // ==================== parseDependenciesFile() - bare notation ====================

    @Test
    public void parseDependenciesFile_bareNotation_returnsOneDependency() throws IOException {
        File file = tempFolder.newFile("deps.txt");
        writeLines(file, "com.google.code.gson:gson:2.10.1");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertEquals(1, result.size());
        assertEquals("com.google.code.gson", result.get(0).getGroupId());
        assertEquals("gson", result.get(0).getArtifactId());
        assertEquals("2.10.1", result.get(0).getVersion());
    }

    @Test
    public void parseDependenciesFile_multipleBareNotations_returnsAll() throws IOException {
        File file = tempFolder.newFile("deps.txt");
        writeLines(file,
                "com.google.code.gson:gson:2.10.1",
                "org.apache.commons:commons-lang3:3.12.0",
                "io.reactivex.rxjava3:rxjava:3.1.6");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertEquals(3, result.size());
        assertEquals("gson", result.get(0).getArtifactId());
        assertEquals("commons-lang3", result.get(1).getArtifactId());
        assertEquals("rxjava", result.get(2).getArtifactId());
    }

    // ==================== parseDependenciesFile() - Gradle-style prefixes ====================

    @Test
    public void parseDependenciesFile_implementationPrefix_stripsPrefix() throws IOException {
        File file = tempFolder.newFile("deps.txt");
        writeLines(file, "implementation com.example:mylib:1.0");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertEquals(1, result.size());
        assertEquals("com.example", result.get(0).getGroupId());
        assertEquals("mylib", result.get(0).getArtifactId());
        assertEquals("1.0", result.get(0).getVersion());
    }

    @Test
    public void parseDependenciesFile_apiPrefix_stripsPrefix() throws IOException {
        File file = tempFolder.newFile("deps.txt");
        writeLines(file, "api org.jetbrains.kotlin:kotlin-stdlib:1.9.0");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertEquals(1, result.size());
        assertEquals("kotlin-stdlib", result.get(0).getArtifactId());
    }

    @Test
    public void parseDependenciesFile_compileOnlyPrefix_stripsPrefix() throws IOException {
        File file = tempFolder.newFile("deps.txt");
        writeLines(file, "compileOnly com.example:annotation-processor:1.0");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertEquals(1, result.size());
        assertEquals("annotation-processor", result.get(0).getArtifactId());
    }

    // ==================== parseDependenciesFile() - quote stripping ====================

    @Test
    public void parseDependenciesFile_singleQuotedNotation_stripsQuotes() throws IOException {
        File file = tempFolder.newFile("deps.txt");
        writeLines(file, "implementation 'com.example:lib:1.0'");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertEquals(1, result.size());
        assertEquals("lib", result.get(0).getArtifactId());
    }

    @Test
    public void parseDependenciesFile_doubleQuotedNotation_stripsQuotes() throws IOException {
        File file = tempFolder.newFile("deps.txt");
        writeLines(file, "implementation \"com.example:lib:2.0\"");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertEquals(1, result.size());
        assertEquals("lib", result.get(0).getArtifactId());
        assertEquals("2.0", result.get(0).getVersion());
    }

    @Test
    public void parseDependenciesFile_bareNotationWithSingleQuotes_stripsQuotes() throws IOException {
        File file = tempFolder.newFile("deps.txt");
        writeLines(file, "'com.example:lib:1.0'");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertEquals(1, result.size());
        assertEquals("com.example", result.get(0).getGroupId());
    }

    @Test
    public void parseDependenciesFile_bareNotationWithDoubleQuotes_stripsQuotes() throws IOException {
        File file = tempFolder.newFile("deps.txt");
        writeLines(file, "\"com.example:lib:1.0\"");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertEquals(1, result.size());
        assertEquals("com.example", result.get(0).getGroupId());
    }

    // ==================== parseDependenciesFile() - mixed valid and invalid lines ====================

    @Test
    public void parseDependenciesFile_mixedValidAndInvalidLines_skipsInvalid() throws IOException {
        File file = tempFolder.newFile("deps.txt");
        writeLines(file,
                "# comment",
                "com.example:lib:1.0",
                "",
                "invalid-not-a-dependency",
                "// comment",
                "implementation com.other:other-lib:2.0");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertEquals(2, result.size());
        assertEquals("lib", result.get(0).getArtifactId());
        assertEquals("other-lib", result.get(1).getArtifactId());
    }

    @Test
    public void parseDependenciesFile_invalidNotation_skipped() throws IOException {
        File file = tempFolder.newFile("deps.txt");
        // Too few parts
        writeLines(file, "group:artifact");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertTrue(result.isEmpty());
    }

    @Test
    public void parseDependenciesFile_unsafeCoordinates_skipped() throws IOException {
        File file = tempFolder.newFile("deps.txt");
        writeLines(file, "../etc/passwd:artifact:1.0");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertTrue(result.isEmpty());
    }

    // ==================== parseDependenciesFile() - template/default file ====================

    @Test
    public void parseDependenciesFile_defaultTemplateFile_returnsEmptyList() throws IOException {
        // The generated template contains only comments — should return no declarations
        File file = tempFolder.newFile("deps.txt");
        writeLines(file,
                "# Add dependencies here, one per line",
                "# Format: implementation groupId:artifactId:version",
                "# Example: implementation com.google.code.gson:gson:2.10.1",
                "#",
                "# Run \"Sync Dependencies\" from the IDE menu to download");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertTrue(result.isEmpty());
    }

    // ==================== parseDependenciesFile() - whitespace handling ====================

    @Test
    public void parseDependenciesFile_lineWithLeadingWhitespace_parsed() throws IOException {
        File file = tempFolder.newFile("deps.txt");
        writeLines(file, "  com.example:lib:1.0");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertEquals(1, result.size());
    }

    @Test
    public void parseDependenciesFile_lineWithTrailingWhitespace_parsed() throws IOException {
        File file = tempFolder.newFile("deps.txt");
        writeLines(file, "com.example:lib:1.0   ");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertEquals(1, result.size());
    }

    // ==================== parseDependenciesFile() - preserves order ====================

    @Test
    public void parseDependenciesFile_preservesDeclarationOrder() throws IOException {
        File file = tempFolder.newFile("deps.txt");
        writeLines(file,
                "com.a:a:1.0",
                "com.b:b:2.0",
                "com.c:c:3.0");
        List<DependencyDeclaration> result = DependencyResolver.parseDependenciesFile(file);
        assertEquals(3, result.size());
        assertEquals("a", result.get(0).getArtifactId());
        assertEquals("b", result.get(1).getArtifactId());
        assertEquals("c", result.get(2).getArtifactId());
    }

    // ==================== Helper ====================

    private void writeLines(File file, String... lines) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            for (String line : lines) {
                writer.write(line);
                writer.write(System.lineSeparator());
            }
        }
    }
}