package ide.sketchware.codeproject.dependencies;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import mod.jbk.build.BuiltInLibraries;

/**
 * Curated offline built-in libraries bundled in Sketchware Pro's assets, plus
 * deterministic transitive resolution over {@link BuiltInLibraries#KNOWN_BUILT_IN_LIBRARIES}.
 */
public final class CodeProjectBuiltInLibraries {

    public static final class Option {
        public final String libraryName;
        public final String displayName;

        public Option(String libraryName, String displayName) {
            this.libraryName = libraryName;
            this.displayName = displayName;
        }
    }

    private static final List<Option> POPULAR_OPTIONS = Collections.unmodifiableList(createOptions());

    private CodeProjectBuiltInLibraries() {
    }

    private static List<Option> createOptions() {
        List<Option> list = new ArrayList<>();
        list.add(new Option(BuiltInLibraries.ANDROIDX_APPCOMPAT, "AndroidX AppCompat (" + BuiltInLibraries.ANDROIDX_APPCOMPAT + ")"));
        list.add(new Option(BuiltInLibraries.MATERIAL, "Material Components (" + BuiltInLibraries.MATERIAL + ")"));
        list.add(new Option(BuiltInLibraries.ANDROIDX_CONSTRAINTLAYOUT, "ConstraintLayout (" + BuiltInLibraries.ANDROIDX_CONSTRAINTLAYOUT + ")"));
        list.add(new Option(BuiltInLibraries.ANDROIDX_RECYCLERVIEW, "RecyclerView (" + BuiltInLibraries.ANDROIDX_RECYCLERVIEW + ")"));
        list.add(new Option(BuiltInLibraries.ANDROIDX_CARDVIEW, "CardView (" + BuiltInLibraries.ANDROIDX_CARDVIEW + ")"));
        list.add(new Option(BuiltInLibraries.ANDROIDX_SWIPEREFRESHLAYOUT, "SwipeRefreshLayout (" + BuiltInLibraries.ANDROIDX_SWIPEREFRESHLAYOUT + ")"));
        list.add(new Option(BuiltInLibraries.ANDROIDX_VIEWPAGER2, "ViewPager2 (" + BuiltInLibraries.ANDROIDX_VIEWPAGER2 + ")"));
        list.add(new Option(BuiltInLibraries.ANDROIDX_COORDINATORLAYOUT, "CoordinatorLayout (" + BuiltInLibraries.ANDROIDX_COORDINATORLAYOUT + ")"));
        list.add(new Option(BuiltInLibraries.ANDROIDX_CORE_KTX, "AndroidX Core KTX (" + BuiltInLibraries.ANDROIDX_CORE_KTX + ")"));
        list.add(new Option(BuiltInLibraries.JETBRAINS_KOTLINX_COROUTINES_ANDROID, "Kotlinx Coroutines (" + BuiltInLibraries.JETBRAINS_KOTLINX_COROUTINES_ANDROID + ")"));
        list.add(new Option(BuiltInLibraries.GLIDE, "Glide (" + BuiltInLibraries.GLIDE + ")"));
        list.add(new Option(BuiltInLibraries.GSON, "Gson (" + BuiltInLibraries.GSON + ")"));
        list.add(new Option(BuiltInLibraries.OKHTTP_ANDROID, "OkHttp (" + BuiltInLibraries.OKHTTP_ANDROID + ")"));
        list.add(new Option(BuiltInLibraries.LOTTIE, "Lottie (" + BuiltInLibraries.LOTTIE + ")"));
        list.add(new Option(BuiltInLibraries.CIRCLEIMAGEVIEW, "CircleImageView (" + BuiltInLibraries.CIRCLEIMAGEVIEW + ")"));
        list.add(new Option(BuiltInLibraries.FIREBASE_AUTH, "Firebase Auth (" + BuiltInLibraries.FIREBASE_AUTH + ")"));
        list.add(new Option(BuiltInLibraries.FIREBASE_DATABASE, "Firebase Realtime Database (" + BuiltInLibraries.FIREBASE_DATABASE + ")"));
        list.add(new Option(BuiltInLibraries.FIREBASE_STORAGE, "Firebase Storage (" + BuiltInLibraries.FIREBASE_STORAGE + ")"));
        list.add(new Option(BuiltInLibraries.FIREBASE_MESSAGING, "Firebase Messaging (" + BuiltInLibraries.FIREBASE_MESSAGING + ")"));
        list.add(new Option(BuiltInLibraries.PLAY_SERVICES_ADS, "Google Mobile Ads (" + BuiltInLibraries.PLAY_SERVICES_ADS + ")"));
        list.add(new Option(BuiltInLibraries.PLAY_SERVICES_LOCATION, "Play Services Location (" + BuiltInLibraries.PLAY_SERVICES_LOCATION + ")"));
        list.add(new Option(BuiltInLibraries.PLAY_SERVICES_MAPS, "Play Services Maps (" + BuiltInLibraries.PLAY_SERVICES_MAPS + ")"));
        return list;
    }

    public static List<Option> getSelectableOptions() {
        return POPULAR_OPTIONS;
    }

    public static String serialize(List<String> libraryNames) {
        if (libraryNames == null || libraryNames.isEmpty()) return "";
        Set<String> unique = new LinkedHashSet<>();
        for (String name : libraryNames) {
            if (name != null && !name.trim().isEmpty()) {
                unique.add(name.trim());
            }
        }
        return String.join(",", unique);
    }

    public static List<String> deserialize(String csv) {
        List<String> result = new ArrayList<>();
        if (csv == null || csv.trim().isEmpty()) return result;
        Set<String> seen = new LinkedHashSet<>();
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && seen.add(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * Expands selected built-in library names to include all transitive dependencies
     * declared in {@link BuiltInLibraries#KNOWN_BUILT_IN_LIBRARIES}. Handles cyclic
     * definitions (e.g. appcompat <-> appcompat-resources) safely.
     */
    public static List<BuiltInLibraries.BuiltInLibrary> resolveTransitive(List<String> selectedNames) {
        if (selectedNames == null || selectedNames.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, BuiltInLibraries.BuiltInLibrary> resolved = new LinkedHashMap<>();
        Set<String> visited = new LinkedHashSet<>();
        for (String name : selectedNames) {
            visit(name, visited, resolved);
        }
        return new ArrayList<>(resolved.values());
    }

    private static void visit(
            String name,
            Set<String> visited,
            Map<String, BuiltInLibraries.BuiltInLibrary> resolved) {
        if (name == null || name.trim().isEmpty()) return;
        String trimmed = name.trim();
        if (!visited.add(trimmed)) return;

        Optional<BuiltInLibraries.BuiltInLibrary> opt = BuiltInLibraries.BuiltInLibrary.ofName(trimmed);
        if (!opt.isPresent()) return;

        BuiltInLibraries.BuiltInLibrary lib = opt.get();
        for (String dep : lib.getDependencyNames()) {
            visit(dep, visited, resolved);
        }
        resolved.put(lib.getName(), lib);
    }
}
