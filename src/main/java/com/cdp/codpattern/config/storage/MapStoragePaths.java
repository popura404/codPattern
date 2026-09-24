package com.cdp.codpattern.config.storage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Set;

/** Save-local paths. Map names are encoded without case-insensitive filesystem collisions. */
public final class MapStoragePaths {
    private final Path world;
    private final Path legacy;

    public MapStoragePaths(Path world, Path gameDirectory) {
        this.world = world.toAbsolutePath().normalize();
        this.legacy = gameDirectory.toAbsolutePath().normalize().resolve("fpsmatch");
    }

    public Path world() { return world; }
    public Path legacy() { return legacy; }
    public Path root() { return world.resolve("serverconfig/codpattern/maps"); }
    public Path metadata() { return root().resolve(".storage"); }
    public Path commonRules() { return root().resolve("builtin/rules/config.json"); }
    public Path oldCommonRules() { return world.resolve("serverconfig/codpattern/tdm_rules/config.json"); }

    public Path mode(String directory) {
        if (!directory.matches("(?:builtin/)?[a-z0-9][a-z0-9_-]*") || directory.equals("builtin")) {
            throw new IllegalArgumentException("Invalid mode directory: " + directory);
        }
        return root().resolve(directory);
    }

    public Path map(String directory, String name) { return mode(directory).resolve(mapDirectory(name)); }
    public Path rules(String directory, String name) { return map(directory, name).resolve("rules"); }

    public static String mapDirectory(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Map name is empty");
        byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 100) throw new IllegalArgumentException("Map name exceeds 100 UTF-8 bytes");
        // Safe on Windows and Linux, reversible, and independent of locale/case folding.
        return "m-" + HexFormat.of().formatHex(bytes);
    }

    public static String legacyFileName(String name) {
        String fixed = name;
        for (char ch : "\\/:*?\"<>|".toCharArray()) fixed = fixed.replace(String.valueOf(ch), "");
        if (fixed.isBlank() || Set.of(".", "..").contains(fixed)) {
            throw new IllegalArgumentException("Unsafe legacy path: " + name);
        }
        return fixed;
    }
}
