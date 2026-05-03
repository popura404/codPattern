package com.cdp.codpattern.app.zombies.map.object;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Optional;

public record ZombiesWindowData(
        String objectId,
        ResourceKey<Level> dimension,
        BlockPos areaFrom,
        BlockPos areaTo,
        Optional<BlockPos> interactionPos
) {
    public static final Codec<ZombiesWindowData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("objectId").forGetter(ZombiesWindowData::objectId),
            ZombiesObjectCodecs.DIMENSION_CODEC.fieldOf("dimension").forGetter(ZombiesWindowData::dimension),
            BlockPos.CODEC.optionalFieldOf("areaFrom", BlockPos.ZERO).forGetter(ZombiesWindowData::areaFrom),
            BlockPos.CODEC.optionalFieldOf("areaTo", BlockPos.ZERO).forGetter(ZombiesWindowData::areaTo),
            BlockPos.CODEC.optionalFieldOf("interactionPos").forGetter(ZombiesWindowData::interactionPos)
    ).apply(instance, ZombiesWindowData::new));

    public ZombiesWindowData {
        objectId = objectId == null ? "" : objectId.trim();
        dimension = dimension == null ? Level.OVERWORLD : dimension;
        areaFrom = areaFrom == null ? BlockPos.ZERO : areaFrom;
        areaTo = areaTo == null ? BlockPos.ZERO : areaTo;
        interactionPos = interactionPos == null ? Optional.empty() : interactionPos;
    }
}
