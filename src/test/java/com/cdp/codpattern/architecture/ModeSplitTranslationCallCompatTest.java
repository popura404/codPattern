package com.cdp.codpattern.architecture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Snapshots ordered Java-side arguments to every Component.translatable call. */
public final class ModeSplitTranslationCallCompatTest {
    private static final Path MAIN_JAVA = Path.of("src/main/java");
    private static final Path BASELINE =
            Path.of("docs/mode-split/phase0/translation-call-baseline.tsv");
    private static final String CALL_PREFIX = "Component.translatable(";

    private ModeSplitTranslationCallCompatTest() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, CallSnapshot> actual = scanCalls();
        if (Arrays.asList(args).contains("--emit-baseline")) {
            System.out.println("# MODE_SPLIT_TRANSLATION_CALL_BASELINE_V1");
            System.out.println("# source_file\tcall_count\tordered_call_sha256");
            actual.forEach((path, snapshot) -> System.out.println(
                    path + "\t" + snapshot.count() + "\t" + snapshot.sha256()));
            return;
        }

        Map<String, CallSnapshot> expected = readBaseline();
        require(expected.equals(actual),
                "Java translation call argument/order baseline drifted. expected=" + expected
                        + ", actual=" + actual);
        int totalCalls = actual.values().stream().mapToInt(CallSnapshot::count).sum();
        require(totalCalls == 491, "translation call count drifted from the Phase 0 baseline: " + totalCalls);
        System.out.println("PASS mode split translation call baseline: " + totalCalls
                + " ordered calls across " + actual.size() + " source files");
    }

    private static Map<String, CallSnapshot> scanCalls() throws Exception {
        Map<String, CallSnapshot> snapshots = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(MAIN_JAVA)) {
            for (Path path : paths.filter(file -> file.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList()) {
                String source = Files.readString(path, StandardCharsets.UTF_8);
                List<String> calls = extractCalls(source, path);
                if (calls.isEmpty()) {
                    continue;
                }
                String relative = MAIN_JAVA.relativize(path).toString().replace('\\', '/');
                snapshots.put(relative, new CallSnapshot(calls.size(), sha256(String.join("\n", calls))));
            }
        }
        return snapshots;
    }

    private static List<String> extractCalls(String source, Path path) {
        List<String> calls = new ArrayList<>();
        int cursor = 0;
        while (true) {
            int start = source.indexOf(CALL_PREFIX, cursor);
            if (start < 0) {
                return calls;
            }
            int open = start + CALL_PREFIX.length() - 1;
            int close = matchingCloseParen(source, open, path);
            calls.add(normalizeWhitespace(source.substring(start, close + 1)));
            cursor = close + 1;
        }
    }

    private static int matchingCloseParen(String source, int open, Path path) {
        int depth = 0;
        boolean inString = false;
        boolean inCharacter = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean escaped = false;
        for (int index = open; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';

            if (inLineComment) {
                if (current == '\n') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                if (current == '*' && next == '/') {
                    inBlockComment = false;
                    index++;
                }
                continue;
            }
            if (inString || inCharacter) {
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (current == '\\') {
                    escaped = true;
                    continue;
                }
                if (inString && current == '"') {
                    inString = false;
                } else if (inCharacter && current == '\'') {
                    inCharacter = false;
                }
                continue;
            }

            if (current == '/' && next == '/') {
                inLineComment = true;
                index++;
                continue;
            }
            if (current == '/' && next == '*') {
                inBlockComment = true;
                index++;
                continue;
            }
            if (current == '"') {
                inString = true;
                continue;
            }
            if (current == '\'') {
                inCharacter = true;
                continue;
            }
            if (current == '(') {
                depth++;
            } else if (current == ')' && --depth == 0) {
                return index;
            }
        }
        throw new AssertionError("unterminated Component.translatable call in " + path);
    }

    private static String normalizeWhitespace(String call) {
        StringBuilder normalized = new StringBuilder(call.length());
        boolean pendingSpace = false;
        boolean inString = false;
        boolean inCharacter = false;
        boolean escaped = false;
        for (int index = 0; index < call.length(); index++) {
            char current = call.charAt(index);
            if (inString || inCharacter) {
                normalized.append(current);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (inString && current == '"') {
                    inString = false;
                } else if (inCharacter && current == '\'') {
                    inCharacter = false;
                }
                continue;
            }
            if (current == '"') {
                if (pendingSpace && normalized.length() > 0) {
                    normalized.append(' ');
                }
                pendingSpace = false;
                inString = true;
                normalized.append(current);
            } else if (current == '\'') {
                if (pendingSpace && normalized.length() > 0) {
                    normalized.append(' ');
                }
                pendingSpace = false;
                inCharacter = true;
                normalized.append(current);
            } else if (Character.isWhitespace(current)) {
                pendingSpace = true;
            } else {
                if (pendingSpace && normalized.length() > 0) {
                    normalized.append(' ');
                }
                pendingSpace = false;
                normalized.append(current);
            }
        }
        return normalized.toString();
    }

    private static Map<String, CallSnapshot> readBaseline() throws IOException {
        require(Files.isRegularFile(BASELINE), "missing translation call baseline: " + BASELINE);
        Map<String, CallSnapshot> snapshots = new LinkedHashMap<>();
        for (String line : Files.readAllLines(BASELINE, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] fields = line.split("\\t", -1);
            require(fields.length == 3, "invalid translation call baseline row: " + line);
            CallSnapshot previous = snapshots.put(
                    fields[0], new CallSnapshot(Integer.parseInt(fields[1]), fields[2]));
            require(previous == null, "duplicate translation call baseline file: " + fields[0]);
        }
        return snapshots;
    }

    private static String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder text = new StringBuilder(hash.length * 2);
        for (byte next : hash) {
            text.append(String.format("%02x", next));
        }
        return text.toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record CallSnapshot(int count, String sha256) {
    }
}
