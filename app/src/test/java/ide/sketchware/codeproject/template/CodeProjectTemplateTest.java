package ide.sketchware.codeproject.template;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import ide.sketchware.codeproject.model.CodeProject;

import static org.junit.Assert.assertTrue;

public class CodeProjectTemplateTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void generatesExpectedProjectStructureAndEscapesXml() throws Exception {
        File projectRoot = tempFolder.newFolder("proj_601");
        CodeProject project = new CodeProject(
                "601",
                "DemoApp",
                "com.example.demo",
                "Tom & Jerry's \"App\" <1>",
                "3",
                "1.2.3",
                "24"
        ) {
            @Override
            public String getProjectMyscPath() {
                return projectRoot.getAbsolutePath();
            }
        };

        CodeProjectTemplate.generate(project);

        File manifest = new File(project.getManifestPath());
        File mainActivity = new File(project.getJavaSourcePath(), "com/example/demo/MainActivity.java");
        File layout = new File(project.getResPath(), "layout/activity_main.xml");
        File strings = new File(project.getResPath(), "values/strings.xml");
        File colors = new File(project.getResPath(), "values/colors.xml");
        File styles = new File(project.getResPath(), "values/styles.xml");
        File deps = new File(project.getSourcePath(), "dependencies.txt");

        assertTrue(manifest.isFile());
        assertTrue(mainActivity.isFile());
        assertTrue(layout.isFile());
        assertTrue(strings.isFile());
        assertTrue(colors.isFile());
        assertTrue(styles.isFile());
        assertTrue(deps.isFile());

        String manifestText = new String(Files.readAllBytes(manifest.toPath()), StandardCharsets.UTF_8);
        assertTrue(manifestText.contains("package=\"com.example.demo\""));
        assertTrue(manifestText.contains("android:versionCode=\"3\""));
        assertTrue(manifestText.contains("android:versionName=\"1.2.3\""));
        assertTrue(manifestText.contains("android:minSdkVersion=\"24\""));

        String stringsText = new String(Files.readAllBytes(strings.toPath()), StandardCharsets.UTF_8);
        assertTrue(stringsText.contains("Tom &amp; Jerry\\'s \\\"App\\\" &lt;1&gt;"));

        String activityText = new String(Files.readAllBytes(mainActivity.toPath()), StandardCharsets.UTF_8);
        assertTrue(activityText.contains("package com.example.demo;"));
        assertTrue(activityText.contains("extends Activity"));
    }
}
