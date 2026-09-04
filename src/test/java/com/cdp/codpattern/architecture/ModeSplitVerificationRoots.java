package com.cdp.codpattern.architecture;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Shared multi-root path resolver for the physical-split verification programs. */
public final class ModeSplitVerificationRoots {
    public static final String PRODUCTION_JAVA_ROOTS = "modeSplit.productionJavaRoots";
    public static final String PRODUCTION_RESOURCE_ROOTS = "modeSplit.productionResourceRoots";
    public static final String TEST_JAVA_ROOTS = "modeSplit.testJavaRoots";
    public static final String TEST_RESOURCE_ROOTS = "modeSplit.testResourceRoots";
    public static final String MAIN_CLASS_ROOTS = "modeSplit.mainClassRoots";

    private static final Path REPOSITORY_ROOT = findRepositoryRoot();

    private ModeSplitVerificationRoots() {
    }

    public static Path repositoryRoot() {
        return REPOSITORY_ROOT;
    }

    public static List<Path> productionJavaRoots() {
        return configuredRoots(PRODUCTION_JAVA_ROOTS, List.of("src/main/java"));
    }

    public static List<Path> productionResourceRoots() {
        return configuredRoots(PRODUCTION_RESOURCE_ROOTS, List.of("src/main/resources"));
    }

    public static List<Path> testJavaRoots() {
        return configuredRoots(TEST_JAVA_ROOTS, List.of("src/test/java"));
    }

    public static List<Path> testResourceRoots() {
        return configuredRoots(TEST_RESOURCE_ROOTS, List.of("src/test/resources"));
    }

    public static List<Path> mainClassRoots() {
        return configuredRoots(MAIN_CLASS_ROOTS, List.of("build/classes/java/main"));
    }

    public static Path resolveRepositoryPath(Path path) {
        Path absolute = path.isAbsolute() ? path.normalize() : REPOSITORY_ROOT.resolve(path).normalize();
        if (Files.exists(absolute)) {
            return absolute;
        }
        String normalized = normalize(path.toString());
        Path resolved = resolveKnownRoot(normalized, "src/main/java/", productionJavaRoots());
        if (resolved != null) {
            return resolved;
        }
        resolved = resolveKnownRoot(normalized, "src/main/resources/", productionResourceRoots());
        if (resolved != null) {
            return resolved;
        }
        resolved = resolveKnownRoot(normalized, "src/test/java/", testJavaRoots());
        if (resolved != null) {
            return resolved;
        }
        resolved = resolveKnownRoot(normalized, "src/test/resources/", testResourceRoots());
        return resolved == null ? absolute : resolved;
    }

    public static Path productionJavaSource(String className) {
        String relative = className.replace('.', '/') + ".java";
        return requireUnique(relative, productionJavaRoots(), "production Java source " + className);
    }

    public static Path testResource(String relative) {
        return requireUnique(normalize(relative), testResourceRoots(), "test resource " + relative);
    }

    public static List<LocatedFile> productionJavaFiles() throws IOException {
        return files(productionJavaRoots(), ".java", true);
    }

    public static List<LocatedFile> productionResourceFiles() throws IOException {
        // Separate Forge artifacts intentionally repeat per-JAR paths such as META-INF/mods.toml
        // and pack.mcmeta. Preserve both physical files; final artifact gates decide which
        // duplicates are allowed to ship.
        return files(productionResourceRoots(), null, false);
    }

    public static String normalize(String value) {
        return value.replace('\\', '/');
    }

    private static List<LocatedFile> files(
            List<Path> roots,
            String suffix,
            boolean rejectDuplicateRelativePaths
    ) throws IOException {
        Map<String, Path> byRelativePath = new LinkedHashMap<>();
        List<LocatedFile> result = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                    String relative = normalize(root.relativize(path).toString());
                    if (suffix != null && !relative.endsWith(suffix)) {
                        continue;
                    }
                    Path duplicate = byRelativePath.putIfAbsent(relative, path);
                    if (rejectDuplicateRelativePaths && duplicate != null) {
                        throw new AssertionError("Duplicate relative path across verification roots: "
                                + relative + " in " + duplicate + " and " + path);
                    }
                    result.add(new LocatedFile(path, relative));
                }
            }
        }
        result.sort((left, right) -> {
            int relative = left.relativePath().compareTo(right.relativePath());
            return relative != 0 ? relative : left.path().compareTo(right.path());
        });
        return List.copyOf(result);
    }

    private static Path requireUnique(String relative, List<Path> roots, String description) {
        List<Path> matches = roots.stream()
                .map(root -> root.resolve(relative).normalize())
                .filter(Files::isRegularFile)
                .toList();
        if (matches.size() != 1) {
            throw new AssertionError("Expected exactly one " + description + " across " + roots
                    + ", found " + matches);
        }
        return matches.get(0);
    }

    private static Path resolveKnownRoot(String path, String marker, List<Path> roots) {
        int markerIndex = path.indexOf(marker);
        if (markerIndex < 0) {
            return null;
        }
        String relative = path.substring(markerIndex + marker.length());
        List<Path> matches = roots.stream()
                .map(root -> root.resolve(relative).normalize())
                .filter(Files::exists)
                .toList();
        if (matches.size() > 1) {
            throw new AssertionError("Ambiguous verification path across roots: " + path + " -> " + matches);
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    private static List<Path> configuredRoots(String propertyName, List<String> defaults) {
        String configured = System.getProperty(propertyName, "").trim();
        List<String> values = configured.isEmpty()
                ? defaults
                : List.of(configured.split(java.util.regex.Pattern.quote(File.pathSeparator), -1));
        List<Path> roots = values.stream()
                .filter(value -> !value.isBlank())
                .map(Paths::get)
                .map(path -> path.isAbsolute() ? path.normalize() : REPOSITORY_ROOT.resolve(path).normalize())
                .toList();
        if (roots.isEmpty()) {
            throw new AssertionError("No roots configured for system property " + propertyName);
        }
        return roots;
    }

    private static Path findRepositoryRoot() {
        List<Path> starts = new ArrayList<>();
        starts.add(Paths.get("").toAbsolutePath().normalize());
        try {
            starts.add(Paths.get(ModeSplitVerificationRoots.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).toAbsolutePath().normalize());
        } catch (Exception ignored) {
            // The working-directory search below still provides the normal path.
        }
        for (Path start : starts) {
            Path current = start;
            while (current != null) {
                if (isMainRepository(current)) {
                    return current;
                }
                Path siblingMain = current.resolveSibling("codPattern");
                if (isMainRepository(siblingMain)) {
                    return siblingMain.normalize();
                }
                current = current.getParent();
            }
        }
        throw new AssertionError("Could not locate the codPattern repository root from " + starts);
    }

    private static boolean isMainRepository(Path path) {
        return path != null
                && Files.isRegularFile(path.resolve("settings.gradle"))
                && Files.isRegularFile(path.resolve(
                "src/main/java/com/cdp/codpattern/CodPatternConstants.java"));
    }

    public record LocatedFile(Path path, String relativePath) {
    }
}
