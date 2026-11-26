# Development Guide

This document explains the architecture of this fork (`ide.sketchware`), how to
build and test it, and the conventions contributors should follow.

> If you only want to build the app, jump to [Building](#2-building).

---

## 1. What this fork is

This is a fork of Sketchware Pro migrated from the legacy drag-and-drop
block-based app builder into a full-code **Code Project IDE**:

- Full file explorer, multi-tab Sora Editor (Java, Kotlin, XML), inline
  compiler diagnostics, search & replace (single-file and project-wide),
  formatting, integrated Layout Preview, Resource Editor, and Logcat panel.
- Self-contained on-device Android build pipeline (`CodeProjectBuilder`):
  AAPT2 (with built-in offline library resource caching + AAR `res/`, `assets/`,
  `--extra-packages`, and `--proguard` rules), optional `ViewBindingBuilder`
  code generation, `K2JVMCompiler` (Kotlin), ECJ (Java `1.7`–`11` + warnings
  toggle, custom `android.jar`, and extra classpath via Sketchware Pro's
  `BuildSettings` / `BuildSettingsBottomSheet`), optional **StringFog** string
  obfuscation (`StringFogClassInjector`), D8 or R8 shrinking/obfuscation
  (`proguard-rules.pro` + library consumer rules), native library (`jniLibs/` +
  AAR `jni/`) packaging, pure-Java `ZipAlign`, APK signing (testkey and custom
  release keystore), Android App Bundle (`.aab`) export, and optional root
  auto-install / auto-launch (`libsu`).
- Curated offline **Built-in Libraries** picker (`CodeProjectBuiltInLibraries`
  backed by `BuiltInLibraries`) with cycle-safe transitive resolution, plus
  Maven dependency resolution via `CosmicDependencyBridge`.
- Project settings (ViewBinding & R8/ProGuard toggles, custom app icon sync to
  `res/mipmap/ic_launcher.png` and `AndroidManifest.xml`), **Import Material
  Icon** (`ImportIconActivity` + `SvgUtils` to `res/drawable/*.xml`), safe
  source ZIP export, and full `.swb` backup/restore preserving `app/src` and
  manually placed `app/libs`.

The application id and namespace were renamed from `pro.sketchware` to
**`ide.sketchware`**. New first-party code lives under `ide.sketchware.*`.

| Setting       | Value            |
| ------------- | ---------------- |
| applicationId | `ide.sketchware` |
| namespace     | `ide.sketchware` |
| compileSdk    | 36               |
| targetSdk     | 28               |
| minSdk        | 26               |
| JDK           | 17+              |

---

## 2. Building & Testing

The project builds with Gradle 8.13 and Android Gradle Plugin 8.12 (`mise.toml`
pins `java = "17"`, and JDK 17–21 works with the wrapper).

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

The **iron rule** for any change: `./gradlew assembleDebug testDebugUnitTest`
must report `BUILD SUCCESSFUL` after every commit.

### Android E2E Testing (GitHub Actions / Local Emulator / redroid)

Automated UI end-to-end flows live in `maestro/` and are executed on a real
Android Emulator (API 31, KVM) via `.github/workflows/android-e2e.yml` on
pull requests or manual `workflow_dispatch` runs (uploading screenshots,
JUnit XML reports, and filtered Logcat under `artifacts/android-e2e/`).

To run locally against a Binder-enabled Linux host (`redroid`) or any connected
Android device/emulator:

```bash
./scripts/android-start.sh      # Optional: start redroid on Binder-enabled Linux host
./scripts/android-status.sh     # Verify ADB device & boot completion
./scripts/android-install.sh    # Install debug APK & grant storage permissions
./scripts/android-test.sh       # Run all maestro/*.yaml flows & collect artifacts
./scripts/android-stop.sh       # Optional: stop redroid container
```

---

## 3. Package map

### First-party / actively maintained — `ide.sketchware.*`
| Package | Role |
| ------- | ---- |
| `ide.sketchware.codeproject.*` | Core Code Project IDE (model, template, builder, compiler error parser, Maven/AAR dependency bridge, editor UI, project settings). |
| `ide.sketchware.activities.resourceseditor.*` | Standalone resource editor (strings, colors, styles, themes, arrays), wired to CodeProject's `res/` root. |
| `ide.sketchware.activities.preview.*` | Layout XML preview (`LayoutPreviewActivity`), backed by `ViewPane` with explicit resource root support. |
| `ide.sketchware.activities.*` | Main project list (`MainActivity`, `ProjectsFragment`), settings, about, icon creator, import icon. |
| `ide.sketchware.util`, `ide.sketchware.utility`, `ide.sketchware.xml`, `ide.sketchware.lib` | Shared helpers (`FileUtil`, `BinaryExecutor`, `XmlBuilder`, `EditorUtils`, …). |

### Storage & shared build infrastructure — `a.a.a.*` & `mod.*`
- `a.a.a.*`: Core project metadata/path storage (`lC`, `wq`, `yB`, `oB`, `DB`, `yq`) and legacy code-generation support still referenced by `app/libs/*.jar` or shared helpers.
- `mod.jbk.build.*`, `mod.hey.studios.compiler.*`, `mod.hey.studios.code.*`, `mod.hey.studios.project.backup.*`, `mod.bobur.*`, `mod.khaled.logcat.*`, `mod.alucard.tn.apksigner.*`: Shared compiler assets (`BuiltInLibraries`), Kotlin diagnostics, Sora editor preferences/themes (`SrcCodeEditor`), `.swb` backup/restore (`BackupFactory`), vector drawable rendering (`VectorDrawableLoader`/`VectorDrawableParser`), logcat reader, and APK signing.
- `mod.hilal.saif.activities.tools.{AppSettings, ConfigActivity}`: General app tools and preferences (block-editor manager items and block preferences have been removed).

> **Maven dependency resolution already exists.** `mod.pranav.dependency.resolver.DependencyResolver`
> wraps `com.github.Cosmic-Ide:DependencyResolver` (`org.cosmic.ide.dependency.resolver`).
> The Code Project IDE consumes it through `ide.sketchware.codeproject.dependencies.CosmicDependencyBridge`,
> which places resolved JARs in `app/libs/resolved/` and extracts AAR `res/`,
> `assets/`, `jni/`, and `AndroidManifest.xml` into `app/libs/resolved_aar/`.
> **Reuse this — do not hand-roll a Maven resolver.**

### Prebuilt JARs — `app/libs/*.jar`
`com.besome.sketch-classes.jar`, `base_libs.jar`, `a.a.a-*-classes.jar`, etc.
contain compiled classes that the source tree extends or overrides. References
from these JARs are **invisible to source-level grep**, so always scan
`app/libs/*.jar` before deleting any legacy class.

---

## 4. Migration & Cleanup History

| Phase | Outcome |
| ----- | ------- |
| Package & IDE foundation | Renamed `pro.sketchware` → `ide.sketchware`, built `ide.sketchware.codeproject.*`, wired `CosmicDependencyBridge` over `Cosmic-Ide`. |
| Drag-and-drop retirement | Removed `ViewEditorFragment`, `ViewEditor`, `DesignDrawer`, `WidgetsCreatorManager`, and transitioned new project creation to `CreateCodeProjectActivity`. |
| Full migration completion | Fixed CodeProject data-loss paths in `ExportProjectActivity` (APK/AAB/source ZIP) and `BackupFactory` (`.swb` backup/restore of `app/src` + `app/libs`); routed CodeProject settings in `ProjectsAdapter` to `ProjectSettingsActivity` with custom app icon support; removed all `DesignActivity.sc_id` coupling and deleted `DesignActivity` & `LogicEditorActivity`; cleaned legacy block managers from `AppSettings`/`ConfigActivity` and removed the defunct Store tab; peeled **188 dead legacy Java classes**, **41 dead `<activity>` declarations** from `AndroidManifest.xml`, and orphaned XML layouts/menus; upgraded `CodeProjectBuilder` to Java 8 (`-1.8`) with full AAR `res/`/`assets/`/`jni/`/`--extra-packages` linking and AAB packaging; expanded unit test suite. |

---

## 5. Safe dead-code removal methodology ("orphan peeling")

When removing further legacy classes in the future, a class is safe to delete only
if it is unreachable from all live roots:

1. No declaration in `AndroidManifest.xml`.
2. No keep rule in `app/proguard-rules.pro`.
3. No class/binary reference inside `app/libs/*.jar`.
4. No reference from any live `.java`, `.kt`, or `.xml` file (including ViewBinding classes and custom view tags).

Always verify `./gradlew assembleDebug testDebugUnitTest` after each batch.

---

## 6. Contributing conventions

- Prefer **Java** over Kotlin for new code unless Kotlin is clearly warranted
  (the build supports both).
- Put new first-party features under `ide.sketchware.*`, respecting existing
  directory/file naming.
- **Reuse existing infrastructure before building new** — search `mod.*`,
  `app/libs/*.jar`, and `gradle/libs.versions.toml` first.
- Commit message prefixes: `feat:`, `fix:`, `style:`, `refactor:`, `test:`,
  `docs:`, `chore:`.
- Always run `./gradlew assembleDebug testDebugUnitTest` before committing.
