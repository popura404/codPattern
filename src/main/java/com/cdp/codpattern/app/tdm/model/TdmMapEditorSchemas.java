package com.cdp.codpattern.app.tdm.model;

import com.cdp.codpattern.app.match.editor.AreaLayerDefinition;
import com.cdp.codpattern.app.match.editor.ModeMapEditorSchema;
import com.cdp.codpattern.app.match.editor.ModeMapEditorSchemas;
import com.cdp.codpattern.app.match.editor.ObjectFeatureDefinition;
import com.cdp.codpattern.app.match.editor.PointLayerDefinition;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointKind;

import java.util.List;
import java.util.Optional;

/**
 * @deprecated Legacy TDM editor schema facade. New code should use {@link ModeMapEditorSchemas}.
 */
@Deprecated(forRemoval = false)
public final class TdmMapEditorSchemas {
    public static final String MATCH_END_TELEPORT = ModeMapEditorSchemas.MATCH_END_TELEPORT;

    private static final ModeMapEditorSchema FRONTLINE_SCHEMA = schema(List.of(initialSpawnLayer()));
    private static final ModeMapEditorSchema TEAM_DEATHMATCH_SCHEMA = schema(List.of(
            initialSpawnLayer(),
            dynamicRespawnCandidateLayer()
    ));

    private TdmMapEditorSchemas() {
    }

    public static void registerDefaults() {
        ModeMapEditorSchemas.registerDefaults();
    }

    public static ModeMapEditorSchema frontlineSchema() {
        return FRONTLINE_SCHEMA;
    }

    public static ModeMapEditorSchema teamDeathmatchSchema() {
        return TEAM_DEATHMATCH_SCHEMA;
    }

    public static List<String> spawnPointLayerKeys(String gameType) {
        return ModeMapEditorSchemas.spawnPointLayerKeys(gameType);
    }

    public static boolean supportsSpawnPointLayer(String gameType, SpawnPointKind kind) {
        return ModeMapEditorSchemas.supportsSpawnPointLayer(gameType, kind);
    }

    public static Optional<String> resolvePointLayerKey(String gameType, String rawLayerKey) {
        return ModeMapEditorSchemas.resolvePointLayerKey(gameType, rawLayerKey);
    }

    public static boolean supportsPointLayer(String gameType, String layerKey) {
        return ModeMapEditorSchemas.supportsPointLayer(gameType, layerKey);
    }

    public static Optional<SpawnPointKind> legacySpawnPointKind(String layerKey) {
        return ModeMapEditorSchemas.legacySpawnPointKind(layerKey);
    }

    public static boolean supportsDynamicRespawnMerge(String gameType) {
        return ModeMapEditorSchemas.supportsDynamicRespawnMerge(gameType);
    }

    public static boolean supportsMatchEndTeleport(String gameType) {
        return ModeMapEditorSchemas.supportsMatchEndTeleport(gameType);
    }

    private static ModeMapEditorSchema schema(List<PointLayerDefinition> pointLayers) {
        return new ModeMapEditorSchema() {
            @Override
            public List<PointLayerDefinition> pointLayers() {
                return pointLayers;
            }

            @Override
            public List<AreaLayerDefinition> areaLayers() {
                return List.of();
            }

            @Override
            public List<ObjectFeatureDefinition> objectFeatures() {
                return List.of(new ObjectFeatureDefinition(
                        MATCH_END_TELEPORT,
                        "command.codpattern.map.endtp.feature",
                        false));
            }
        };
    }

    private static PointLayerDefinition initialSpawnLayer() {
        return new PointLayerDefinition(
                SpawnPointKind.INITIAL.serializedName(),
                "command.codpattern.map.spawn.kind.initial",
                true,
                true);
    }

    private static PointLayerDefinition dynamicRespawnCandidateLayer() {
        return new PointLayerDefinition(
                SpawnPointKind.DYNAMIC_CANDIDATE.serializedName(),
                "command.codpattern.map.spawn.kind.dynamic_candidate",
                true,
                true);
    }
}
