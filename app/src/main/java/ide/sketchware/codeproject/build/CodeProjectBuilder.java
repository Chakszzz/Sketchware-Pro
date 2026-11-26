package ide.sketchware.codeproject.build;

import android.content.Context;
import android.os.Build;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import a.a.a.KB;
import com.android.bundle.Config;
import com.android.tools.build.bundletool.commands.BuildBundleCommand;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.origin.Origin;
import com.github.megatronking.stringfog.plugin.StringFogClassInjector;
import com.github.megatronking.stringfog.plugin.StringFogMappingPrinter;
import com.google.common.collect.ImmutableList;
import com.iyxan23.zipalignjava.InvalidZipException;
import com.iyxan23.zipalignjava.ZipAlign;

import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.compiler.kotlin.DiagnosticCollector;
import mod.hey.studios.project.proguard.ProguardHandler;
import mod.jbk.build.BuiltInLibraries;
import mod.pranav.viewbinding.ViewBindingBuilder;
import org.jetbrains.kotlin.cli.common.ExitCode;
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments;
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler;
import org.jetbrains.kotlin.config.Services;
import ide.sketchware.codeproject.dependencies.CodeProjectBuiltInLibraries;
import ide.sketchware.codeproject.model.CodeProject;
import ide.sketchware.utility.BinaryExecutor;
import ide.sketchware.utility.FileUtil;

public class CodeProjectBuilder {

    private static final Pattern MANIFEST_PACKAGE_PATTERN =
            Pattern.compile("package\\s*=\\s*\"([^\"]+)\"");

    private final Context context;
    private final CodeProject project;
    private final File binDir;
    private final File genDir;
    private final File classesDir;
    private final File dexDir;
    private File aapt2Binary;
    private List<File> cachedLibraryJars;
    private List<BuiltInLibraries.BuiltInLibrary> cachedBuiltInLibraries;
    private BuildSettings buildSettings;
    private boolean usesKotlin = false;
    private boolean protoFormat = false;

    public CodeProjectBuilder(Context context, CodeProject project) {
        this.context = context;
        this.project = project;
        this.binDir = new File(project.getBinPath());
        this.genDir = new File(project.getGenPath());
        this.classesDir = new File(binDir, "classes");
        this.dexDir = new File(binDir, "dex");
    }

    public void setProtoFormat(boolean protoFormat) {
        this.protoFormat = protoFormat;
    }

    public File build(BuildProgressListener listener) throws Exception {
        File alignedApk = buildUnsignedAlignedApk(listener);

        if (listener != null) listener.onProgress("Signing APK...", 8);
        File signedApk = signApk(alignedApk);

        if (listener != null) listener.onProgress("Done", 9);
        return signedApk;
    }

    public File buildUnsignedAlignedApk(BuildProgressListener listener) throws Exception {
        this.protoFormat = false;
        compileToDex(listener);

        if (listener != null) listener.onProgress("Building APK...", 7);
        return buildApk();
    }

    public File buildAppBundle(BuildProgressListener listener) throws Exception {
        this.protoFormat = true;
        compileToDex(listener);

        if (listener != null) listener.onProgress("Building App Bundle...", 7);
        return packageAppBundle();
    }

    private void compileToDex(BuildProgressListener listener) throws Exception {
        if (listener != null) listener.onProgress("Preparing...", 1);
        prepare();

        if (listener != null) listener.onProgress("Extracting AAPT2...", 2);
        extractAapt2();

        if (listener != null) listener.onProgress("Compiling resources...", 3);
        compileResources();

        if (project.isViewBindingEnabled()) {
            if (listener != null) listener.onProgress("Generating ViewBinding...", 3);
            generateViewBindings();
        }

        if (listener != null) listener.onProgress("Compiling Kotlin...", 4);
        compileKotlin();

        if (listener != null) listener.onProgress("Compiling Java...", 5);
        compileJava();

        if (project.isStringFogEnabled()) {
            if (listener != null) listener.onProgress("Running StringFog...", 5);
            runStringFog();
        }

        if (listener != null) {
            listener.onProgress(project.isMinifyEnabled() ? "Shrinking with R8..." : "Creating DEX...", 6);
        }
        createDex();
    }

    private void prepare() {
        if (binDir.exists()) {
            FileUtil.deleteFile(binDir.getAbsolutePath());
        }
        if (genDir.exists()) {
            FileUtil.deleteFile(genDir.getAbsolutePath());
        }
        binDir.mkdirs();
        classesDir.mkdirs();
        dexDir.mkdirs();
        genDir.mkdirs();
        cachedLibraryJars = null;
        cachedBuiltInLibraries = null;
        buildSettings = loadBuildSettings();
        usesKotlin = false;
        BuiltInLibraries.extractCompileAssets();
    }

    private BuildSettings loadBuildSettings() {
        try {
            if (project.getScId() != null && !project.getScId().isEmpty()) {
                return new BuildSettings(project.getScId());
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private File resolveAndroidJar() {
        if (buildSettings != null) {
            String customPath = buildSettings.getValue(BuildSettings.SETTING_ANDROID_JAR_PATH, "");
            if (customPath != null && !customPath.trim().isEmpty()) {
                File customJar = new File(customPath.trim());
                if (customJar.isFile()) {
                    return customJar;
                }
            }
        }
        return new File(BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH, "android.jar");
    }

    private String getExtraClasspath() {
        if (buildSettings == null) return "";
        String cp = buildSettings.getValue(BuildSettings.SETTING_CLASSPATH, "");
        return cp == null ? "" : cp.trim();
    }

    private void extractAapt2() throws Exception {
        String abi = Build.SUPPORTED_ABIS[0];
        String assetName = "aapt/aapt2-" + abi;
        aapt2Binary = new File(context.getFilesDir(), "aapt2");

        // Only re-extract if the file does not exist or has a different size than the asset
        long assetSize;
        try (InputStream sizeIs = context.getAssets().open(assetName)) {
            assetSize = 0;
            byte[] buf = new byte[8192];
            int n;
            while ((n = sizeIs.read(buf)) != -1) {
                assetSize += n;
            }
        }

        if (aapt2Binary.exists() && aapt2Binary.length() == assetSize && aapt2Binary.canExecute()) {
            return;
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            FileUtil.extractFileFromZip(is, aapt2Binary);
        }
        aapt2Binary.setExecutable(true);
    }

    private void compileResources() throws Exception {
        File resDir = new File(project.getResPath());
        File compiledResDir = new File(binDir, "compiled_res");
        File compiledAarResDir = new File(binDir, "compiled_aar_res");
        compiledResDir.mkdirs();
        compiledAarResDir.mkdirs();

        // Compile built-in offline library resources (cached across builds)
        List<File> compiledBuiltInArchives = new ArrayList<>();
        List<File> builtInAssetsDirs = new ArrayList<>();
        Set<String> extraPackages = new LinkedHashSet<>();

        List<BuiltInLibraries.BuiltInLibrary> builtInLibs = getResolvedBuiltInLibraries();
        if (!builtInLibs.isEmpty()) {
            File builtInCacheDir = new File(context.getCacheDir(), "compiled_builtin_libs");
            builtInCacheDir.mkdirs();
            for (BuiltInLibraries.BuiltInLibrary lib : builtInLibs) {
                File libDir = BuiltInLibraries.getLibraryPath(lib.getName());
                File libRes = new File(libDir, "res");
                if (libRes.exists() && libRes.isDirectory() && hasAnyFiles(libRes)) {
                    File cachedZip = new File(builtInCacheDir, lib.getName() + ".zip");
                    if (!cachedZip.exists() || cachedZip.length() == 0) {
                        File tmpZip = new File(builtInCacheDir, lib.getName() + ".zip.tmp");
                        if (tmpZip.exists()) {
                            tmpZip.delete();
                        }
                        ArrayList<String> libArgs = new ArrayList<>();
                        libArgs.add(aapt2Binary.getAbsolutePath());
                        libArgs.add("compile");
                        libArgs.add("--dir");
                        libArgs.add(libRes.getAbsolutePath());
                        libArgs.add("-o");
                        libArgs.add(tmpZip.getAbsolutePath());

                        BinaryExecutor libExecutor = new BinaryExecutor();
                        libExecutor.setCommands(libArgs);
                        String libOutput = libExecutor.execute();
                        if (!tmpZip.exists()) {
                            String detail = libOutput.isEmpty()
                                    ? "No compiled output produced for built-in library " + lib.getName()
                                    : libOutput;
                            throw new Exception("AAPT2 built-in library compile error: " + detail);
                        }
                        if (!tmpZip.renameTo(cachedZip)) {
                            FileUtil.copyFile(tmpZip.getAbsolutePath(), cachedZip.getAbsolutePath());
                            tmpZip.delete();
                        }
                    }
                    compiledBuiltInArchives.add(cachedZip);
                }

                File libAssets = new File(libDir, "assets");
                if (libAssets.exists() && libAssets.isDirectory() && hasAnyFiles(libAssets)) {
                    builtInAssetsDirs.add(libAssets);
                }

                if (lib.getPackageName().isPresent()) {
                    String pkg = lib.getPackageName().get();
                    if (!pkg.isEmpty() && !pkg.equals(project.getPackageName())) {
                        extraPackages.add(pkg);
                    }
                }
            }
        }

        // Compile resolved AAR library resources (after built-in libs, before project overlays)
        List<File> compiledAarArchives = new ArrayList<>();
        List<File> aarAssetsDirs = new ArrayList<>();
        List<File> libraryManifests = new ArrayList<>();

        for (BuiltInLibraries.BuiltInLibrary lib : builtInLibs) {
            File libManifest = new File(BuiltInLibraries.getLibraryPath(lib.getName()), "AndroidManifest.xml");
            if (libManifest.isFile()) {
                libraryManifests.add(libManifest);
            }
        }

        File resolvedAarRoot = new File(project.getResolvedAarPath());
        if (resolvedAarRoot.exists() && resolvedAarRoot.isDirectory()) {
            File[] aarDirs = resolvedAarRoot.listFiles();
            if (aarDirs != null) {
                Arrays.sort(aarDirs, (a, b) -> a.getName().compareTo(b.getName()));
                for (File aarDir : aarDirs) {
                    if (!aarDir.isDirectory()) continue;

                    File aarRes = new File(aarDir, "res");
                    if (aarRes.exists() && aarRes.isDirectory() && hasAnyFiles(aarRes)) {
                        File outZip = new File(compiledAarResDir, aarDir.getName() + ".zip");
                        ArrayList<String> aarArgs = new ArrayList<>();
                        aarArgs.add(aapt2Binary.getAbsolutePath());
                        aarArgs.add("compile");
                        aarArgs.add("--dir");
                        aarArgs.add(aarRes.getAbsolutePath());
                        aarArgs.add("-o");
                        aarArgs.add(outZip.getAbsolutePath());

                        BinaryExecutor aarExecutor = new BinaryExecutor();
                        aarExecutor.setCommands(aarArgs);
                        String aarOutput = aarExecutor.execute();
                        if (!outZip.exists()) {
                            String detail = aarOutput.isEmpty()
                                    ? "No compiled output produced for AAR " + aarDir.getName()
                                    : aarOutput;
                            throw new Exception("AAPT2 AAR compile error: " + detail);
                        }
                        compiledAarArchives.add(outZip);
                    }

                    File aarAssets = new File(aarDir, "assets");
                    if (aarAssets.exists() && aarAssets.isDirectory() && hasAnyFiles(aarAssets)) {
                        aarAssetsDirs.add(aarAssets);
                    }

                    File aarManifest = new File(aarDir, "AndroidManifest.xml");
                    if (aarManifest.exists() && aarManifest.isFile()) {
                        libraryManifests.add(aarManifest);
                        String pkg = extractManifestPackage(aarManifest);
                        if (pkg != null && !pkg.isEmpty() && !pkg.equals(project.getPackageName())) {
                            extraPackages.add(pkg);
                        }
                    }
                }
            }
        }

        // Compile individual project resource files
        if (resDir.exists()) {
            File[] resDirs = resDir.listFiles();
            if (resDirs != null) {
                for (File dir : resDirs) {
                    if (dir.isDirectory()) {
                        File[] files = dir.listFiles();
                        if (files != null) {
                            for (File file : files) {
                                if (file.isFile()) {
                                    ArrayList<String> args = new ArrayList<>();
                                    args.add(aapt2Binary.getAbsolutePath());
                                    args.add("compile");
                                    args.add("-o");
                                    args.add(compiledResDir.getAbsolutePath());
                                    args.add(file.getAbsolutePath());

                                    BinaryExecutor executor = new BinaryExecutor();
                                    executor.setCommands(args);
                                    String output = executor.execute();

                                    // Determine expected .flat artifact name
                                    String flatName = dir.getName() + "_" + file.getName().replaceFirst("\\.[^.]+$", "") + ".arsc.flat";
                                    if (!file.getName().endsWith(".xml")) {
                                        flatName = dir.getName() + "_" + file.getName() + ".flat";
                                    }
                                    File expectedFlat = new File(compiledResDir, flatName);

                                    // Only fail if no .flat artifact was produced (output may contain non-fatal warnings)
                                    if (!expectedFlat.exists()) {
                                        // Fallback: accept a .flat file whose basename exactly matches the resource stem
                                        boolean found = false;
                                        String stem = file.getName().replaceFirst("\\.[^.]+$", "");
                                        File[] flatFiles = compiledResDir.listFiles();
                                        if (flatFiles != null) {
                                            for (File f : flatFiles) {
                                                if (f.getName().endsWith(".flat")) {
                                                    // Extract the basename (everything before .arsc.flat or .flat)
                                                    String flatBase = f.getName()
                                                            .replace(".arsc.flat", "")
                                                            .replace(".flat", "");
                                                    // AAPT2 names flats as "dir_stem.arsc.flat" or "dir_stem.ext.flat"
                                                    // The stem we need to match is after the last underscore-separated dir prefix
                                                    String expectedPrefix = dir.getName() + "_" + stem;
                                                    if (flatBase.equals(expectedPrefix) || flatBase.startsWith(expectedPrefix + ".")) {
                                                        found = true;
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                        if (!found) {
                                            String errorDetail = output.isEmpty()
                                                    ? "No compiled output produced for " + file.getName()
                                                    : output;
                                            throw new Exception("AAPT2 compile error: " + errorDetail);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Link resources
        File androidJar = resolveAndroidJar();
        File outputApk = new File(binDir, "resources.apk");
        File manifestFile = new File(project.getManifestPath());
        File effectiveManifest = manifestFile;
        if (!libraryManifests.isEmpty()) {
            effectiveManifest = CodeProjectManifestMerger.merge(
                    manifestFile,
                    libraryManifests,
                    project.getPackageName(),
                    new File(binDir, "merged_AndroidManifest.xml"));
        }
        File aaptRulesFile = new File(binDir, "aapt_rules.pro");

        ArrayList<String> args = new ArrayList<>();
        args.add(aapt2Binary.getAbsolutePath());
        args.add("link");
        if (protoFormat) {
            args.add("--proto-format");
        }
        args.add("-I");
        args.add(androidJar.getAbsolutePath());
        args.add("--manifest");
        args.add(effectiveManifest.getAbsolutePath());
        args.add("-o");
        args.add(outputApk.getAbsolutePath());
        args.add("--java");
        args.add(genDir.getAbsolutePath());
        args.add("--proguard");
        args.add(aaptRulesFile.getAbsolutePath());
        args.add("--auto-add-overlay");
        args.add("--no-version-vectors");
        args.add("--no-version-transitions");

        File assetsDir = new File(project.getSourcePath() + File.separator + "assets");
        if (assetsDir.exists() && assetsDir.isDirectory()) {
            args.add("-A");
            args.add(assetsDir.getAbsolutePath());
        }
        for (File aarAssets : aarAssetsDirs) {
            args.add("-A");
            args.add(aarAssets.getAbsolutePath());
        }
        for (File builtInAssets : builtInAssetsDirs) {
            args.add("-A");
            args.add(builtInAssets.getAbsolutePath());
        }

        if (project.getVersionCode() != null && !project.getVersionCode().isEmpty()) {
            args.add("--version-code");
            args.add(project.getVersionCode());
        }
        if (project.getVersionName() != null && !project.getVersionName().isEmpty()) {
            args.add("--version-name");
            args.add(project.getVersionName());
        }
        if (project.getMinSdkVersion() != null && !project.getMinSdkVersion().isEmpty()) {
            args.add("--min-sdk-version");
            args.add(project.getMinSdkVersion());
        }

        if (!extraPackages.isEmpty()) {
            args.add("--extra-packages");
            args.add(String.join(":", extraPackages));
        }

        // Add built-in library resources, then AAR resources, then project resources so project overlays win
        for (File builtInZip : compiledBuiltInArchives) {
            args.add("-R");
            args.add(builtInZip.getAbsolutePath());
        }
        for (File aarZip : compiledAarArchives) {
            args.add("-R");
            args.add(aarZip.getAbsolutePath());
        }
        File[] compiledFiles = compiledResDir.listFiles();
        if (compiledFiles != null) {
            for (File f : compiledFiles) {
                args.add("-R");
                args.add(f.getAbsolutePath());
            }
        }

        BinaryExecutor executor = new BinaryExecutor();
        executor.setCommands(args);
        String output = executor.execute();

        // Check if expected output file exists as a reliable indicator of success
        if (!outputApk.exists()) {
            String errorDetail = output.isEmpty() ? "AAPT2 link produced no output file" : output;
            throw new Exception("AAPT2 link error: " + errorDetail);
        }
    }

    private void generateViewBindings() throws Exception {
        File layoutDir = new File(project.getResPath(), "layout");
        if (!layoutDir.exists() || !layoutDir.isDirectory()) {
            return;
        }
        File[] files = layoutDir.listFiles((dir, name) -> name.endsWith(".xml"));
        if (files == null || files.length == 0) {
            return;
        }
        Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));

        String packageName = project.getPackageName();
        if (packageName == null || packageName.isEmpty()) {
            return;
        }
        File bindingOutDir = new File(genDir, packageName.replace('.', File.separatorChar));
        if (!bindingOutDir.exists() && !bindingOutDir.mkdirs()) {
            throw new Exception("Failed to create ViewBinding output directory: " + bindingOutDir.getAbsolutePath());
        }
        new ViewBindingBuilder(Arrays.asList(files), bindingOutDir, packageName).generateBindings();
    }

    private boolean hasAnyFiles(File dir) {
        File[] children = dir.listFiles();
        if (children == null) return false;
        for (File child : children) {
            if (child.isFile() || (child.isDirectory() && hasAnyFiles(child))) {
                return true;
            }
        }
        return false;
    }

    static String extractManifestPackage(File manifestFile) {
        if (manifestFile == null || !manifestFile.isFile()) return null;
        String content = FileUtil.readFile(manifestFile.getAbsolutePath());
        if (content == null || content.isEmpty()) return null;
        Matcher matcher = MANIFEST_PACKAGE_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }

    private void compileKotlin() throws Exception {
        File sourceDir = new File(project.getKotlinSourcePath());
        List<File> ktFiles = new ArrayList<>(FileUtil.listFilesRecursively(sourceDir, ".kt"));

        if (ktFiles.isEmpty()) return;
        usesKotlin = true;

        File androidJar = resolveAndroidJar();
        File lambdaStubs = new File(BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH, "core-lambda-stubs.jar");
        File kotlinStdlib = BuiltInLibraries.getLibraryClassesJarPath(BuiltInLibraries.JETBRAINS_KOTLIN_STDLIB);

        StringBuilder classpath = new StringBuilder();
        classpath.append(androidJar.getAbsolutePath());
        if (lambdaStubs.exists()) {
            classpath.append(":").append(lambdaStubs.getAbsolutePath());
        }
        // Kotlin stdlib must be on the classpath to resolve standard library symbols
        if (kotlinStdlib.exists()) {
            classpath.append(":").append(kotlinStdlib.getAbsolutePath());
        }
        for (File jar : getLibraryJars()) {
            classpath.append(":").append(jar.getAbsolutePath());
        }
        String extraCp = getExtraClasspath();
        if (!extraCp.isEmpty()) {
            classpath.append(":").append(extraCp);
        }

        List<String> arguments = new ArrayList<>();
        arguments.add("-cp");
        arguments.add(classpath.toString());

        // Pass generated R sources and project Java sources as source roots so Kotlin
        // can resolve R and other project Java types. With setCompileJava(false) the
        // compiler reads these .java files for resolution only (it does not emit them).
        if (genDir.exists()) {
            arguments.add(genDir.getAbsolutePath());
        }
        // The Kotlin source dir is the same as the Java source dir; passing the
        // directory compiles all .kt files within it and reads sibling .java files.
        arguments.add(sourceDir.getAbsolutePath());

        K2JVMCompiler compiler = new K2JVMCompiler();
        DiagnosticCollector collector = new DiagnosticCollector();

        K2JVMCompilerArguments args = new K2JVMCompilerArguments();
        args.setCompileJava(false);
        args.setIncludeRuntime(false);
        args.setNoJdk(true);
        args.setNoReflect(true);
        args.setNoStdlib(true);
        args.setDestination(classesDir.getAbsolutePath());

        File kotlinHome = new File(binDir, "kotlin_home");
        kotlinHome.mkdirs();
        args.setKotlinHome(kotlinHome.getAbsolutePath());

        compiler.parseArguments(arguments.toArray(new String[0]), args);

        ExitCode exitCode = compiler.exec(collector, Services.EMPTY, args);

        // Clean up META-INF that kotlinc generates (causes D8 issues)
        File metaInf = new File(classesDir, "META-INF");
        if (metaInf.exists()) {
            FileUtil.deleteFile(metaInf.getAbsolutePath());
        }

        if (exitCode != ExitCode.OK) {
            throw new Exception("Kotlin compilation failed: " + collector.getDiagnostics(true));
        }
    }

    private void compileJava() throws Exception {
        File androidJar = resolveAndroidJar();
        File lambdaStubs = new File(BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH, "core-lambda-stubs.jar");

        StringBuilder classpath = new StringBuilder();
        classpath.append(androidJar.getAbsolutePath());
        if (lambdaStubs.exists()) {
            classpath.append(":").append(lambdaStubs.getAbsolutePath());
        }

        // Add compiled Kotlin classes to classpath so Java can reference them
        File[] classFiles = classesDir.listFiles();
        if (classesDir.exists() && classFiles != null && classFiles.length > 0) {
            classpath.append(":").append(classesDir.getAbsolutePath());
        }
        for (File jar : getLibraryJars()) {
            classpath.append(":").append(jar.getAbsolutePath());
        }
        String extraCp = getExtraClasspath();
        if (!extraCp.isEmpty()) {
            classpath.append(":").append(extraCp);
        }

        String javaVersion = BuildSettings.SETTING_JAVA_VERSION_1_8;
        boolean suppressWarnings = true;
        if (buildSettings != null) {
            String configuredVersion = buildSettings.getValue(
                    BuildSettings.SETTING_JAVA_VERSION, BuildSettings.SETTING_JAVA_VERSION_1_8);
            if (configuredVersion != null && !configuredVersion.trim().isEmpty()) {
                javaVersion = configuredVersion.trim();
            }
            suppressWarnings = BuildSettings.SETTING_GENERIC_VALUE_TRUE.equals(
                    buildSettings.getValue(BuildSettings.SETTING_NO_WARNINGS, BuildSettings.SETTING_GENERIC_VALUE_TRUE));
        }

        List<String> args = new ArrayList<>();
        args.add("-" + javaVersion);
        if (suppressWarnings) {
            args.add("-nowarn");
        } else {
            args.add("-deprecation");
        }
        args.add("-d");
        args.add(classesDir.getAbsolutePath());
        args.add("-cp");
        args.add(classpath.toString());
        args.add("-proc:none");
        args.add(project.getJavaSourcePath());
        args.add(genDir.getAbsolutePath());

        java.io.StringWriter errWriter = new java.io.StringWriter();
        java.io.StringWriter outWriter = new java.io.StringWriter();
        org.eclipse.jdt.internal.compiler.batch.Main ecjMain =
                new org.eclipse.jdt.internal.compiler.batch.Main(
                        new java.io.PrintWriter(outWriter),
                        new java.io.PrintWriter(errWriter),
                        false, null, null);

        boolean success = ecjMain.compile(args.toArray(new String[0]));
        if (!success) {
            String errorOutput = errWriter.toString();
            if (errorOutput.isEmpty()) {
                errorOutput = outWriter.toString();
            }
            if (errorOutput.isEmpty()) {
                errorOutput = "Java compilation failed. Check source files for errors.";
            }
            throw new Exception(errorOutput);
        }
    }

    private void runStringFog() throws Exception {
        StringFogMappingPrinter mappingPrinter = new StringFogMappingPrinter(
                new File(binDir, "stringFogMapping.txt"));
        StringFogClassInjector injector = new StringFogClassInjector(
                new String[0],
                "UTF-8",
                "com.github.megatronking.stringfog.xor.StringFogImpl",
                "com.github.megatronking.stringfog.xor.StringFogImpl",
                mappingPrinter);
        mappingPrinter.startMappingOutput();
        mappingPrinter.ouputInfo("UTF-8", "com.github.megatronking.stringfog.xor.StringFogImpl");
        injector.doFog2ClassInDir(classesDir);
        KB.a(context, "stringfog/stringfog.zip", classesDir.getAbsolutePath());
    }

    private void createDex() throws Exception {
        File outputDex = new File(dexDir, "classes.dex");

        List<Path> programFiles = new ArrayList<>();
        Set<String> seenProgramPaths = new HashSet<>();
        for (File file : FileUtil.listFilesRecursively(classesDir, ".class")) {
            addUniquePath(file, programFiles, seenProgramPaths);
        }
        for (File jar : getLibraryJars()) {
            addUniquePath(jar, programFiles, seenProgramPaths);
        }
        // When the project uses Kotlin, the stdlib classes must be dexed and packaged
        // so kotlin runtime symbols are available at runtime (deduplicated against built-in libs).
        if (usesKotlin) {
            File kotlinStdlib = BuiltInLibraries.getLibraryClassesJarPath(BuiltInLibraries.JETBRAINS_KOTLIN_STDLIB);
            if (kotlinStdlib.exists()) {
                addUniquePath(kotlinStdlib, programFiles, seenProgramPaths);
            }
        }

        List<Path> libraryFiles = new ArrayList<>();
        File androidJar = resolveAndroidJar();
        libraryFiles.add(androidJar.toPath());

        int minApi = 21; // safe default for modern Android builds
        try {
            String minSdkStr = project.getMinSdkVersion();
            if (minSdkStr != null && !minSdkStr.isEmpty()) {
                minApi = Integer.parseInt(minSdkStr);
            }
        } catch (NumberFormatException ignored) {}

        if (project.isMinifyEnabled()) {
            runR8(programFiles, libraryFiles, minApi);
        } else {
            D8.run(D8Command.builder()
                    .setMode(CompilationMode.RELEASE)
                    .setIntermediate(false)
                    .setMinApiLevel(minApi)
                    .addLibraryFiles(libraryFiles)
                    .setOutput(dexDir.toPath(), OutputMode.DexIndexed)
                    .addProgramFiles(programFiles)
                    .build());
        }

        if (!outputDex.exists()) {
            throw new Exception("DEX creation failed. classes.dex not found.");
        }
    }

    private void runR8(List<Path> programFiles, List<Path> libraryFiles, int minApi) throws Exception {
        List<Path> configFiles = new ArrayList<>();
        Set<String> seenConfigs = new HashSet<>();

        try {
            String androidRulesPath = ProguardHandler.ANDROID_PROGUARD_RULES_PATH;
            if (androidRulesPath != null && !androidRulesPath.isEmpty()) {
                File androidRules = new File(androidRulesPath);
                if (androidRules.isFile()) {
                    addUniquePath(androidRules, configFiles, seenConfigs);
                }
            }
        } catch (Throwable ignored) {
            // Fall back to inline rules + aapt_rules.pro if external storage is unavailable
        }

        File aaptRules = new File(binDir, "aapt_rules.pro");
        if (aaptRules.isFile()) {
            addUniquePath(aaptRules, configFiles, seenConfigs);
        }

        for (BuiltInLibraries.BuiltInLibrary lib : getResolvedBuiltInLibraries()) {
            File libProguard = BuiltInLibraries.getLibraryProguardConfiguration(lib.getName());
            if (libProguard.isFile()) {
                addUniquePath(libProguard, configFiles, seenConfigs);
            }
        }

        File resolvedAarRoot = new File(project.getResolvedAarPath());
        if (resolvedAarRoot.exists() && resolvedAarRoot.isDirectory()) {
            File[] aarDirs = resolvedAarRoot.listFiles();
            if (aarDirs != null) {
                Arrays.sort(aarDirs, (a, b) -> a.getName().compareTo(b.getName()));
                for (File aarDir : aarDirs) {
                    if (!aarDir.isDirectory()) continue;
                    File aarProguard = new File(aarDir, "proguard.txt");
                    if (aarProguard.isFile()) {
                        addUniquePath(aarProguard, configFiles, seenConfigs);
                    }
                }
            }
        }

        File projectProguard = new File(project.getProguardRulesPath());
        if (projectProguard.isFile()) {
            addUniquePath(projectProguard, configFiles, seenConfigs);
        }

        List<String> inlineRules = Arrays.asList(
                "-keep public class * extends android.app.Application",
                "-keep public class * extends android.app.Activity",
                "-keep public class * extends android.app.Service",
                "-keep public class * extends android.content.BroadcastReceiver",
                "-keep public class * extends android.content.ContentProvider",
                "-keepclassmembers class **.R$* { public static <fields>; }",
                "-dontwarn",
                "-ignorewarnings"
        );

        R8Command.Builder builder = R8Command.builder()
                .addProgramFiles(programFiles)
                .addProguardConfiguration(inlineRules, Origin.unknown())
                .setProguardMapOutputPath(new File(binDir, "mapping.txt").toPath())
                .setMinApiLevel(minApi)
                .addLibraryFiles(libraryFiles)
                .setOutput(dexDir.toPath(), OutputMode.DexIndexed)
                .setMode(CompilationMode.RELEASE);

        if (!configFiles.isEmpty()) {
            builder.addProguardConfigurationFiles(configFiles);
        }

        R8.run(builder.build());
    }

    private File buildApk() throws Exception {
        File resourcesApk = new File(binDir, "resources.apk");
        File unsignedApk = new File(binDir, "unsigned.apk");
        File alignedApk = new File(binDir, "aligned.apk");
        Set<String> addedEntries = new HashSet<>();

        // Build the unsigned APK by copying all entries from resources.apk
        // and adding classes.dex, native libraries, and JAR resources
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(resourcesApk));
             ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(unsignedApk))) {

            // Copy all existing entries preserving compression method and metadata
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                if (!addedEntries.add(entry.getName())) {
                    zis.closeEntry();
                    continue;
                }
                ZipEntry outEntry = new ZipEntry(entry.getName());
                outEntry.setTime(entry.getTime());

                if (entry.getMethod() == ZipEntry.STORED) {
                    // For STORED entries, we must set size, compressed size, and CRC
                    outEntry.setMethod(ZipEntry.STORED);
                    outEntry.setSize(entry.getSize());
                    outEntry.setCompressedSize(entry.getCompressedSize());
                    outEntry.setCrc(entry.getCrc());
                } else {
                    outEntry.setMethod(ZipEntry.DEFLATED);
                }

                zos.putNextEntry(outEntry);
                int read;
                while ((read = zis.read(buffer)) != -1) {
                    zos.write(buffer, 0, read);
                }
                zos.closeEntry();
                zis.closeEntry();
            }

            // Add every dex file D8 produced (classes.dex, classes2.dex, ...).
            for (File dex : getSortedDexFiles()) {
                if (!addedEntries.add(dex.getName())) continue;
                ZipEntry dexEntry = new ZipEntry(dex.getName());
                dexEntry.setMethod(ZipEntry.DEFLATED);
                zos.putNextEntry(dexEntry);
                try (FileInputStream dexIn = new FileInputStream(dex)) {
                    int read;
                    while ((read = dexIn.read(buffer)) != -1) {
                        zos.write(buffer, 0, read);
                    }
                }
                zos.closeEntry();
            }

            // Package native libraries from app/src/main/jniLibs and resolved AARs
            addNativeLibrariesToZip(zos, "lib", addedEntries, buffer);

            // Package non-class Java resources from dependency JARs
            addJarResourcesToZip(zos, "", addedEntries, buffer);
        }

        // Zipalign the APK (4-byte alignment) for resources.arsc and uncompressed entries
        zipalign(unsignedApk, alignedApk);

        return alignedApk;
    }

    private File packageAppBundle() throws Exception {
        File resourcesApk = new File(binDir, "resources.apk");
        File mainModuleArchive = new File(binDir, "module-main.zip");
        String safeName = (project.getProjectName() != null && !project.getProjectName().isEmpty())
                ? project.getProjectName().replaceAll("[^a-zA-Z0-9._-]", "_")
                : "app";
        File outputBundle = new File(binDir, safeName + ".aab");

        List<String> uncompressedGlobs = new ArrayList<>();
        Set<String> addedEntries = new HashSet<>();
        byte[] buffer = new byte[8192];

        try (ZipOutputStream moduleZip = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(mainModuleArchive)))) {

            // 1. Add DEX files under dex/
            for (File dex : getSortedDexFiles()) {
                String entryName = "dex/" + dex.getName();
                if (!addedEntries.add(entryName)) continue;
                moduleZip.putNextEntry(new ZipEntry(entryName));
                try (FileInputStream dexIn = new FileInputStream(dex)) {
                    int read;
                    while ((read = dexIn.read(buffer)) != -1) {
                        moduleZip.write(buffer, 0, read);
                    }
                }
                moduleZip.closeEntry();
            }

            // 2. Re-map proto-format resources.apk entries into bundle module layout
            try (ZipInputStream apkRes = new ZipInputStream(new FileInputStream(resourcesApk))) {
                ZipEntry entry;
                while ((entry = apkRes.getNextEntry()) != null) {
                    String name = entry.getName();
                    String mappedName;
                    if (name.startsWith("assets/")) {
                        mappedName = "assets/" + name.substring("assets/".length());
                    } else if (name.startsWith("res/")) {
                        mappedName = "res/" + name.substring("res/".length());
                    } else if (name.equals("AndroidManifest.xml")) {
                        mappedName = "manifest/AndroidManifest.xml";
                    } else if (name.equals("resources.pb")) {
                        mappedName = "resources.pb";
                    } else {
                        mappedName = "root/" + name;
                    }

                    if (!addedEntries.add(mappedName)) {
                        apkRes.closeEntry();
                        continue;
                    }

                    ZipEntry outEntry = new ZipEntry(mappedName);
                    if (entry.getMethod() == ZipEntry.STORED) {
                        outEntry.setMethod(ZipEntry.STORED);
                        outEntry.setSize(entry.getSize());
                        outEntry.setCompressedSize(entry.getCompressedSize());
                        outEntry.setCrc(entry.getCrc());
                        uncompressedGlobs.add(name);
                    } else {
                        outEntry.setMethod(ZipEntry.DEFLATED);
                    }
                    moduleZip.putNextEntry(outEntry);
                    int read;
                    while ((read = apkRes.read(buffer)) != -1) {
                        moduleZip.write(buffer, 0, read);
                    }
                    moduleZip.closeEntry();
                    apkRes.closeEntry();
                }
            }

            // 3. Add native libraries under lib/
            addNativeLibrariesToZip(moduleZip, "lib", addedEntries, buffer);

            // 4. Add non-class JAR resources under root/
            addJarResourcesToZip(moduleZip, "root/", addedEntries, buffer);
        }

        BuildBundleCommand.builder()
                .setModulesPaths(ImmutableList.of(mainModuleArchive.toPath()))
                .setOverwriteOutput(true)
                .setOutputPath(outputBundle.toPath())
                .setBundleConfig(Config.BundleConfig.newBuilder()
                        .setCompression(Config.Compression.newBuilder()
                                .addAllUncompressedGlob(uncompressedGlobs).build())
                        .build())
                .build()
                .execute();

        if (!outputBundle.exists() || outputBundle.length() == 0) {
            throw new Exception("App Bundle creation failed: output .aab is missing or empty.");
        }
        return outputBundle;
    }

    private File[] getSortedDexFiles() throws Exception {
        File[] dexFiles = dexDir.listFiles((dir, name) -> name.matches("classes\\d*\\.dex"));
        if (dexFiles == null || dexFiles.length == 0) {
            throw new Exception("No dex files found to package.");
        }
        java.util.Arrays.sort(dexFiles, (f1, f2) -> {
            String n1 = f1.getName();
            String n2 = f2.getName();
            if (n1.equals("classes.dex")) return -1;
            if (n2.equals("classes.dex")) return 1;
            try {
                int num1 = Integer.parseInt(n1.substring(7, n1.length() - 4));
                int num2 = Integer.parseInt(n2.substring(7, n2.length() - 4));
                return Integer.compare(num1, num2);
            } catch (Exception e) {
                return n1.compareTo(n2);
            }
        });
        return dexFiles;
    }

    private void addNativeLibrariesToZip(
            ZipOutputStream zos,
            String libPrefix,
            Set<String> addedEntries,
            byte[] buffer) throws Exception {
        List<File> jniRoots = new ArrayList<>();
        File projectJni = new File(project.getJniLibsPath());
        if (projectJni.exists() && projectJni.isDirectory()) {
            jniRoots.add(projectJni);
        }
        File resolvedAarRoot = new File(project.getResolvedAarPath());
        if (resolvedAarRoot.exists() && resolvedAarRoot.isDirectory()) {
            File[] aarDirs = resolvedAarRoot.listFiles();
            if (aarDirs != null) {
                java.util.Arrays.sort(aarDirs, (a, b) -> a.getName().compareTo(b.getName()));
                for (File aarDir : aarDirs) {
                    File jniDir = new File(aarDir, "jni");
                    if (jniDir.exists() && jniDir.isDirectory()) {
                        jniRoots.add(jniDir);
                    }
                }
            }
        }

        for (File jniRoot : jniRoots) {
            File[] abiDirs = jniRoot.listFiles();
            if (abiDirs == null) continue;
            for (File abiDir : abiDirs) {
                if (!abiDir.isDirectory()) continue;
                File[] soFiles = abiDir.listFiles();
                if (soFiles == null) continue;
                for (File soFile : soFiles) {
                    if (!soFile.isFile()) continue;
                    String entryName = libPrefix + "/" + abiDir.getName() + "/" + soFile.getName();
                    if (!addedEntries.add(entryName)) continue;
                    ZipEntry zipEntry = new ZipEntry(entryName);
                    zipEntry.setMethod(ZipEntry.DEFLATED);
                    zos.putNextEntry(zipEntry);
                    try (FileInputStream in = new FileInputStream(soFile)) {
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            zos.write(buffer, 0, read);
                        }
                    }
                    zos.closeEntry();
                }
            }
        }
    }

    private void addJarResourcesToZip(
            ZipOutputStream zos,
            String entryPrefix,
            Set<String> addedEntries,
            byte[] buffer) throws Exception {
        for (File jar : getLibraryJars()) {
            try (ZipInputStream jis = new ZipInputStream(new FileInputStream(jar))) {
                ZipEntry jarEntry;
                while ((jarEntry = jis.getNextEntry()) != null) {
                    String path = jarEntry.getName();
                    if (jarEntry.isDirectory()
                            || path.endsWith(".class")
                            || path.equals("META-INF/MANIFEST.MF")
                            || path.startsWith("META-INF/") && (path.endsWith(".SF")
                                || path.endsWith(".DSA")
                                || path.endsWith(".RSA")
                                || path.endsWith(".EC"))) {
                        jis.closeEntry();
                        continue;
                    }
                    String targetName = entryPrefix + path;
                    if (!addedEntries.add(targetName)) {
                        jis.closeEntry();
                        continue;
                    }
                    ZipEntry outEntry = new ZipEntry(targetName);
                    outEntry.setMethod(ZipEntry.DEFLATED);
                    zos.putNextEntry(outEntry);
                    int read;
                    while ((read = jis.read(buffer)) != -1) {
                        zos.write(buffer, 0, read);
                    }
                    zos.closeEntry();
                    jis.closeEntry();
                }
            }
        }
    }

    private void zipalign(File input, File output) throws Exception {
        try (RandomAccessFile in = new RandomAccessFile(input, "r");
             FileOutputStream out = new FileOutputStream(output)) {
            ZipAlign.alignZip(in, out);
        } catch (IOException | InvalidZipException e) {
            throw new Exception("Zipalign failed: " + e.getMessage(), e);
        }

        if (!output.exists() || output.length() == 0) {
            throw new Exception("Zipalign produced no output APK.");
        }
    }

    private File signApk(File apk) throws Exception {
        File signedApk = new File(binDir, "signed.apk");
        mod.jbk.util.TestkeySignBridge.signWithTestkey(
                apk.getAbsolutePath(), signedApk.getAbsolutePath());
        if (!signedApk.exists() || signedApk.length() == 0) {
            throw new Exception("APK signing failed: signed APK is missing or empty.");
        }
        return signedApk;
    }

    private List<BuiltInLibraries.BuiltInLibrary> getResolvedBuiltInLibraries() {
        if (cachedBuiltInLibraries == null) {
            cachedBuiltInLibraries = CodeProjectBuiltInLibraries.resolveTransitive(project.getBuiltInLibraries());
        }
        return cachedBuiltInLibraries;
    }

    private List<File> getLibraryJars() {
        if (cachedLibraryJars != null) {
            return cachedLibraryJars;
        }
        List<File> jars = new ArrayList<>();
        Set<String> seenPaths = new HashSet<>();

        // Scan libs/ (manually placed JARs)
        scanJarsInDir(new File(project.getLibsPath()), jars, seenPaths);

        // Scan libs/resolved/ (auto-resolved dependencies)
        scanJarsInDir(new File(project.getLibsPath(), "resolved"), jars, seenPaths);

        // Add selected built-in offline library JARs (with transitive dependencies)
        for (BuiltInLibraries.BuiltInLibrary lib : getResolvedBuiltInLibraries()) {
            File libJar = BuiltInLibraries.getLibraryClassesJarPath(lib.getName());
            if (libJar.isFile()) {
                addUniqueFile(libJar, jars, seenPaths);
            }
        }

        cachedLibraryJars = jars;
        return cachedLibraryJars;
    }

    private void scanJarsInDir(File dir, List<File> jars, Set<String> seenPaths) {
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().endsWith(".jar")) {
                        addUniqueFile(file, jars, seenPaths);
                    }
                }
            }
        }
    }

    private static void addUniqueFile(File file, List<File> out, Set<String> seenPaths) {
        String key = canonicalKey(file);
        if (seenPaths.add(key)) {
            out.add(file);
        }
    }

    private static void addUniquePath(File file, List<Path> out, Set<String> seenPaths) {
        String key = canonicalKey(file);
        if (seenPaths.add(key)) {
            out.add(file.toPath());
        }
    }

    private static String canonicalKey(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }

    public interface BuildProgressListener {
        void onProgress(String message, int step);
    }
}
