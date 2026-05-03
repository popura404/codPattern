package com.cdp.codpattern.app.zombies.map.object;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Optional;

public record ZombiesSodaMachineData(
        String objectId,
        String buffId,
        int cost,
        boolean requiresPower,
        ResourceKey<Level> dimension,
        BlockPos pos,
        Optional<BlockPos> interactionPos
) {
    public static final Codec<ZombiesSodaMachineData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("objectId").forGetter(ZombiesSodaMachineData::objectId),
            Codec.STRING.fieldOf("buffId").forGetter(ZombiesSodaMachineData::buffId),
            Codec.INT.optionalFieldOf("cost", 0).forGetter(ZombiesSodaMachineData::cost),
            Codec.BOOL.optionalFieldOf("requiresPower", true).forGetter(ZombiesSodaMachineData::requiresPower),
            ZombiesObjectCodecs.DIMENSION_CODEC.fieldOf("dimension").forGetter(ZombiesSodaMachineData::dimension),
            BlockPos.CODEC.optionalFieldOf("pos", BlockPos.ZERO).forGetter(ZombiesSodaMachineData::pos),
            BlockPos.CODEC.optionalFieldOf("interactionPos").forGetter(ZombiesSodaMachineData::interactionPos)
    ).apply(instance, ZombiesSodaMachineData::new));

    public ZombiesSodaMachineData {
        objectId = objectId == null ? "" : objectId.trim();
        buffId = buffId == null ? "" : buffId.trim();
        dimension = dimension == null ? Level.OVERWORLD : dimension;
        pos = pos == null ? BlockPos.ZERO : pos;
        interactionPos = interactionPos == null ? Optional.empty() : interactionPos;
    }
}
