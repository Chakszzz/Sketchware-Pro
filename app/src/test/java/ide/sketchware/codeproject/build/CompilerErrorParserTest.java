package ide.sketchware.codeproject.build;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CompilerErrorParserTest {

    @Test
    public void parsesEcjErrorWithLineAndColumn() {
        String output = "/project/app/src/main/java/com/example/MainActivity.java:14:9: error: cannot find symbol";
        List<CompilerErrorParser.CompilerError> errors = CompilerErrorParser.parse(output);

        assertEquals(1, errors.size());
        CompilerErrorParser.CompilerError err = errors.get(0);
        assertEquals("/project/app/src/main/java/com/example/MainActivity.java", err.filePath);
        assertEquals(14, err.lineNumber);
        assertEquals(9, err.columnNumber);
        assertEquals("cannot find symbol", err.message);
        assertFalse(err.isWarning);
    }

    @Test
    public void parsesAapt2XmlErrorWithoutColumn() {
        String output = "/project/app/src/main/res/layout/activity_main.xml:7: error: attribute android:foo not found";
        List<CompilerErrorParser.CompilerError> errors = CompilerErrorParser.parse(output);

        assertEquals(1, errors.size());
        CompilerErrorParser.CompilerError err = errors.get(0);
        assertEquals("/project/app/src/main/res/layout/activity_main.xml", err.filePath);
        assertEquals(7, err.lineNumber);
        assertEquals(-1, err.columnNumber);
        assertEquals("attribute android:foo not found", err.message);
        assertFalse(err.isWarning);
    }

    @Test
    public void parsesKotlinErrorAndWarning() {
        String output = "e: /project/Main.kt: (22, 13): Unresolved reference: foo\n"
                + "w: /project/Main.kt: (5, 1): Unused import directive";
        List<CompilerErrorParser.CompilerError> errors = CompilerErrorParser.parse(output);

        assertEquals(2, errors.size());

        CompilerErrorParser.CompilerError first = errors.get(0);
        assertEquals("/project/Main.kt", first.filePath);
        assertEquals(22, first.lineNumber);
        assertEquals(13, first.columnNumber);
        assertEquals("Unresolved reference: foo", first.message);
        assertFalse(first.isWarning);

        CompilerErrorParser.CompilerError second = errors.get(1);
        assertEquals("/project/Main.kt", second.filePath);
        assertEquals(5, second.lineNumber);
        assertEquals(1, second.columnNumber);
        assertEquals("Unused import directive", second.message);
        assertTrue(second.isWarning);
    }

    @Test
    public void ignoresNullEmptyAndNoiseLines() {
        assertTrue(CompilerErrorParser.parse(null).isEmpty());
        assertTrue(CompilerErrorParser.parse("").isEmpty());
        assertTrue(CompilerErrorParser.parse("BUILD FAILED\nSome unrelated line").isEmpty());
    }
}
