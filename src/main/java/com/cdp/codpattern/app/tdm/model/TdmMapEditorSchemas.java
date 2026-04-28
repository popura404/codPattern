package com.cdp.codpattern.app.tdm.model;

import com.cdp.codpattern.app.match.editor.AreaLayerDefinition;
import com.cdp.codpattern.app.match.editor.ModeMapEditorSchema;
import com.cdp.codpattern.app.match.editor.ModeMapEditorSchemaRegistry;
import com.cdp.codpattern.app.match.editor.ObjectFeatureDefinition;
import com.cdp.codpattern.app.match.editor.PointLayerDefinition;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointKind;

import java.util.List;

public final class TdmMapEditorSchemas {
    public static final String MATCH_END_TELEPORT = "match_end_teleport";

    private static final ModeMapEditorSchema FRONTLINE_SCHEMA = schema(List.of(initialSpawnLayer()));
    private static final ModeMapEditorSchema TEAM_DEATHMATCH_SCHEMA = schema(List.of(
            initialSpawnLayer(),
            dynamicRespawnCandidateLayer()
    ));

    private TdmMapEditorSchemas() {
    }

    public static void registerDefaults() {
        ModeMapEditorSchemaRegistry.register(TdmGameTypes.CDP_TDM, FRONTLINE_SCHEMA);
        ModeMapEditorSchemaRegistry.register(TdmGameTypes.CDP_TACTICAL_TDM, TEAM_DEATHMATCH_SCHEMA);
    }

    public static List<String> spawnPointLayerKeys(String gameType) {
        registerDefaults();
        List<String> keys = ModeMapEditorSchemaRegistry.pointLayerKeys(gameType);
        return keys.isEmpty() ? List.of(SpawnPointKind.INITIAL.serializedName()) : keys;
    }

    public static boolean supportsSpawnPointLayer(String gameType, SpawnPointKind kind) {
        registerDefaults();
        SpawnPointKind resolvedKind = kind == null ? SpawnPointKind.INITIAL : kind;
        return ModeMapEditorSchemaRegistry.supportsPointLayer(gameType, resolvedKind.serializedName());
    }

    public static boolean supportsDynamicRespawnMerge(String gameType) {
        return supportsSpawnPointLayer(gameType, SpawnPointKind.DYNAMIC_CANDIDATE)
                && TdmGameTypes.supportsDynamicRespawnPoints(gameType);
    }

    public static boolean supportsMatchEndTeleport(String gameType) {
        registerDefaults();
        return ModeMapEditorSchemaRegistry.supportsObjectFeature(gameType, MATCH_END_TELEPORT);
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
