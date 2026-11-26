package ide.sketchware.codeproject.dependencies;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mod.jbk.build.BuiltInLibraries;

public class CodeProjectBuiltInLibrariesTest {

    @Test
    public void testSerializeAndDeserializeRoundTrip() {
        List<String> input = Arrays.asList(
                BuiltInLibraries.MATERIAL,
                BuiltInLibraries.GSON,
                BuiltInLibraries.MATERIAL // duplicate should be dropped
        );
        String csv = CodeProjectBuiltInLibraries.serialize(input);
        List<String> parsed = CodeProjectBuiltInLibraries.deserialize(csv);

        assertEquals(Arrays.asList(BuiltInLibraries.MATERIAL, BuiltInLibraries.GSON), parsed);
        assertTrue(CodeProjectBuiltInLibraries.deserialize("").isEmpty());
        assertTrue(CodeProjectBuiltInLibraries.deserialize(null).isEmpty());
    }

    @Test
    public void testAllSelectableOptionsExistInBuiltInLibraries() {
        List<CodeProjectBuiltInLibraries.Option> options = CodeProjectBuiltInLibraries.getSelectableOptions();
        assertFalse(options.isEmpty());
        for (CodeProjectBuiltInLibraries.Option opt : options) {
            assertTrue(
                    "Option " + opt.libraryName + " must exist in BuiltInLibraries.KNOWN_BUILT_IN_LIBRARIES",
                    BuiltInLibraries.BuiltInLibrary.ofName(opt.libraryName).isPresent()
            );
        }
    }

    @Test
    public void testResolveTransitiveHandlesCyclesAndIncludesDeps() {
        // Material -> AppCompat <-> AppCompat-Resources (cycle) -> Core -> Kotlin stdlib
        List<BuiltInLibraries.BuiltInLibrary> resolved = CodeProjectBuiltInLibraries.resolveTransitive(
                Collections.singletonList(BuiltInLibraries.MATERIAL)
        );
        Set<String> names = new HashSet<>();
        for (BuiltInLibraries.BuiltInLibrary lib : resolved) {
            names.add(lib.getName());
        }

        assertTrue(names.contains(BuiltInLibraries.MATERIAL));
        assertTrue(names.contains(BuiltInLibraries.ANDROIDX_APPCOMPAT));
        assertTrue(names.contains(BuiltInLibraries.ANDROIDX_APPCOMPAT_RESOURCES));
        assertTrue(names.contains(BuiltInLibraries.ANDROIDX_CORE));
        assertTrue(names.contains(BuiltInLibraries.JETBRAINS_KOTLIN_STDLIB));
        // No duplicates in resolved output
        assertEquals(names.size(), resolved.size());
    }

    @Test
    public void testResolveTransitiveIgnoresUnknownLibraryNames() {
        List<BuiltInLibraries.BuiltInLibrary> resolved = CodeProjectBuiltInLibraries.resolveTransitive(
                Arrays.asList("non-existent-lib-99.0", BuiltInLibraries.GSON)
        );
        Set<String> names = new HashSet<>();
        for (BuiltInLibraries.BuiltInLibrary lib : resolved) {
            names.add(lib.getName());
        }
        assertFalse(names.contains("non-existent-lib-99.0"));
        assertTrue(names.contains(BuiltInLibraries.GSON));
        assertTrue(names.contains(BuiltInLibraries.ERROR_PRONE_ANNOTATIONS));
        assertEquals(2, resolved.size());
    }
}
