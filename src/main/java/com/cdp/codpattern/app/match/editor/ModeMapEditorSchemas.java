package com.cdp.codpattern.app.match.editor;

import com.cdp.codpattern.app.match.GameModeBootstrap;
import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.model.ModeCapability;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointKind;

import java.util.List;
import java.util.Optional;

public final class ModeMapEditorSchemas {
    public static final String MATCH_END_TELEPORT = "match_end_teleport";

    private ModeMapEditorSchemas() {
    }

    public static void registerDefaults() {
        GameModeBootstrap.registerEditorSchemas();
    }

    public static List<String> spawnPointLayerKeys(String gameType) {
        registerDefaults();
        List<String> keys = ModeMapEditorSchemaRegistry.pointLayerKeys(gameType);
        return keys.isEmpty() ? List.of(SpawnPointKind.INITIAL.serializedName()) : keys;
    }

    public static boolean supportsSpawnPointLayer(String gameType, SpawnPointKind kind) {
        SpawnPointKind resolvedKind = kind == null ? SpawnPointKind.INITIAL : kind;
        return supportsPointLayer(gameType, resolvedKind.serializedName());
    }

    public static Optional<String> resolvePointLayerKey(String gameType, String rawLayerKey) {
        registerDefaults();
        String requestedKey = rawLayerKey == null || rawLayerKey.isBlank()
                ? SpawnPointKind.INITIAL.serializedName()
                : rawLayerKey.trim();
        return ModeMapEditorSchemaRegistry.pointLayerKeys(gameType).stream()
                .filter(key -> key.equalsIgnoreCase(requestedKey))
                .findFirst();
    }

    public static boolean supportsPointLayer(String gameType, String layerKey) {
        return resolvePointLayerKey(gameType, layerKey).isPresent();
    }

    public static List<String> areaLayerKeys(String gameType) {
        registerDefaults();
        return ModeMapEditorSchemaRegistry.areaLayerKeys(gameType);
    }

    public static Optional<String> resolveAreaLayerKey(String gameType, String rawLayerKey) {
        registerDefaults();
        String requestedKey = rawLayerKey == null ? "" : rawLayerKey.trim();
        if (requestedKey.isBlank()) {
            return Optional.empty();
        }
        return ModeMapEditorSchemaRegistry.areaLayerKeys(gameType).stream()
                .filter(key -> key.equalsIgnoreCase(requestedKey))
                .findFirst();
    }

    public static boolean supportsAreaLayer(String gameType, String layerKey) {
        return resolveAreaLayerKey(gameType, layerKey).isPresent();
    }

    public static Optional<SpawnPointKind> legacySpawnPointKind(String layerKey) {
        if (layerKey == null || layerKey.isBlank()) {
            return Optional.of(SpawnPointKind.INITIAL);
        }
        String requestedKey = layerKey.trim();
        for (SpawnPointKind kind : SpawnPointKind.values()) {
            if (kind.serializedName().equalsIgnoreCase(requestedKey)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    public static boolean supportsDynamicRespawnMerge(String gameType) {
        return supportsSpawnPointLayer(gameType, SpawnPointKind.DYNAMIC_CANDIDATE)
                && GameModeRegistry.hasCapability(gameType, ModeCapability.DYNAMIC_RESPAWN_POINTS);
    }

    public static boolean supportsMatchEndTeleport(String gameType) {
        return supportsObjectFeature(gameType, MATCH_END_TELEPORT);
    }

    public static boolean supportsObjectFeature(String gameType, String featureKey) {
        registerDefaults();
        return ModeMapEditorSchemaRegistry.supportsObjectFeature(gameType, featureKey);
    }
}
