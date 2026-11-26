package ide.sketchware.codeproject.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import a.a.a.wq;
import a.a.a.yB;
import ide.sketchware.codeproject.dependencies.CodeProjectBuiltInLibraries;

public class CodeProject {

    public static final String PROJECT_TYPE_CODE = "code";
    public static final String KEY_PROJECT_TYPE = "sc_project_type";
    public static final String KEY_BUILT_IN_LIBS = "sc_builtin_libs";
    public static final String KEY_VIEW_BINDING = "sc_view_binding";
    public static final String KEY_MINIFY_ENABLED = "sc_minify_enabled";
    public static final String KEY_STRINGFOG_ENABLED = "sc_stringfog_enabled";

    private String scId;
    private String projectName;
    private String packageName;
    private String appName;
    private String versionCode;
    private String versionName;
    private String minSdkVersion;
    private List<String> builtInLibraries = new ArrayList<>();
    private boolean viewBindingEnabled = false;
    private boolean minifyEnabled = false;
    private boolean stringFogEnabled = false;

    public CodeProject() {
    }

    public CodeProject(String scId, String projectName, String packageName, String appName, String versionCode, String versionName, String minSdkVersion) {
        this.scId = scId;
        this.projectName = projectName;
        this.packageName = packageName;
        this.appName = appName;
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.minSdkVersion = minSdkVersion;
    }

    public String getScId() {
        return scId;
    }

    public void setScId(String scId) {
        this.scId = scId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getVersionCode() {
        return versionCode;
    }

    public void setVersionCode(String versionCode) {
        this.versionCode = versionCode;
    }

    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public String getMinSdkVersion() {
        return minSdkVersion;
    }

    public void setMinSdkVersion(String minSdkVersion) {
        this.minSdkVersion = minSdkVersion;
    }

    public List<String> getBuiltInLibraries() {
        return builtInLibraries == null ? Collections.emptyList() : builtInLibraries;
    }

    public void setBuiltInLibraries(List<String> builtInLibraries) {
        this.builtInLibraries = builtInLibraries == null ? new ArrayList<>() : new ArrayList<>(builtInLibraries);
    }

    public boolean isViewBindingEnabled() {
        return viewBindingEnabled;
    }

    public void setViewBindingEnabled(boolean viewBindingEnabled) {
        this.viewBindingEnabled = viewBindingEnabled;
    }

    public boolean isMinifyEnabled() {
        return minifyEnabled;
    }

    public void setMinifyEnabled(boolean minifyEnabled) {
        this.minifyEnabled = minifyEnabled;
    }

    public boolean isStringFogEnabled() {
        return stringFogEnabled;
    }

    public void setStringFogEnabled(boolean stringFogEnabled) {
        this.stringFogEnabled = stringFogEnabled;
    }

    public String getProjectMyscPath() {
        return wq.d(scId);
    }

    public String getSourcePath() {
        return getProjectMyscPath() + File.separator + "app" + File.separator + "src" + File.separator + "main";
    }

    public String getJavaSourcePath() {
        return getSourcePath() + File.separator + "java";
    }

    public String getKotlinSourcePath() {
        return getJavaSourcePath();
    }

    public String getResPath() {
        return getSourcePath() + File.separator + "res";
    }

    public String getManifestPath() {
        return getSourcePath() + File.separator + "AndroidManifest.xml";
    }

    public String getProguardRulesPath() {
        return getSourcePath() + File.separator + "proguard-rules.pro";
    }

    public String getBinPath() {
        return getProjectMyscPath() + File.separator + "bin";
    }

    public String getGenPath() {
        return getProjectMyscPath() + File.separator + "gen";
    }

    public String getLibsPath() {
        return getProjectMyscPath() + File.separator + "app" + File.separator + "libs";
    }

    public String getJniLibsPath() {
        return getSourcePath() + File.separator + "jniLibs";
    }

    public String getResolvedAarPath() {
        return getLibsPath() + File.separator + "resolved_aar";
    }

    public static boolean isCodeProject(HashMap<String, Object> projectMap) {
        if (projectMap == null) return false;
        return PROJECT_TYPE_CODE.equals(yB.c(projectMap, KEY_PROJECT_TYPE));
    }

    public static CodeProject fromMetadata(HashMap<String, Object> metadata) {
        if (metadata == null) return null;
        CodeProject project = new CodeProject();
        project.setScId(yB.c(metadata, "sc_id"));
        project.setProjectName(yB.c(metadata, "my_ws_name"));
        project.setPackageName(yB.c(metadata, "my_sc_pkg_name"));
        project.setAppName(yB.c(metadata, "my_app_name"));
        project.setVersionCode(yB.c(metadata, "sc_ver_code"));
        project.setVersionName(yB.c(metadata, "sc_ver_name"));
        String minSdk = yB.c(metadata, "sc_min_sdk");
        if (minSdk.isEmpty()) {
            minSdk = "21";
        }
        project.setMinSdkVersion(minSdk);
        project.setBuiltInLibraries(CodeProjectBuiltInLibraries.deserialize(yB.c(metadata, KEY_BUILT_IN_LIBS)));
        project.setViewBindingEnabled("true".equalsIgnoreCase(yB.c(metadata, KEY_VIEW_BINDING)));
        project.setMinifyEnabled("true".equalsIgnoreCase(yB.c(metadata, KEY_MINIFY_ENABLED)));
        project.setStringFogEnabled("true".equalsIgnoreCase(yB.c(metadata, KEY_STRINGFOG_ENABLED)));
        return project;
    }
}
