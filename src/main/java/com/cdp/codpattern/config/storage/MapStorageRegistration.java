package com.cdp.codpattern.config.storage;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** Public addon contract. Validators must not construct maps or access live server state. */
public record MapStorageRegistration(
        String mode, String owner, String directory, List<String> legacyFolders,
        Consumer<JsonObject> validateMap, LegacyRules rules) {
    public MapStorageRegistration {
        if (mode == null || owner == null || validateMap == null) throw new IllegalArgumentException("Missing registration field");
        if (directory.startsWith("builtin/") && !owner.equals("codpattern")) {
            throw new IllegalArgumentException("builtin is reserved for codpattern");
        }
        new MapStoragePaths(Path.of("."), Path.of(".")).mode(directory);
        legacyFolders = List.copyOf(legacyFolders);
        for (String folder : legacyFolders) {
            if (!folder.equals(MapStoragePaths.legacyFileName(folder))) throw new IllegalArgumentException("Unsafe legacy folder");
        }
    }

    public record LegacyRules(String relativeRoot, Function<String, String> legacyMapDirectory,
                              Consumer<Path> validate) {
        public LegacyRules {
            Path path = Path.of(relativeRoot).normalize();
            if (path.isAbsolute() || path.startsWith("..") || !path.startsWith("serverconfig/codpattern")) {
                throw new IllegalArgumentException("Rules must be below serverconfig/codpattern");
            }
        }
        public Path root(MapStoragePaths paths) { return paths.world().resolve(relativeRoot); }
        public Path forMap(MapStoragePaths paths, String name) {
            Path root = root(paths);
            Path result = root.resolve(legacyMapDirectory.apply(name)).normalize();
            if (!result.startsWith(root) || result.equals(root)) throw new IllegalArgumentException("Unsafe rules path");
            return result;
        }
    }
}
