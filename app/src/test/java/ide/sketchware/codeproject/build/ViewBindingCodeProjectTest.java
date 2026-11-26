package ide.sketchware.codeproject.build;

import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

import mod.pranav.viewbinding.ViewBindingBuilder;

public class ViewBindingCodeProjectTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testGeneratesBindingClassInAppPackage() throws Exception {
        File layoutDir = tempFolder.newFolder("res", "layout");
        File layoutFile = new File(layoutDir, "activity_main.xml");
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    android:layout_width=\"match_parent\"\n"
                + "    android:layout_height=\"match_parent\"\n"
                + "    android:orientation=\"vertical\">\n"
                + "    <TextView\n"
                + "        android:id=\"@+id/greeting_text\"\n"
                + "        android:layout_width=\"wrap_content\"\n"
                + "        android:layout_height=\"wrap_content\" />\n"
                + "    <Button\n"
                + "        android:id=\"@+id/action_button\"\n"
                + "        android:layout_width=\"wrap_content\"\n"
                + "        android:layout_height=\"wrap_content\" />\n"
                + "</LinearLayout>\n";
        Files.write(layoutFile.toPath(), xml.getBytes(StandardCharsets.UTF_8));

        File outDir = tempFolder.newFolder("gen", "com", "example", "demo");
        ViewBindingBuilder builder = new ViewBindingBuilder(
                Collections.singletonList(layoutFile),
                outDir,
                "com.example.demo"
        );
        builder.generateBindings();

        File generated = new File(outDir, "ActivityMainBinding.java");
        assertTrue("ActivityMainBinding.java should be generated", generated.isFile());

        String code = new String(Files.readAllBytes(generated.toPath()), StandardCharsets.UTF_8);
        assertTrue(code.contains("package com.example.demo;"));
        assertTrue(code.contains("public final class ActivityMainBinding"));
        assertTrue(code.contains("public final TextView greetingText;"));
        assertTrue(code.contains("public final Button actionButton;"));
        assertTrue(code.contains("R.layout.activity_main"));
        assertTrue(code.contains("R.id.greeting_text"));
        assertTrue(code.contains("R.id.action_button"));
    }
}
