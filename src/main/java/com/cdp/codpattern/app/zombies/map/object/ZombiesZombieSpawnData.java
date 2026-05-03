package com.cdp.codpattern.app.zombies.map.object;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record ZombiesZombieSpawnData(
        String objectId,
        int group,
        double weight,
        ResourceKey<Level> dimension,
        BlockPos pos,
        float yaw,
        float pitch
) {
    public static final Codec<ZombiesZombieSpawnData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("objectId").forGetter(ZombiesZombieSpawnData::objectId),
            Codec.INT.optionalFieldOf("group", 1).forGetter(ZombiesZombieSpawnData::group),
            Codec.DOUBLE.optionalFieldOf("weight", 1.0D).forGetter(ZombiesZombieSpawnData::weight),
            ZombiesObjectCodecs.DIMENSION_CODEC.fieldOf("dimension").forGetter(ZombiesZombieSpawnData::dimension),
            BlockPos.CODEC.optionalFieldOf("pos", BlockPos.ZERO).forGetter(ZombiesZombieSpawnData::pos),
            Codec.FLOAT.optionalFieldOf("yaw", 0.0F).forGetter(ZombiesZombieSpawnData::yaw),
            Codec.FLOAT.optionalFieldOf("pitch", 0.0F).forGetter(ZombiesZombieSpawnData::pitch)
    ).apply(instance, ZombiesZombieSpawnData::new));
}
