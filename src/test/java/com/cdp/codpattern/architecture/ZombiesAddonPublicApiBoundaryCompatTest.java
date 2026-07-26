package com.cdp.codpattern.architecture;

import com.cdp.codpattern.app.match.model.GameModeDefinition;
import com.cdp.codpattern.app.zombies.model.ZombiesGameModeDefinitions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ZombiesAddonPublicApiBoundaryCompatTest {
    private static final String MANIFEST = "docs/mode-split/phase0/ownership-manifest.tsv";
    private static final Pattern IMPORT = Pattern.compile("(?m)^import\\s+(?:static\\s+)?([^;]+);$");

    private ZombiesAddonPublicApiBoundaryCompatTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = findRepositoryRoot();
        List<Rule> rules = readRules(root.resolve(MANIFEST));
        List<Path> addonSources;
        try (Stream<Path> paths = Files.walk(root.resolve("src/main/java"))) {
            addonSources = paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> "ZOMBIES_ADDON".equals(ownerOf(root, path, rules)))
                    .sorted()
                    .toList();
        }
        require(!addonSources.isEmpty(), "final ownership manifest found no Zombies-addon sources");

        int publicMainImports = 0;
        for (Path source : addonSources) {
            String relative = normalize(root.relativize(source));
            String text = Files.readString(source, StandardCharsets.UTF_8);
            require(!text.contains("import com.cdp.codpattern.CodPattern;")
                            && !text.contains("CodPattern.MODID")
                            && !text.contains("com.cdp.codpattern.bootstrap.CoreBootstrap")
                            && !text.contains("import com.cdp.codpattern.bootstrap."),
                    "addon source reaches back into the combined shim/core bootstrap: " + relative);

            Matcher imports = IMPORT.matcher(text);
            while (imports.find()) {
                String imported = imports.group(1).trim();
                Path target = resolveProjectType(root, imported);
                if (target == null) {
                    continue;
                }
                String owner = ownerOf(root, target, rules);
                require(!"COMPOSITION_SHIM".equals(owner),
                        "addon source imports the composition shim: " + relative + " -> " + imported);
                if ("FUTURE_MAIN".equals(owner)) {
                    require(isPublicTopLevelType(target),
                            "addon source imports a non-public main type: " + relative + " -> " + imported);
                    publicMainImports++;
                }
            }
        }

        List<GameModeDefinition> definitions = new ArrayList<>();
        ZombiesGameModeDefinitions.contributor().contribute(definitions::add);
        require(definitions.size() == 1 && "zombies".equals(definitions.get(0).gameType()),
                "Zombies definition facade must expose exactly the addon-owned Zombies definition");
        require(publicMainImports > 0, "boundary audit did not inspect any addon-to-main public API imports");

        System.out.println("PASS Phase 7 Zombies addon public-API boundary: "
                + addonSources.size() + " addon sources, " + publicMainImports + " public main imports");
    }

    private static Path resolveProjectType(Path root, String imported) {
        String candidate = imported;
        while (!candidate.isBlank()) {
            Path source = root.resolve("src/main/java/" + candidate.replace('.', '/') + ".java");
            if (Files.isRegularFile(source)) {
                return source;
            }
            int dot = candidate.lastIndexOf('.');
            if (dot < 0) {
                return null;
            }
            candidate = candidate.substring(0, dot);
        }
        return null;
    }

    private static boolean isPublicTopLevelType(Path source) throws IOException {
        String name = source.getFileName().toString().replaceFirst("\\.java$", "");
        String text = Files.readString(source, StandardCharsets.UTF_8);
        Pattern declaration = Pattern.compile(
                "(?m)^public\\s+(?:(?:final|abstract|sealed|non-sealed)\\s+)?"
                        + "(?:class|interface|record|enum)\\s+" + Pattern.quote(name) + "\\b");
        return declaration.matcher(text).find();
    }

    private static List<Rule> readRules(Path manifest) throws IOException {
        List<Rule> rules = new ArrayList<>();
        for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] columns = line.split("\\t", 4);
            require(columns.length == 4, "invalid ownership-manifest row: " + line);
            rules.add(new Rule(columns[0], columns[1], columns[2]));
        }
        return rules;
    }

    private static String ownerOf(Path root, Path path, List<Rule> rules) {
        String relative = normalize(root.relativize(path));
        String owner = null;
        for (Rule rule : rules) {
            if (rule.matches(relative)) {
                owner = rule.owner();
            }
        }
        require(owner != null, "final ownership manifest does not classify " + relative);
        return owner;
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve(MANIFEST))) {
                return current;
            }
            current = current.getParent();
        }
        throw new AssertionError("Unable to locate repository root containing " + MANIFEST);
    }

    private static String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record Rule(String owner, String kind, String pattern) {
        boolean matches(String path) {
            return switch (kind) {
                case "EXACT" -> path.equals(pattern);
                case "PREFIX" -> path.startsWith(pattern);
                case "REGEX" -> path.matches(pattern);
                default -> throw new AssertionError("unknown ownership-manifest match kind: " + kind);
            };
        }
    }
}
