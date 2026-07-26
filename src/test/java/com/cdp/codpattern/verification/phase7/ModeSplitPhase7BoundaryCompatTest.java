package com.cdp.codpattern.verification.phase7;

import com.cdp.codpattern.app.match.GameModeBootstrap;
import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.GameModeRuntimeRegistry;
import com.cdp.codpattern.app.match.model.ClientModePresentationRegistry;
import com.cdp.codpattern.app.tdm.model.TdmGameModeDefinitions;
import com.cdp.codpattern.app.zombies.model.ZombiesGameModeDefinitions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Combined-classpath checks that complement the isolated Phase 7 compiler and fresh-JVM fence. */
public final class ModeSplitPhase7BoundaryCompatTest {
    private static final Path OWNERSHIP_MANIFEST =
            Path.of("docs/mode-split/phase0/ownership-manifest.tsv");

    private ModeSplitPhase7BoundaryCompatTest() {
    }

    public static void main(String[] args) throws Exception {
        finalOwnershipManifestClassifiesAllProductionFiles();
        zombiesGatewaysStayOutsideTheCompositionShim();
        combinedDistributionProvidesAllThreeModes();
        repositoryRemainsSingleProjectWithoutSplitSourceSets();
        System.out.println("PASS Phase 7 ownership, gateway, combined-distribution, and physical-split boundary compat");
    }

    private static void finalOwnershipManifestClassifiesAllProductionFiles() throws IOException {
        OwnershipManifest manifest = OwnershipManifest.read(OWNERSHIP_MANIFEST);
        String manifestText = Files.readString(OWNERSHIP_MANIFEST, StandardCharsets.UTF_8)
                .toLowerCase(Locale.ROOT);
        require(!manifestText.contains("provisional")
                        && !manifestText.contains("ambiguous")
                        && !manifestText.contains("unresolved"),
                "final ownership manifest must contain no provisional, ambiguous, or unresolved entry");

        List<String> productionFiles = new ArrayList<>();
        productionFiles.addAll(listFiles(Path.of("src/main/java")));
        productionFiles.addAll(listFiles(Path.of("src/main/resources")));
        require(!productionFiles.isEmpty(), "production file inventory must not be empty");

        EnumMap<Owner, Integer> counts = new EnumMap<>(Owner.class);
        for (String productionFile : productionFiles) {
            Owner owner = manifest.ownerOf(productionFile);
            require(owner != null, "final ownership manifest does not classify " + productionFile);
            counts.merge(owner, 1, Integer::sum);
        }
        require(counts.getOrDefault(Owner.FUTURE_MAIN, 0) > 0,
                "final ownership manifest needs future-main files");
        require(counts.getOrDefault(Owner.ZOMBIES_ADDON, 0) > 0,
                "final ownership manifest needs Zombies-addon files");
        require(counts.getOrDefault(Owner.COMPOSITION_SHIM, 0) >= 1,
                "final ownership manifest should retain temporary composition glue");
        require(manifest.ownerOf("src/main/java/com/cdp/codpattern/CodPattern.java")
                        == Owner.COMPOSITION_SHIM,
                "CodPattern must remain the sole composition shim");
        require(manifest.ownerOf("src/main/resources/assets/codpattern/textures/gui/modes/zombies_preview.png")
                        == Owner.ZOMBIES_ADDON,
                "Zombies preview texture should be addon-owned");
        require(manifest.ownerOf("src/main/resources/data/tacz/tags/blocks/interact_key/whitelist.json")
                        == Owner.ZOMBIES_ADDON,
                "Zombies TaCZ interaction whitelist should be addon-owned");
        require(manifest.ownerOf("src/main/resources/assets/codpattern/lang/en_us.json")
                        == Owner.COMPOSITION_SHIM,
                "mixed compatibility language bundle should have a resolved temporary-shim owner");

        System.out.println("Phase 7 final ownership manifest: " + productionFiles.size()
                + " production files classified " + counts);
    }

    private static void zombiesGatewaysStayOutsideTheCompositionShim() throws IOException {
        Map<Path, List<String>> requiredPublicRoutes = Map.of(
                Path.of("src/main/java/com/cdp/codpattern/app/zombies/bootstrap/ZombiesBootstrap.java"),
                List.of("ZombiesGameModeDefinitions.registerDefaults()", "ModePlayerLoginContributors.register("),
                Path.of("src/main/java/com/cdp/codpattern/app/zombies/bootstrap/ZombiesClientBootstrap.java"),
                List.of("ModeClientActionHandlers.register(", "ModeGuiOverlayContributors.register("),
                Path.of("src/main/java/com/cdp/codpattern/app/zombies/bootstrap/ZombiesNetworkPacketContributor.java"),
                List.of("ModeNetworkPacketContributions.install(", "ModeNetworkPacketSlots."),
                Path.of("src/main/java/com/cdp/codpattern/app/zombies/model/ZombiesGameModeDefinitions.java"),
                List.of("ModeDefinitionContributions.register(", "Optional.of(ZombiesRuntimeProvider.INSTANCE)"));

        for (Map.Entry<Path, List<String>> entry : requiredPublicRoutes.entrySet()) {
            String source = Files.readString(entry.getKey(), StandardCharsets.UTF_8);
            require(!source.contains("com.cdp.codpattern.CodPattern")
                            && !source.contains("CoreBootstrap")
                            && !source.contains("getDeclared")
                            && !source.contains("setAccessible"),
                    "addon gateway must not reach into the composition shim or reflective internals: "
                            + entry.getKey());
            for (String requiredRoute : entry.getValue()) {
                require(source.contains(requiredRoute),
                        "addon gateway is missing public boundary route " + requiredRoute + " in " + entry.getKey());
            }
        }
    }

    private static void combinedDistributionProvidesAllThreeModes() {
        TdmGameModeDefinitions.registerDefaults();
        ZombiesGameModeDefinitions.registerDefaults();
        GameModeBootstrap.registerCommonProviders();

        List<String> gameTypes = GameModeRegistry.orderedDefinitions().stream()
                .map(definition -> definition.gameType())
                .toList();
        require(gameTypes.equals(List.of("frontline", "teamdeathmatch", "zombies")),
                "combined distribution should expose all three modes in stable order: " + gameTypes);
        for (String gameType : gameTypes) {
            require(GameModeRuntimeRegistry.find(gameType).isPresent(),
                    "combined distribution is missing runtime provider for " + gameType);
            require(ClientModePresentationRegistry.find(gameType).isPresent(),
                    "combined distribution is missing client presentation for " + gameType);
        }
    }

    private static void repositoryRemainsSingleProjectWithoutSplitSourceSets() throws IOException {
        String settings = Files.readString(Path.of("settings.gradle"), StandardCharsets.UTF_8);
        require(!Pattern.compile("(?m)^\\s*include(?:Build)?\\s*\\(").matcher(settings).find(),
                "Phase 7 must not add a Gradle subproject or included build");
        String build = Files.readString(Path.of("build.gradle"), StandardCharsets.UTF_8);
        require(!build.contains("sourceSets.create") && !build.contains("sourceSets.register"),
                "Phase 7 must not create a Gradle SourceSet");
        require(!build.contains("zombiesAddonJar") && !build.contains("mainOnlyJar"),
                "Phase 7 must not create split shipping JAR tasks");
    }

    private static List<String> listFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .map(Path::toString)
                    .map(path -> path.replace('\\', '/'))
                    .sorted()
                    .toList();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private enum Owner {
        FUTURE_MAIN,
        ZOMBIES_ADDON,
        COMPOSITION_SHIM
    }

    private enum MatchKind {
        EXACT,
        PREFIX,
        REGEX
    }

    private record OwnershipRule(Owner owner, MatchKind kind, String expression, Pattern pattern) {
        private boolean matches(String path) {
            return switch (kind) {
                case EXACT -> path.equals(expression);
                case PREFIX -> path.startsWith(expression);
                case REGEX -> pattern.matcher(path).matches();
            };
        }
    }

    private record OwnershipManifest(List<OwnershipRule> rules) {
        private static OwnershipManifest read(Path path) throws IOException {
            require(Files.isRegularFile(path), "missing final ownership manifest: " + path);
            List<OwnershipRule> rules = new ArrayList<>();
            for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] fields = rawLine.split("\\t", -1);
                require(fields.length >= 3, "invalid final ownership manifest row: " + rawLine);
                Owner owner = Owner.valueOf(fields[0].trim());
                MatchKind kind = MatchKind.valueOf(fields[1].trim());
                String expression = fields[2].trim();
                if (kind != MatchKind.REGEX) {
                    expression = expression.replace('\\', '/');
                }
                rules.add(new OwnershipRule(
                        owner,
                        kind,
                        expression,
                        kind == MatchKind.REGEX ? Pattern.compile(expression) : null));
            }
            require(!rules.isEmpty(), "final ownership manifest must contain rules");
            return new OwnershipManifest(List.copyOf(rules));
        }

        private Owner ownerOf(String rawPath) {
            String path = rawPath.replace('\\', '/');
            Owner resolved = null;
            for (OwnershipRule rule : rules) {
                if (rule.matches(path)) {
                    resolved = rule.owner();
                }
            }
            return resolved;
        }
    }
}
