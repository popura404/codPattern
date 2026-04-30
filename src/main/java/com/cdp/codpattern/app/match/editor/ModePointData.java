package com.cdp.codpattern.app.match.editor;

import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointKind;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;

public record ModePointData(
        String layerKey,
        ResourceKey<Level> dimension,
        BlockPos position,
        float yaw,
        float pitch
) {
    public ModePointData {
        Objects.requireNonNull(layerKey, "layerKey");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(position, "position");
    }

    public static ModePointData fromSpawnPointData(String layerKey, SpawnPointData data) {
        Objects.requireNonNull(data, "data");
        return new ModePointData(
                layerKey,
                data.getDimension(),
                data.getPosition(),
                data.getYaw(),
                data.getPitch());
    }

    public SpawnPointData toSpawnPointData(SpawnPointKind kind) {
        return new SpawnPointData(dimension, position, yaw, pitch, kind);
    }

    public SpawnPointData toDisplaySpawnPointData() {
        return toSpawnPointData(SpawnPointKind.INITIAL);
    }
}
