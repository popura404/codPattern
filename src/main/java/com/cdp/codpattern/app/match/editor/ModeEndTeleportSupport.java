package com.cdp.codpattern.app.match.editor;

import com.cdp.codpattern.app.match.ModeRoomHandle;
import com.cdp.codpattern.app.match.model.GameModeDefinition;
import com.cdp.codpattern.app.match.model.ModeCapability;
import com.cdp.codpattern.app.match.port.ModeMapEditPort;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Shared end-point editor and registration contract for playable modes, including addons. */
public final class ModeEndTeleportSupport {
    private static final String KEY = ModeMapEditorSchemas.MATCH_END_TELEPORT;
    private static final ModeMapEditorSchema SCHEMA = new ModeMapEditorSchema() {
        @Override public List<PointLayerDefinition> pointLayers() { return List.of(); }
        @Override public List<AreaLayerDefinition> areaLayers() { return List.of(); }
        @Override public List<ObjectFeatureDefinition> objectFeatures() {
            return List.of(new ObjectFeatureDefinition(KEY, "command.codpattern.map.endtp.feature", false));
        }
    };

    private ModeEndTeleportSupport() { }

    /** For modes whose other objects are edited by their own tools. */
    public static ModeMapEditorSchema schema() { return SCHEMA; }

    /** The setter must accept null, so a failed save can restore an unset point. */
    public static ModeMapEditPort editor(Supplier<Optional<SpawnPointData>> read, Consumer<SpawnPointData> write) {
        Objects.requireNonNull(read, "read");
        Objects.requireNonNull(write, "write");
        return new ModeMapEditPort() {
            @Override public boolean supportsPointLayer(String key) { return false; }
            @Override public List<ModePointData> pointLayerPoints(String team, String key) { return List.of(); }
            @Override public boolean addPointLayerPoint(String team, ModePointData point) { return false; }
            @Override public Optional<ModePointData> removePointLayerPoint(String team, String key, int index) {
                return Optional.empty();
            }
            @Override public void replacePointLayerPoints(String team, String key, List<ModePointData> points) {
                throw new IllegalArgumentException("Unsupported point layer: " + key);
            }
            @Override public boolean supportsObjectFeature(String key) { return KEY.equals(key); }
            @Override public Optional<ModeObjectData> objectFeature(String key) {
                return supportsObjectFeature(key) ? read.get().map(point -> ModeObjectData.fromSpawnPointData(KEY, point))
                        : Optional.empty();
            }
            @Override public void setObjectFeature(String key, ModeObjectData value) {
                if (!supportsObjectFeature(key) || value != null && !KEY.equals(value.featureKey())) {
                    throw new IllegalArgumentException("Unsupported object feature: " + key);
                }
                write.accept(value == null ? null : value.toSpawnPointData());
            }
        };
    }

    public static void requireDefinition(GameModeDefinition definition) {
        // Metadata-only definitions do not instantiate maps. Every playable mode must expose the editor.
        if (definition.runtimeProvider().isEmpty()) return;
        if (!definition.hasCapability(ModeCapability.MATCH_END_TELEPORT)
                || definition.editorSchema().filter(schema -> schema.supportsObjectFeature(KEY)).isEmpty()) {
            throw new IllegalStateException("Mode " + definition.gameType()
                    + " must declare MATCH_END_TELEPORT and the match_end_teleport editor feature");
        }
    }

    public static void requireHandle(String gameType, ModeRoomHandle handle) {
        if (handle == null || !handle.summaryPort().supportsConfiguredEndPoint()
                || handle.mapEditPort().filter(port -> port.supportsObjectFeature(KEY)).isEmpty()) {
            throw new IllegalStateException("Mode " + gameType
                    + " must expose configuredEndPoint and a writable match_end_teleport mapEditPort before registration");
        }
    }
}
