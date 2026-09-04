package com.cdp.codpattern.app.match.editor;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.ModeModules;

import java.util.List;
import java.util.Optional;

/** Read-only editor-schema facade over the frozen mode catalog. */
public final class ModeMapEditorSchemaRegistry {
    private ModeMapEditorSchemaRegistry() {
    }

    public static Optional<ModeMapEditorSchema> find(String gameType) {
        String canonicalGameType = GameModeRegistry.canonicalize(gameType);
        if (canonicalGameType.isBlank()) {
            return Optional.empty();
        }
        return ModeModules.catalog().editorSchema(canonicalGameType);
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
