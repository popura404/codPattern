package com.cdp.codpattern.architecture;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Executable Round 3 resource, language-union, and physical ownership gate. */
public final class ModeSplitRound3ResourceCompatTest {
    private static final Path RESOURCE_OVERLAY = Path.of(
            "docs/mode-split/physical/round0/resource-ownership-overlay.tsv");
    private static final Path LANGUAGE_SEMANTIC_BASELINE = Path.of(
            "docs/mode-split/physical/round3/language-semantic-baseline.tsv");
    private static final Path LANGUAGE_OWNERSHIP_BASELINE = Path.of(
            "docs/mode-split/physical/round3/language-ownership-baseline.tsv");
    private static final Path MAIN_RESOURCES = Path.of("src/main/resources");
    private static final Path ADDON_RESOURCES = Path.of("../zombies-addon/src/main/resources");
    private static final Path MAIN_JAVA = Path.of("src/main/java");
    private static final Path ADDON_JAVA = Path.of("../zombies-addon/src/main/java");
    private static final List<String> LOCALES = List.of("en_us", "ja_jp", "zh_cn", "zh_tw");
    private static final Set<String> PER_JAR_METADATA = Set.of("META-INF/mods.toml", "pack.mcmeta");
    private static final Pattern FORMAT_TOKEN = Pattern.compile(
            "%(?:(?:\\d+)\\$)?[-#+ 0,(<]*\\d*(?:\\.\\d+)?[bBhHsScCdoxXeEfgGaAtTn%]");

    private ModeSplitRound3ResourceCompatTest() {
    }

    public static void main(String[] args) throws Exception {
        verifyOverlayOwnershipAndHashes();
        Map<String, LanguageFiles> languages = verifyLanguagePartition();
        verifyLiteralOwnership(languages.get("en_us"));
        verifyPhysicalBoundaries();
        System.out.println("PASS Round 3 resource/language partition: 44 semantic baseline resources, "
                + "29 moved addon resources, 4 disjoint language pairs, exact key/value union");
    }

    private static void verifyOverlayOwnershipAndHashes() throws Exception {
        int semanticResources = 0;
        int movedAddonResources = 0;
        int futureMainResources = 0;
        int partitionedLanguages = 0;
        for (String line : dataLines(RESOURCE_OVERLAY)) {
            String[] fields = line.split("\\t", -1);
            require(fields.length == 6, "invalid resource ownership row: " + line);
            String owner = fields[0];
            String oldPath = fields[1];
            String newPath = fields[2];
            String kind = fields[3];
            String expectedSha256 = fields[5];
            if ("TEST_FIXTURE".equals(kind)) {
                continue;
            }
            semanticResources++;
            switch (owner) {
                case "FUTURE_MAIN" -> {
                    futureMainResources++;
                    Path path = repositoryPath(oldPath);
                    require(Files.isRegularFile(path), "missing future-main resource: " + oldPath);
                    if (!PER_JAR_METADATA.contains(relativeResourcePath(oldPath))) {
                        requireEquals(expectedSha256, sha256(Files.readAllBytes(path)),
                                "future-main resource content drifted: " + oldPath);
                    }
                }
                case "ZOMBIES_ADDON" -> {
                    movedAddonResources++;
                    Path staleMainPath = ModeSplitVerificationRoots.repositoryRoot()
                            .resolve(oldPath).normalize();
                    require(!Files.exists(staleMainPath),
                            "stale addon-owned resource remains in main: " + oldPath);
                    Path target = repositoryPath(newPath);
                    require(Files.isRegularFile(target), "missing moved addon resource: " + newPath);
                    requireEquals(expectedSha256, sha256(Files.readAllBytes(target)),
                            "moved addon resource content drifted: " + newPath);
                }
                case "PARTITIONED_LANGUAGE" -> partitionedLanguages++;
                default -> throw new AssertionError("unexpected production-resource owner: " + owner);
            }
        }
        requireEquals(44, semanticResources, "semantic production-resource baseline count drifted");
        requireEquals(11, futureMainResources, "future-main resource count drifted");
        requireEquals(29, movedAddonResources, "moved addon resource count drifted");
        requireEquals(4, partitionedLanguages, "partitioned language count drifted");

        JsonObject whitelist = readJson(ADDON_RESOURCES.resolve(
                "data/tacz/tags/blocks/interact_key/whitelist.json"));
        require(!whitelist.get("replace").getAsBoolean(), "TaCZ whitelist replace flag drifted");
        List<String> values = new ArrayList<>();
        whitelist.getAsJsonArray("values").forEach(value -> values.add(value.getAsString()));
        requireEquals(7, values.size(), "TaCZ Zombies whitelist value count drifted");
        require(values.stream().allMatch(value -> value.startsWith("codpattern:zombies_")),
                "TaCZ whitelist acquired a non-Zombies registry ID: " + values);
    }

    private static Map<String, LanguageFiles> verifyLanguagePartition() throws Exception {
        Map<String, SemanticBaseline> semantic = readSemanticBaseline();
        Map<String, OwnershipBaseline> ownership = readOwnershipBaseline();
        Map<String, LanguageFiles> result = new HashMap<>();
        Set<String> commonAddonKeys = null;
        for (String locale : LOCALES) {
            Path mainPath = MAIN_RESOURCES.resolve("assets/codpattern/lang/" + locale + ".json");
            Path addonPath = ADDON_RESOURCES.resolve(
                    "assets/codpattern_zombies/lang/" + locale + ".json");
            require(Files.isRegularFile(mainPath), "missing main language file: " + mainPath);
            require(Files.isRegularFile(addonPath), "missing addon language file: " + addonPath);
            require(!Files.exists(MAIN_RESOURCES.resolve(
                            "assets/codpattern_zombies/lang/" + locale + ".json")),
                    "addon language namespace leaked into main: " + locale);
            require(!Files.exists(ADDON_RESOURCES.resolve(
                            "assets/codpattern/lang/" + locale + ".json")),
                    "legacy main language path leaked into addon: " + locale);

            TreeMap<String, String> main = stringMap(readJson(mainPath));
            TreeMap<String, String> addon = stringMap(readJson(addonPath));
            Set<String> overlap = new HashSet<>(main.keySet());
            overlap.retainAll(addon.keySet());
            require(overlap.isEmpty(), "main/addon language key overlap in " + locale + ": " + overlap);
            TreeMap<String, String> union = new TreeMap<>(main);
            union.putAll(addon);

            SemanticBaseline expectedSemantic = semantic.get(locale);
            OwnershipBaseline expectedOwnership = ownership.get(locale);
            require(expectedSemantic != null && expectedOwnership != null,
                    "missing Round 3 language baseline for " + locale);
            requireEquals(expectedSemantic.keyCount(), union.size(),
                    "language semantic-union key count drifted: " + locale);
            requireEquals(expectedSemantic.canonicalKeyValueSha256(), canonicalKeyValueSha256(union),
                    "language semantic-union key/value content drifted: " + locale);
            FormatCounts counts = formatCounts(union);
            requireEquals(expectedSemantic.formattedKeyCount(), counts.formattedKeyCount(),
                    "formatted translation key count drifted: " + locale);
            requireEquals(expectedSemantic.argumentTokenCount(), counts.argumentTokenCount(),
                    "translation argument count drifted: " + locale);

            requireEquals(expectedOwnership.mainKeyCount(), main.size(),
                    "main language ownership count drifted: " + locale);
            requireEquals(expectedOwnership.addonKeyCount(), addon.size(),
                    "addon language ownership count drifted: " + locale);
            requireEquals(expectedOwnership.mainKeysSha256(), keysSha256(main.keySet()),
                    "main language ownership set drifted: " + locale);
            requireEquals(expectedOwnership.addonKeysSha256(), keysSha256(addon.keySet()),
                    "addon language ownership set drifted: " + locale);
            if (commonAddonKeys == null) {
                commonAddonKeys = Set.copyOf(addon.keySet());
            } else {
                requireEquals(commonAddonKeys, addon.keySet(),
                        "addon language key set differs by locale: " + locale);
            }
            result.put(locale, new LanguageFiles(main, addon));
        }
        requireEquals(4, semantic.size(), "semantic language baseline locale count drifted");
        requireEquals(4, ownership.size(), "ownership language baseline locale count drifted");
        return Map.copyOf(result);
    }

    private static void verifyLiteralOwnership(LanguageFiles english) throws IOException {
        String mainSource = sourceText(MAIN_JAVA);
        String addonSource = sourceText(ADDON_JAVA);
        TreeMap<String, String> union = new TreeMap<>(english.main());
        union.putAll(english.addon());
        int mainOnly = 0;
        int addonOnly = 0;
        int shared = 0;
        for (String key : union.keySet()) {
            String literal = "\"" + key + "\"";
            boolean usedByMain = mainSource.contains(literal);
            boolean usedByAddon = addonSource.contains(literal);
            if (usedByMain) {
                require(english.main().containsKey(key),
                        "main or shared literal key moved out of main language file: " + key);
                if (usedByAddon) {
                    shared++;
                } else {
                    mainOnly++;
                }
            } else if (usedByAddon) {
                require(english.addon().containsKey(key),
                        "addon-only literal key did not move to addon language file: " + key);
                addonOnly++;
            }
        }
        requireEquals(330, mainOnly, "main-only literal translation ownership count drifted");
        requireEquals(150, addonOnly, "addon-only literal translation ownership count drifted");
        requireEquals(3, shared, "shared literal translation ownership count drifted");
        requireEquals(106, english.addon().size() - addonOnly,
                "addon dynamic/automatic translation family count drifted");
    }

    private static void verifyPhysicalBoundaries() throws IOException {
        require(Files.isRegularFile(MAIN_RESOURCES.resolve("codpattern.mixins.json")),
                "main mixin descriptor is missing");
        try (Stream<Path> paths = Files.walk(ADDON_RESOURCES)) {
            List<Path> addonMixins = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".mixins.json"))
                    .toList();
            require(addonMixins.isEmpty(), "addon unexpectedly packages mixin descriptors: " + addonMixins);
        }
        require(Files.isRegularFile(MAIN_RESOURCES.resolve("assets/codpattern/20s_se.ogg")),
                "main root audio copy is missing");
        require(Files.isRegularFile(MAIN_RESOURCES.resolve("assets/codpattern/sounds/20s_se.ogg")),
                "main sounds audio copy is missing");
        require(Files.isRegularFile(MAIN_RESOURCES.resolve("assets/codpattern/sounds.json")),
                "main sounds.json is missing");

        Set<String> mainPaths = relativeFiles(MAIN_RESOURCES);
        Set<String> addonPaths = relativeFiles(ADDON_RESOURCES);
        Set<String> duplicates = new HashSet<>(mainPaths);
        duplicates.retainAll(addonPaths);
        duplicates.removeAll(PER_JAR_METADATA);
        require(duplicates.isEmpty(), "unapproved duplicate physical resources: " + duplicates);
        requireEquals(PER_JAR_METADATA, intersection(mainPaths, addonPaths),
                "per-JAR metadata duplication set drifted");
    }

    private static Map<String, SemanticBaseline> readSemanticBaseline() throws IOException {
        Map<String, SemanticBaseline> result = new HashMap<>();
        for (String line : dataLines(LANGUAGE_SEMANTIC_BASELINE)) {
            String[] fields = line.split("\\t", -1);
            require(fields.length == 6, "invalid semantic language baseline row: " + line);
            result.put(fields[0], new SemanticBaseline(
                    Integer.parseInt(fields[1]), fields[3],
                    Integer.parseInt(fields[4]), Integer.parseInt(fields[5])));
        }
        return Map.copyOf(result);
    }

    private static Map<String, OwnershipBaseline> readOwnershipBaseline() throws IOException {
        Map<String, OwnershipBaseline> result = new HashMap<>();
        for (String line : dataLines(LANGUAGE_OWNERSHIP_BASELINE)) {
            String[] fields = line.split("\\t", -1);
            require(fields.length == 5, "invalid language ownership baseline row: " + line);
            result.put(fields[0], new OwnershipBaseline(
                    Integer.parseInt(fields[1]), Integer.parseInt(fields[2]), fields[3], fields[4]));
        }
        return Map.copyOf(result);
    }

    private static TreeMap<String, String> stringMap(JsonObject object) {
        TreeMap<String, String> result = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            require(entry.getValue().isJsonPrimitive()
                            && entry.getValue().getAsJsonPrimitive().isString(),
                    "translation value must remain a string: " + entry.getKey());
            result.put(entry.getKey(), entry.getValue().getAsString());
        }
        return result;
    }

    private static FormatCounts formatCounts(Map<String, String> values) {
        int formattedKeyCount = 0;
        int argumentTokenCount = 0;
        for (String value : values.values()) {
            Matcher matcher = FORMAT_TOKEN.matcher(value);
            int tokens = 0;
            while (matcher.find()) {
                String token = matcher.group();
                if (!"%%".equals(token) && !"%n".equals(token)) {
                    tokens++;
                }
            }
            if (tokens > 0) {
                formattedKeyCount++;
                argumentTokenCount += tokens;
            }
        }
        return new FormatCounts(formattedKeyCount, argumentTokenCount);
    }

    private static String canonicalKeyValueSha256(Map<String, String> values) {
        StringBuilder canonical = new StringBuilder();
        values.forEach((key, value) -> canonical.append(jsonString(key)).append('\t')
                .append(jsonString(value)).append('\n'));
        return sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String keysSha256(Set<String> keys) {
        StringBuilder canonical = new StringBuilder();
        keys.stream().sorted().forEach(key -> canonical.append(key).append('\n'));
        return sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String jsonString(String value) {
        StringBuilder result = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (current < 0x20) {
                        result.append(String.format("\\u%04x", (int) current));
                    } else {
                        result.append(current);
                    }
                }
            }
        }
        return result.append('"').toString();
    }

    private static String sourceText(Path root) throws IOException {
        StringBuilder result = new StringBuilder();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".java"))
                    .sorted().toList()) {
                result.append(Files.readString(path, StandardCharsets.UTF_8)).append('\n');
            }
        }
        return result.toString();
    }

    private static Set<String> relativeFiles(Path root) throws IOException {
        Set<String> result = new HashSet<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                result.add(root.relativize(path).toString().replace('\\', '/'));
            }
        }
        return Set.copyOf(result);
    }

    private static Set<String> intersection(Set<String> left, Set<String> right) {
        Set<String> result = new HashSet<>(left);
        result.retainAll(right);
        return Set.copyOf(result);
    }

    private static List<String> dataLines(Path relative) throws IOException {
        Path path = ModeSplitVerificationRoots.resolveRepositoryPath(relative);
        require(Files.isRegularFile(path), "missing Round 3 baseline: " + path);
        return Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
    }

    private static Path repositoryPath(String relative) {
        return ModeSplitVerificationRoots.resolveRepositoryPath(Path.of(relative));
    }

    private static String relativeResourcePath(String repositoryPath) {
        String normalized = repositoryPath.replace('\\', '/');
        String marker = "src/main/resources/";
        int index = normalized.indexOf(marker);
        require(index >= 0, "not a production resource path: " + repositoryPath);
        return normalized.substring(index + marker.length());
    }

    private static JsonObject readJson(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder text = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                text.append(String.format("%02x", value));
            }
            return text.toString();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireEquals(Object expected, Object actual, String message) {
        require(expected.equals(actual), message + ": expected=" + expected + ", actual=" + actual);
    }

    private record SemanticBaseline(
            int keyCount,
            String canonicalKeyValueSha256,
            int formattedKeyCount,
            int argumentTokenCount
    ) {
    }

    private record OwnershipBaseline(
            int mainKeyCount,
            int addonKeyCount,
            String mainKeysSha256,
            String addonKeysSha256
    ) {
    }

    private record LanguageFiles(TreeMap<String, String> main, TreeMap<String, String> addon) {
    }

    private record FormatCounts(int formattedKeyCount, int argumentTokenCount) {
    }
}
