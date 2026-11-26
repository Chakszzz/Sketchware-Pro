package ide.sketchware.codeproject.dependencies;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class DependencyDeclarationTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void parsesValidMavenCoordinates() {
        DependencyDeclaration dep = DependencyDeclaration.parse("com.google.code.gson:gson:2.10.1");
        assertNotNull(dep);
        assertEquals("com.google.code.gson", dep.getGroupId());
        assertEquals("gson", dep.getArtifactId());
        assertEquals("2.10.1", dep.getVersion());
        assertEquals("com.google.code.gson:gson:2.10.1", dep.toString());
    }

    @Test
    public void rejectsInvalidOrUnsafeCoordinates() {
        assertNull(DependencyDeclaration.parse(null));
        assertNull(DependencyDeclaration.parse(""));
        assertNull(DependencyDeclaration.parse("only:two"));
        assertNull(DependencyDeclaration.parse("a:b:c:d"));
        assertNull(DependencyDeclaration.parse("../evil:artifact:1.0"));
        assertNull(DependencyDeclaration.parse("com.example:art/ifact:1.0"));
        assertNull(DependencyDeclaration.parse("com.example:artifact:1.0;rm"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorRejectsPathTraversal() {
        new DependencyDeclaration("com.example", "art\\evil", "1.0");
    }

    @Test
    public void parsesDependenciesFileWithCommentsAndPrefixes() throws Exception {
        File depsFile = tempFolder.newFile("dependencies.txt");
        String content = "# Comment line\n"
                + "// Another comment\n"
                + "\n"
                + "implementation com.google.code.gson:gson:2.10.1\n"
                + "api 'com.squareup.okhttp3:okhttp:4.12.0'\n"
                + "compileOnly \"org.jetbrains:annotations:24.0.0\"\n"
                + "androidx.cardview:cardview:1.0.0\n"
                + "implementation ../bad:coord:1.0\n";
        Files.write(depsFile.toPath(), content.getBytes(StandardCharsets.UTF_8));

        List<DependencyDeclaration> parsed = DependencyResolver.parseDependenciesFile(depsFile);
        assertEquals(4, parsed.size());
        assertEquals("com.google.code.gson:gson:2.10.1", parsed.get(0).toString());
        assertEquals("com.squareup.okhttp3:okhttp:4.12.0", parsed.get(1).toString());
        assertEquals("org.jetbrains:annotations:24.0.0", parsed.get(2).toString());
        assertEquals("androidx.cardview:cardview:1.0.0", parsed.get(3).toString());
    }
}
