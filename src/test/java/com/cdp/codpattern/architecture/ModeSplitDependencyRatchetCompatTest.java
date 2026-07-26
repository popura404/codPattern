package com.cdp.codpattern.architecture;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Phase 0 architecture ratchet for the future-main / Zombies-addon boundary.
 *
 * <p>This is intentionally a pure-JVM executable compatibility test. It reads the provisional
 * ownership manifest, inspects compiled class-file UTF-8 constants (class names, descriptors,
 * generic signatures and annotation descriptors), scans source-level fully qualified references,
 * and checks class-bearing resource metadata. The exact current dependency edges are allowlisted;
 * a new edge or a stale allowlist entry fails the task.</p>
 */
public final class ModeSplitDependencyRatchetCompatTest {
    private static final String OWNERSHIP_MANIFEST =
            "docs/mode-split/phase0/ownership-manifest.tsv";
    private static final String DEPENDENCY_ALLOWLIST =
            "docs/mode-split/phase0/dependency-ratchet-allowlist.tsv";
    private static final String IDENTITY_ALLOWLIST =
            "docs/mode-split/phase0/zombies-identity-ratchet.tsv";
    private static final String MAIN_SOURCE_PREFIX = "src/main/java/";

    private ModeSplitDependencyRatchetCompatTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = findRepositoryRoot();
        OwnershipManifest ownership = OwnershipManifest.read(root.resolve(OWNERSHIP_MANIFEST));

        List<String> productionSources = listRelativeFiles(root, "src/main/java", ".java");
        if (productionSources.isEmpty()) {
            throw new AssertionError("No production Java sources found under src/main/java");
        }

        EnumMap<Owner, Integer> ownerCounts = new EnumMap<>(Owner.class);
        Map<String, Owner> sourceOwners = new LinkedHashMap<>();
        for (String source : productionSources) {
            Owner owner = ownership.ownerOf(source);
            if (owner == null) {
                throw new AssertionError("Ownership manifest does not classify " + source);
            }
            sourceOwners.put(source, owner);
            ownerCounts.merge(owner, 1, Integer::sum);
        }

        List<String> compositionShims = sourceOwners.entrySet().stream()
                .filter(entry -> entry.getValue() == Owner.COMPOSITION_SHIM)
                .map(Map.Entry::getKey)
                .toList();
        if (!compositionShims.equals(List.of("src/main/java/com/cdp/codpattern/CodPattern.java"))) {
            throw new AssertionError("Expected exactly the current CodPattern entry point as the composition shim, got "
                    + compositionShims);
        }
        ownership.assertEveryRuleMatched(productionSources, root);

        Set<String> addonTypes = new TreeSet<>();
        for (Map.Entry<String, Owner> entry : sourceOwners.entrySet()) {
            String topLevelType = sourcePathToTopLevelType(entry.getKey());
            if (entry.getValue() == Owner.ZOMBIES_ADDON) {
                addonTypes.add(topLevelType);
            }
        }

        Map<Edge, Set<String>> evidence = new TreeMap<>();
        scanCompiledTypes(root, sourceOwners, addonTypes, evidence);
        scanSourceFullyQualifiedReferences(root, sourceOwners, addonTypes, evidence);
        scanClassBearingMetadata(root, ownership, addonTypes, evidence);

        Set<Edge> actualEdges = new TreeSet<>(evidence.keySet());
        Set<Edge> allowedEdges = readAllowlist(root.resolve(DEPENDENCY_ALLOWLIST));
        Set<Edge> newEdges = difference(actualEdges, allowedEdges);
        Set<Edge> staleEdges = difference(allowedEdges, actualEdges);
        Map<IdentityUse, Integer> actualIdentityUses = scanFutureMainIdentityUses(root, sourceOwners);
        Map<IdentityUse, Integer> allowedIdentityUses = readIdentityAllowlist(root.resolve(IDENTITY_ALLOWLIST));
        Set<IdentityUse> identityKeys = new TreeSet<>();
        identityKeys.addAll(actualIdentityUses.keySet());
        identityKeys.addAll(allowedIdentityUses.keySet());
        List<String> identityMismatches = new ArrayList<>();
        for (IdentityUse identityUse : identityKeys) {
            int actual = actualIdentityUses.getOrDefault(identityUse, 0);
            int allowed = allowedIdentityUses.getOrDefault(identityUse, 0);
            if (actual != allowed) {
                identityMismatches.add(identityUse.source() + "\t" + identityUse.token()
                        + "\tallowed=" + allowed + "\tactual=" + actual);
            }
        }

        if (!newEdges.isEmpty() || !staleEdges.isEmpty() || !identityMismatches.isEmpty()) {
            StringBuilder message = new StringBuilder("Mode-split dependency ratchet mismatch.\n");
            if (!newEdges.isEmpty()) {
                message.append("New FUTURE_MAIN -> ZOMBIES_ADDON edges (add only after ownership review):\n");
                appendEdges(message, newEdges, evidence);
            }
            if (!staleEdges.isEmpty()) {
                message.append("Stale allowlist edges (remove them so the ratchet shrinks):\n");
                appendEdges(message, staleEdges, evidence);
            }
            if (!identityMismatches.isEmpty()) {
                message.append("Future-main Zombies identity usage changed (reduce intentionally; never grow silently):\n");
                for (String mismatch : identityMismatches) {
                    message.append("  ").append(mismatch).append('\n');
                }
            }
            message.append("Current exact allowlist rows:\n");
            for (Edge edge : actualEdges) {
                message.append(edge.origin()).append('\t').append(edge.target()).append('\n');
            }
            throw new AssertionError(message.toString());
        }

        System.out.println("ModeSplitDependencyRatchetCompatTest passed: "
                + productionSources.size() + " production Java sources classified ("
                + ownerCounts.getOrDefault(Owner.FUTURE_MAIN, 0) + " future-main, "
                + ownerCounts.getOrDefault(Owner.ZOMBIES_ADDON, 0) + " Zombies-addon, "
                + ownerCounts.getOrDefault(Owner.COMPOSITION_SHIM, 0) + " composition shim), "
                + actualEdges.size() + " ratcheted dependency edges and "
                + actualIdentityUses.size() + " temporary identity-use sites.");
    }

    private static Map<IdentityUse, Integer> scanFutureMainIdentityUses(
            Path root,
            Map<String, Owner> sourceOwners
    ) throws IOException {
        List<String> tokens = List.of("BuiltInGameModes.ZOMBIES", "BuiltInGameModes.isZombies(");
        Map<IdentityUse, Integer> uses = new TreeMap<>();
        for (Map.Entry<String, Owner> entry : sourceOwners.entrySet()) {
            if (entry.getValue() != Owner.FUTURE_MAIN
                    || entry.getKey().endsWith("/BuiltInGameModes.java")) {
                continue;
            }
            String text = Files.readString(root.resolve(entry.getKey()), StandardCharsets.UTF_8);
            for (String token : tokens) {
                int count = countOccurrences(text, token);
                if (count > 0) {
                    uses.put(new IdentityUse(entry.getKey(), token), count);
                }
            }
        }
        return uses;
    }

    private static int countOccurrences(String text, String token) {
        int count = 0;
        int fromIndex = 0;
        while (true) {
            int index = text.indexOf(token, fromIndex);
            if (index < 0) {
                return count;
            }
            count++;
            fromIndex = index + token.length();
        }
    }

    private static void scanCompiledTypes(
            Path root,
            Map<String, Owner> sourceOwners,
            Set<String> addonTypes,
            Map<Edge, Set<String>> evidence
    ) throws IOException {
        Path classesRoot = root.resolve("build/classes/java/main");
        if (!Files.isDirectory(classesRoot)) {
            throw new AssertionError("Compiled main classes are missing: " + classesRoot
                    + ". Run through the Gradle task so testClasses executes first.");
        }

        int inspected = 0;
        try (Stream<Path> paths = Files.walk(classesRoot)) {
            for (Path classFile : paths.filter(path -> path.toString().endsWith(".class")).sorted().toList()) {
                String internalName = normalize(classesRoot.relativize(classFile).toString());
                internalName = internalName.substring(0, internalName.length() - ".class".length());
                String topLevelInternalName = stripNestedClassSuffix(internalName);
                String source = MAIN_SOURCE_PREFIX + topLevelInternalName + ".java";
                Owner owner = sourceOwners.get(source);
                if (owner != Owner.FUTURE_MAIN) {
                    continue;
                }
                inspected++;
                String origin = internalToDotted(topLevelInternalName);
                Set<String> constants = readClassUtf8Constants(classFile);
                for (String target : addonTypes) {
                    String internalTarget = dottedToInternal(target);
                    if (containsTypeReference(constants, internalTarget, target)) {
                        record(evidence, new Edge(origin, target),
                                "bytecode:" + normalize(root.relativize(classFile).toString()));
                    }
                }
            }
        }
        if (inspected == 0) {
            throw new AssertionError("No FUTURE_MAIN class files were inspected under " + classesRoot);
        }
    }

    private static void scanSourceFullyQualifiedReferences(
            Path root,
            Map<String, Owner> sourceOwners,
            Set<String> addonTypes,
            Map<Edge, Set<String>> evidence
    ) throws IOException {
        for (Map.Entry<String, Owner> entry : sourceOwners.entrySet()) {
            if (entry.getValue() != Owner.FUTURE_MAIN) {
                continue;
            }
            String source = entry.getKey();
            String origin = sourcePathToTopLevelType(source);
            String text = Files.readString(root.resolve(source), StandardCharsets.UTF_8);
            for (String target : addonTypes) {
                if (containsTypeName(text, target)) {
                    record(evidence, new Edge(origin, target), "source-fq:" + source);
                }
            }
        }
    }

    private static void scanClassBearingMetadata(
            Path root,
            OwnershipManifest ownership,
            Set<String> addonTypes,
            Map<Edge, Set<String>> evidence
    ) throws IOException {
        Path resources = root.resolve("src/main/resources");
        if (!Files.isDirectory(resources)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(resources)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                String relative = normalize(root.relativize(path).toString());
                if (ownership.ownerOf(relative) != Owner.FUTURE_MAIN || !isClassBearingMetadata(relative)) {
                    continue;
                }
                String text = Files.readString(path, StandardCharsets.UTF_8);
                for (String target : addonTypes) {
                    if (containsTypeName(text, target)) {
                        record(evidence, new Edge("resource:" + relative, target), "resource:" + relative);
                    }
                }
            }
        }
    }

    private static boolean isClassBearingMetadata(String path) {
        return path.startsWith("src/main/resources/META-INF/services/")
                || path.endsWith(".json")
                || path.endsWith(".toml")
                || path.endsWith(".properties")
                || path.endsWith(".cfg");
    }

    private static Set<String> readClassUtf8Constants(Path classFile) throws IOException {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(classFile)))) {
            if (input.readInt() != 0xCAFEBABE) {
                throw new IOException("Invalid class-file magic: " + classFile);
            }
            input.readUnsignedShort();
            input.readUnsignedShort();
            int constantPoolCount = input.readUnsignedShort();
            Set<String> utf8 = new LinkedHashSet<>();
            for (int index = 1; index < constantPoolCount; index++) {
                int tag;
                try {
                    tag = input.readUnsignedByte();
                } catch (EOFException error) {
                    throw new IOException("Truncated constant pool in " + classFile, error);
                }
                switch (tag) {
                    case 1 -> utf8.add(input.readUTF());
                    case 3, 4 -> skipFully(input, 4);
                    case 5, 6 -> {
                        skipFully(input, 8);
                        index++;
                    }
                    case 7, 8, 16, 19, 20 -> skipFully(input, 2);
                    case 9, 10, 11, 12, 17, 18 -> skipFully(input, 4);
                    case 15 -> skipFully(input, 3);
                    default -> throw new IOException("Unsupported constant-pool tag " + tag + " in " + classFile);
                }
            }
            return utf8;
        }
    }

    private static void skipFully(DataInputStream input, int bytes) throws IOException {
        int remaining = bytes;
        while (remaining > 0) {
            int skipped = input.skipBytes(remaining);
            if (skipped <= 0) {
                throw new EOFException("Unexpected EOF while reading class-file constant pool");
            }
            remaining -= skipped;
        }
    }

    private static boolean containsTypeReference(Set<String> constants, String internalName, String dottedName) {
        for (String constant : constants) {
            if (containsTypeName(constant, internalName) || containsTypeName(constant, dottedName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsTypeName(String text, String typeName) {
        int fromIndex = 0;
        while (true) {
            int index = text.indexOf(typeName, fromIndex);
            if (index < 0) {
                return false;
            }
            int after = index + typeName.length();
            if (after == text.length()
                    || text.charAt(after) == '$'
                    || !Character.isJavaIdentifierPart(text.charAt(after))) {
                return true;
            }
            fromIndex = index + 1;
        }
    }

    private static Set<Edge> readAllowlist(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new AssertionError("Missing dependency allowlist: " + path);
        }
        Set<Edge> edges = new TreeSet<>();
        int lineNumber = 0;
        for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            lineNumber++;
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] fields = rawLine.split("\\t", -1);
            if (fields.length != 2 || fields[0].isBlank() || fields[1].isBlank()) {
                throw new AssertionError("Invalid dependency allowlist row " + path + ":" + lineNumber);
            }
            Edge edge = new Edge(fields[0].trim(), fields[1].trim());
            if (!edges.add(edge)) {
                throw new AssertionError("Duplicate dependency allowlist edge at " + path + ":" + lineNumber
                        + ": " + edge);
            }
        }
        return edges;
    }

    private static Map<IdentityUse, Integer> readIdentityAllowlist(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new AssertionError("Missing Zombies identity allowlist: " + path);
        }
        Map<IdentityUse, Integer> uses = new TreeMap<>();
        int lineNumber = 0;
        for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            lineNumber++;
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] fields = rawLine.split("\\t", -1);
            if (fields.length != 3 || fields[0].isBlank() || fields[1].isBlank()) {
                throw new AssertionError("Invalid Zombies identity allowlist row " + path + ":" + lineNumber);
            }
            int count;
            try {
                count = Integer.parseInt(fields[2].trim());
            } catch (NumberFormatException error) {
                throw new AssertionError("Invalid Zombies identity count at " + path + ":" + lineNumber, error);
            }
            if (count <= 0) {
                throw new AssertionError("Zombies identity count must be positive at " + path + ":" + lineNumber);
            }
            IdentityUse use = new IdentityUse(normalize(fields[0].trim()), fields[1]);
            if (uses.putIfAbsent(use, count) != null) {
                throw new AssertionError("Duplicate Zombies identity allowlist row at " + path + ":" + lineNumber);
            }
        }
        return uses;
    }

    private static Set<Edge> difference(Set<Edge> left, Set<Edge> right) {
        Set<Edge> result = new TreeSet<>(left);
        result.removeAll(right);
        return result;
    }

    private static void appendEdges(StringBuilder message, Set<Edge> edges, Map<Edge, Set<String>> evidence) {
        for (Edge edge : edges) {
            message.append("  ").append(edge.origin()).append(" -> ").append(edge.target());
            Set<String> edgeEvidence = evidence.get(edge);
            if (edgeEvidence != null && !edgeEvidence.isEmpty()) {
                message.append(" [").append(String.join(", ", edgeEvidence)).append(']');
            }
            message.append('\n');
        }
    }

    private static void record(Map<Edge, Set<String>> evidence, Edge edge, String detail) {
        evidence.computeIfAbsent(edge, ignored -> new TreeSet<>()).add(detail);
    }

    private static List<String> listRelativeFiles(Path root, String directory, String suffix) throws IOException {
        Path start = root.resolve(directory);
        if (!Files.isDirectory(start)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(start)) {
            return paths.filter(Files::isRegularFile)
                    .map(root::relativize)
                    .map(Path::toString)
                    .map(ModeSplitDependencyRatchetCompatTest::normalize)
                    .filter(path -> path.endsWith(suffix))
                    .sorted()
                    .toList();
        }
    }

    private static String sourcePathToTopLevelType(String sourcePath) {
        if (!sourcePath.startsWith(MAIN_SOURCE_PREFIX) || !sourcePath.endsWith(".java")) {
            throw new IllegalArgumentException("Not a production Java source path: " + sourcePath);
        }
        String internalName = sourcePath.substring(
                MAIN_SOURCE_PREFIX.length(),
                sourcePath.length() - ".java".length());
        return internalToDotted(internalName);
    }

    private static String stripNestedClassSuffix(String internalName) {
        int nested = internalName.indexOf('$');
        return nested < 0 ? internalName : internalName.substring(0, nested);
    }

    private static String internalToDotted(String internalName) {
        return internalName.replace('/', '.');
    }

    private static String dottedToInternal(String dottedName) {
        return dottedName.replace('.', '/');
    }

    private static String normalize(String path) {
        return path.replace('\\', '/');
    }

    private static Path findRepositoryRoot() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve(OWNERSHIP_MANIFEST))
                    && Files.isRegularFile(current.resolve("build.gradle"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new AssertionError("Could not locate repository root containing " + OWNERSHIP_MANIFEST);
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

    private record Edge(String origin, String target) implements Comparable<Edge> {
        private Edge {
            if (origin == null || origin.isBlank() || target == null || target.isBlank()) {
                throw new IllegalArgumentException("Dependency edge endpoints must not be blank");
            }
        }

        @Override
        public int compareTo(Edge other) {
            int byOrigin = origin.compareTo(other.origin);
            return byOrigin != 0 ? byOrigin : target.compareTo(other.target);
        }
    }

    private record IdentityUse(String source, String token) implements Comparable<IdentityUse> {
        private IdentityUse {
            if (source == null || source.isBlank() || token == null || token.isBlank()) {
                throw new IllegalArgumentException("Identity-use source and token must not be blank");
            }
        }

        @Override
        public int compareTo(IdentityUse other) {
            int bySource = source.compareTo(other.source);
            return bySource != 0 ? bySource : token.compareTo(other.token);
        }
    }

    private record OwnershipRule(Owner owner, MatchKind kind, String expression, Pattern pattern, int lineNumber) {
        private boolean matches(String path) {
            return switch (kind) {
                case EXACT -> path.equals(expression);
                case PREFIX -> path.startsWith(expression);
                case REGEX -> pattern.matcher(path).matches();
            };
        }
    }

    private static final class OwnershipManifest {
        private final Path source;
        private final List<OwnershipRule> rules;

        private OwnershipManifest(Path source, List<OwnershipRule> rules) {
            this.source = source;
            this.rules = List.copyOf(rules);
        }

        private static OwnershipManifest read(Path path) throws IOException {
            if (!Files.isRegularFile(path)) {
                throw new AssertionError("Missing ownership manifest: " + path);
            }
            List<OwnershipRule> rules = new ArrayList<>();
            int lineNumber = 0;
            for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                lineNumber++;
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] fields = rawLine.split("\\t", -1);
                if (fields.length < 3 || fields[0].isBlank() || fields[1].isBlank() || fields[2].isBlank()) {
                    throw new AssertionError("Invalid ownership manifest row " + path + ":" + lineNumber);
                }
                Owner owner = Owner.valueOf(fields[0].trim());
                MatchKind kind = MatchKind.valueOf(fields[1].trim());
                String expression = kind == MatchKind.REGEX
                        ? fields[2].trim()
                        : normalize(fields[2].trim());
                Pattern pattern = kind == MatchKind.REGEX ? Pattern.compile(expression) : null;
                rules.add(new OwnershipRule(owner, kind, expression, pattern, lineNumber));
            }
            if (rules.isEmpty()) {
                throw new AssertionError("Ownership manifest contains no rules: " + path);
            }
            return new OwnershipManifest(path, rules);
        }

        private Owner ownerOf(String path) {
            Owner resolved = null;
            for (OwnershipRule rule : rules) {
                if (rule.matches(path)) {
                    resolved = rule.owner();
                }
            }
            return resolved;
        }

        private void assertEveryRuleMatched(List<String> productionSources, Path root) throws IOException {
            List<String> candidates = new ArrayList<>(productionSources);
            Path resources = root.resolve("src/main/resources");
            if (Files.isDirectory(resources)) {
                try (Stream<Path> paths = Files.walk(resources)) {
                    candidates.addAll(paths.filter(Files::isRegularFile)
                            .map(root::relativize)
                            .map(Path::toString)
                            .map(ModeSplitDependencyRatchetCompatTest::normalize)
                            .toList());
                }
            }
            for (OwnershipRule rule : rules) {
                boolean matched = candidates.stream().anyMatch(rule::matches);
                if (!matched) {
                    throw new AssertionError("Ownership rule matches no current file at " + source + ":"
                            + rule.lineNumber() + ": " + rule.kind() + " " + rule.expression());
                }
            }
        }
    }
}
