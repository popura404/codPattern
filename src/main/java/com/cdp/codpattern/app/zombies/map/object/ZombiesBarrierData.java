package com.cdp.codpattern.app.zombies.map.object;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record ZombiesBarrierData(
        String objectId,
        int group,
        int cost,
        boolean blocksPlayersOnly,
        ResourceKey<Level> dimension,
        BlockPos areaFrom,
        BlockPos areaTo,
        BlockPos interactionPos
) {
    public static final Codec<ZombiesBarrierData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("objectId").forGetter(ZombiesBarrierData::objectId),
            Codec.INT.optionalFieldOf("group", 1).forGetter(ZombiesBarrierData::group),
            Codec.INT.optionalFieldOf("cost", 0).forGetter(ZombiesBarrierData::cost),
            Codec.BOOL.optionalFieldOf("blocksPlayersOnly", true).forGetter(ZombiesBarrierData::blocksPlayersOnly),
            ZombiesObjectCodecs.DIMENSION_CODEC.fieldOf("dimension").forGetter(ZombiesBarrierData::dimension),
            BlockPos.CODEC.optionalFieldOf("areaFrom", BlockPos.ZERO).forGetter(ZombiesBarrierData::areaFrom),
            BlockPos.CODEC.optionalFieldOf("areaTo", BlockPos.ZERO).forGetter(ZombiesBarrierData::areaTo),
            BlockPos.CODEC.optionalFieldOf("interactionPos", BlockPos.ZERO).forGetter(ZombiesBarrierData::interactionPos)
    ).apply(instance, ZombiesBarrierData::new));
}
