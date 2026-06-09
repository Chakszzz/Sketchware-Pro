package ide.sketchware.codeproject.model;

import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link CodeProject}.
 *
 * <p>Covers the logic introduced in the PR:
 * <ul>
 *   <li>{@link CodeProject#isCodeProject(HashMap)} — new routing discriminator
 *   <li>{@link CodeProject#fromMetadata(HashMap)} — model construction from raw map
 *   <li>Constants, getters, and setters
 * </ul>
 */
public class CodeProjectTest {

    // -----------------------------------------------------------------------
    // isCodeProject() — the critical routing method added in this PR
    // -----------------------------------------------------------------------

    @Test
    public void isCodeProject_nullMap_returnsFalse() {
        assertFalse(CodeProject.isCodeProject(null));
    }

    @Test
    public void isCodeProject_emptyMap_returnsFalse() {
        assertFalse(CodeProject.isCodeProject(new HashMap<>()));
    }

    @Test
    public void isCodeProject_withCodeType_returnsTrue() {
        HashMap<String, Object> map = new HashMap<>();
        map.put(CodeProject.KEY_PROJECT_TYPE, CodeProject.PROJECT_TYPE_CODE);
        assertTrue(CodeProject.isCodeProject(map));
    }

    @Test
    public void isCodeProject_withCodeTypeString_returnsTrue() {
        HashMap<String, Object> map = new HashMap<>();
        map.put(CodeProject.KEY_PROJECT_TYPE, "code");
        assertTrue(CodeProject.isCodeProject(map));
    }

    @Test
    public void isCodeProject_withWrongType_returnsFalse() {
        HashMap<String, Object> map = new HashMap<>();
        map.put(CodeProject.KEY_PROJECT_TYPE, "block");
        assertFalse(CodeProject.isCodeProject(map));
    }

    @Test
    public void isCodeProject_withEmptyTypeString_returnsFalse() {
        HashMap<String, Object> map = new HashMap<>();
        map.put(CodeProject.KEY_PROJECT_TYPE, "");
        assertFalse(CodeProject.isCodeProject(map));
    }

    @Test
    public void isCodeProject_withNullTypeValue_returnsFalse() {
        HashMap<String, Object> map = new HashMap<>();
        map.put(CodeProject.KEY_PROJECT_TYPE, null);
        // yB.c returns "" for null values; "code".equals("") == false
        assertFalse(CodeProject.isCodeProject(map));
    }

    @Test
    public void isCodeProject_withUnrelatedKeys_returnsFalse() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("sc_id", "100");
        map.put("my_ws_name", "MyApp");
        // No sc_project_type key at all
        assertFalse(CodeProject.isCodeProject(map));
    }

    @Test
    public void isCodeProject_caseInsensitiveTypeShouldFail() {
        // The comparison is exact ("code".equals(...)), so "Code" or "CODE" must not match
        HashMap<String, Object> map = new HashMap<>();
        map.put(CodeProject.KEY_PROJECT_TYPE, "Code");
        assertFalse(CodeProject.isCodeProject(map));

        map.put(CodeProject.KEY_PROJECT_TYPE, "CODE");
        assertFalse(CodeProject.isCodeProject(map));
    }

    @Test
    public void isCodeProject_typeWithTrailingSpace_returnsFalse() {
        // Regression: whitespace-padded type must not match "code"
        HashMap<String, Object> map = new HashMap<>();
        map.put(CodeProject.KEY_PROJECT_TYPE, "code ");
        assertFalse(CodeProject.isCodeProject(map));
    }

    // -----------------------------------------------------------------------
    // fromMetadata()
    // -----------------------------------------------------------------------

    @Test
    public void fromMetadata_nullInput_returnsNull() {
        assertNull(CodeProject.fromMetadata(null));
    }

    @Test
    public void fromMetadata_emptyMap_returnsProjectWithDefaultMinSdk() {
        // yB.c returns "" for missing keys; isEmpty() => minSdk defaults to "26"
        CodeProject project = CodeProject.fromMetadata(new HashMap<>());
        assertNotNull(project);
        assertEquals("26", project.getMinSdkVersion());
    }

    @Test
    public void fromMetadata_completeMap_populatesAllFields() {
        HashMap<String, Object> map = buildMetadataMap(
                "42", "My Workspace", "com.example.app",
                "My App", "10", "2.0.0", "28");

        CodeProject project = CodeProject.fromMetadata(map);

        assertNotNull(project);
        assertEquals("42", project.getScId());
        assertEquals("My Workspace", project.getProjectName());
        assertEquals("com.example.app", project.getPackageName());
        assertEquals("My App", project.getAppName());
        assertEquals("10", project.getVersionCode());
        assertEquals("2.0.0", project.getVersionName());
        assertEquals("28", project.getMinSdkVersion());
    }

    @Test
    public void fromMetadata_emptyMinSdk_defaultsTo26() {
        HashMap<String, Object> map = buildMetadataMap(
                "1", "App", "com.test", "TestApp", "1", "1.0", "");

        CodeProject project = CodeProject.fromMetadata(map);

        assertNotNull(project);
        assertEquals("26", project.getMinSdkVersion());
    }

    @Test
    public void fromMetadata_explicitMinSdk_usesProvidedValue() {
        HashMap<String, Object> map = buildMetadataMap(
                "5", "MyProject", "com.foo.bar", "FooApp", "3", "1.2.3", "21");

        CodeProject project = CodeProject.fromMetadata(map);

        assertNotNull(project);
        assertEquals("21", project.getMinSdkVersion());
    }

    @Test
    public void fromMetadata_minSdk26Explicit_usesProvidedValueNotDefault() {
        // "26" is both the real value and the default; verify actual value is preserved
        HashMap<String, Object> map = buildMetadataMap(
                "7", "Proj", "com.proj", "Proj", "1", "1.0", "26");

        CodeProject project = CodeProject.fromMetadata(map);

        assertNotNull(project);
        assertEquals("26", project.getMinSdkVersion());
    }

    @Test
    public void fromMetadata_missingScId_returnsProjectWithEmptyScId() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("my_ws_name", "WorkspaceName");
        // sc_id is absent; yB.c returns ""

        CodeProject project = CodeProject.fromMetadata(map);

        assertNotNull(project);
        assertEquals("", project.getScId());
    }

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    @Test
    public void constant_projectTypeCode_isLiteralCode() {
        assertEquals("code", CodeProject.PROJECT_TYPE_CODE);
    }

    @Test
    public void constant_keyProjectType_isExpectedKey() {
        assertEquals("sc_project_type", CodeProject.KEY_PROJECT_TYPE);
    }

    // -----------------------------------------------------------------------
    // Constructor and setters/getters (regression: ensure rename did not break fields)
    // -----------------------------------------------------------------------

    @Test
    public void noArgConstructor_createsInstanceWithNullFields() {
        CodeProject project = new CodeProject();
        assertNotNull(project);
        assertNull(project.getScId());
        assertNull(project.getProjectName());
        assertNull(project.getPackageName());
        assertNull(project.getAppName());
        assertNull(project.getVersionCode());
        assertNull(project.getVersionName());
        assertNull(project.getMinSdkVersion());
    }

    @Test
    public void fullConstructor_setsAllFields() {
        CodeProject project = new CodeProject(
                "10", "WS", "com.ws", "AppName", "5", "1.5", "26");

        assertEquals("10", project.getScId());
        assertEquals("WS", project.getProjectName());
        assertEquals("com.ws", project.getPackageName());
        assertEquals("AppName", project.getAppName());
        assertEquals("5", project.getVersionCode());
        assertEquals("1.5", project.getVersionName());
        assertEquals("26", project.getMinSdkVersion());
    }

    @Test
    public void setterGetter_scId_roundTrip() {
        CodeProject p = new CodeProject();
        p.setScId("99");
        assertEquals("99", p.getScId());
    }

    @Test
    public void setterGetter_projectName_roundTrip() {
        CodeProject p = new CodeProject();
        p.setProjectName("HelloWorld");
        assertEquals("HelloWorld", p.getProjectName());
    }

    @Test
    public void setterGetter_packageName_roundTrip() {
        CodeProject p = new CodeProject();
        p.setPackageName("com.example.hello");
        assertEquals("com.example.hello", p.getPackageName());
    }

    @Test
    public void setterGetter_appName_roundTrip() {
        CodeProject p = new CodeProject();
        p.setAppName("Hello App");
        assertEquals("Hello App", p.getAppName());
    }

    @Test
    public void setterGetter_versionCode_roundTrip() {
        CodeProject p = new CodeProject();
        p.setVersionCode("42");
        assertEquals("42", p.getVersionCode());
    }

    @Test
    public void setterGetter_versionName_roundTrip() {
        CodeProject p = new CodeProject();
        p.setVersionName("3.1.4");
        assertEquals("3.1.4", p.getVersionName());
    }

    @Test
    public void setterGetter_minSdkVersion_roundTrip() {
        CodeProject p = new CodeProject();
        p.setMinSdkVersion("21");
        assertEquals("21", p.getMinSdkVersion());
    }

    // -----------------------------------------------------------------------
    // isCodeProject() — integration with the constants (avoid magic strings in callers)
    // -----------------------------------------------------------------------

    @Test
    public void isCodeProject_usesPublicConstantsConsistently() {
        // Using the public constants directly must produce the same result as using the raw string.
        HashMap<String, Object> mapViaConstant = new HashMap<>();
        mapViaConstant.put(CodeProject.KEY_PROJECT_TYPE, CodeProject.PROJECT_TYPE_CODE);

        HashMap<String, Object> mapViaLiteral = new HashMap<>();
        mapViaLiteral.put("sc_project_type", "code");

        assertTrue(CodeProject.isCodeProject(mapViaConstant));
        assertTrue(CodeProject.isCodeProject(mapViaLiteral));
        assertEquals(
                CodeProject.isCodeProject(mapViaConstant),
                CodeProject.isCodeProject(mapViaLiteral));
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private static HashMap<String, Object> buildMetadataMap(
            String scId, String wsName, String pkgName, String appName,
            String verCode, String verName, String minSdk) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("sc_id", scId);
        map.put("my_ws_name", wsName);
        map.put("my_sc_pkg_name", pkgName);
        map.put("my_app_name", appName);
        map.put("sc_ver_code", verCode);
        map.put("sc_ver_name", verName);
        map.put("sc_min_sdk", minSdk);
        return map;
    }
}
