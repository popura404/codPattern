package com.cdp.codpattern.app.zombies.map.object;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointKind;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record ZombiesInitialSpawnData(
        ResourceKey<Level> dimension,
        BlockPos pos,
        float yaw,
        float pitch
) {
    public static final Codec<ZombiesInitialSpawnData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ZombiesObjectCodecs.DIMENSION_CODEC.fieldOf("dimension").forGetter(ZombiesInitialSpawnData::dimension),
            BlockPos.CODEC.optionalFieldOf("pos", BlockPos.ZERO).forGetter(ZombiesInitialSpawnData::pos),
            Codec.FLOAT.optionalFieldOf("yaw", 0.0F).forGetter(ZombiesInitialSpawnData::yaw),
            Codec.FLOAT.optionalFieldOf("pitch", 0.0F).forGetter(ZombiesInitialSpawnData::pitch)
    ).apply(instance, ZombiesInitialSpawnData::new));

    public static ZombiesInitialSpawnData fromSpawnPointData(SpawnPointData data) {
        SpawnPointData resolved = data == null
                ? new SpawnPointData(Level.OVERWORLD, BlockPos.ZERO, 0.0F, 0.0F, SpawnPointKind.INITIAL)
                : data;
        return new ZombiesInitialSpawnData(
                resolved.getDimension(),
                resolved.getPosition(),
                resolved.getYaw(),
                resolved.getPitch());
    }

    public SpawnPointData toSpawnPointData() {
        return new SpawnPointData(dimension, pos, yaw, pitch, SpawnPointKind.INITIAL);
    }
}
