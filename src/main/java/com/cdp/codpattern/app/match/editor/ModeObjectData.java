package com.cdp.codpattern.app.match.editor;

import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;

public record ModeObjectData(
        String featureKey,
        ResourceKey<Level> dimension,
        BlockPos position,
        float yaw,
        float pitch,
        CompoundTag payload
) {
    public ModeObjectData {
        Objects.requireNonNull(featureKey, "featureKey");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(position, "position");
        payload = payload == null ? new CompoundTag() : payload.copy();
    }

    public static ModeObjectData fromSpawnPointData(String featureKey, SpawnPointData data) {
        Objects.requireNonNull(data, "data");
        return new ModeObjectData(
                featureKey,
                data.getDimension(),
                data.getPosition(),
                data.getYaw(),
                data.getPitch(),
                new CompoundTag());
    }

    public SpawnPointData toSpawnPointData() {
        return new SpawnPointData(dimension, position, yaw, pitch);
    }

    @Override
    public CompoundTag payload() {
        return payload.copy();
    }
}
