package com.cdp.codpattern.app.match.editor;

import com.cdp.codpattern.app.match.GameModeRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ModeMapEditorSchemaRegistry {
    private static final Map<String, ModeMapEditorSchema> SCHEMAS = new LinkedHashMap<>();

    private ModeMapEditorSchemaRegistry() {
    }

    public static void register(String gameType, ModeMapEditorSchema schema) {
        if (schema == null) {
            return;
        }
        String canonicalGameType = GameModeRegistry.canonicalize(gameType);
        if (canonicalGameType.isBlank()) {
            return;
        }
        SCHEMAS.put(canonicalGameType, schema);
    }

    public static Optional<ModeMapEditorSchema> find(String gameType) {
        String canonicalGameType = GameModeRegistry.canonicalize(gameType);
        if (canonicalGameType.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(SCHEMAS.get(canonicalGameType));
    }

    public static List<String> pointLayerKeys(String gameType) {
        return find(gameType)
                .map(schema -> schema.pointLayers().stream()
                        .map(PointLayerDefinition::key)
                        .toList())
                .orElseGet(List::of);
    }

    public static boolean supportsPointLayer(String gameType, String key) {
        return find(gameType)
                .map(schema -> schema.supportsPointLayer(key))
                .orElse(false);
    }

    public static List<String> areaLayerKeys(String gameType) {
        return find(gameType)
                .map(schema -> schema.areaLayers().stream()
                        .map(AreaLayerDefinition::key)
                        .toList())
                .orElseGet(List::of);
    }

    public static boolean supportsAreaLayer(String gameType, String key) {
        return find(gameType)
                .map(schema -> schema.supportsAreaLayer(key))
                .orElse(false);
    }

    public static boolean supportsObjectFeature(String gameType, String key) {
        return find(gameType)
                .map(schema -> schema.supportsObjectFeature(key))
                .orElse(false);
    }
}
