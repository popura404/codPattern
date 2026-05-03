package com.cdp.codpattern.app.zombies.map.object;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Optional;

public record ZombiesArmorStationData(
        String objectId,
        int armorLevel,
        int buyCost,
        double damageTakenMultiplier,
        ResourceKey<Level> dimension,
        BlockPos pos,
        Optional<BlockPos> interactionPos
) {
    public static final Codec<ZombiesArmorStationData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("objectId").forGetter(ZombiesArmorStationData::objectId),
            Codec.INT.optionalFieldOf("armorLevel", 1).forGetter(ZombiesArmorStationData::armorLevel),
            Codec.INT.optionalFieldOf("buyCost", 0).forGetter(ZombiesArmorStationData::buyCost),
            Codec.DOUBLE.optionalFieldOf("damageTakenMultiplier", 1.0D).forGetter(ZombiesArmorStationData::damageTakenMultiplier),
            ZombiesObjectCodecs.DIMENSION_CODEC.fieldOf("dimension").forGetter(ZombiesArmorStationData::dimension),
            BlockPos.CODEC.optionalFieldOf("pos", BlockPos.ZERO).forGetter(ZombiesArmorStationData::pos),
            BlockPos.CODEC.optionalFieldOf("interactionPos").forGetter(ZombiesArmorStationData::interactionPos)
    ).apply(instance, ZombiesArmorStationData::new));

    public ZombiesArmorStationData {
        objectId = objectId == null ? "" : objectId.trim();
        dimension = dimension == null ? Level.OVERWORLD : dimension;
        pos = pos == null ? BlockPos.ZERO : pos;
        interactionPos = interactionPos == null ? Optional.empty() : interactionPos;
    }
}
