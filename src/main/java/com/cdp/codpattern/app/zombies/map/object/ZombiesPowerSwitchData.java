package com.cdp.codpattern.app.zombies.map.object;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Optional;

public record ZombiesPowerSwitchData(
        String objectId,
        String block,
        int cost,
        boolean emitsRedstoneWhenPowered,
        ResourceKey<Level> dimension,
        BlockPos pos,
        Optional<BlockPos> interactionPos
) {
    public static final Codec<ZombiesPowerSwitchData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("objectId").forGetter(ZombiesPowerSwitchData::objectId),
            Codec.STRING.optionalFieldOf("block", "codpattern:zombies_power_switch").forGetter(ZombiesPowerSwitchData::block),
            Codec.INT.optionalFieldOf("cost", 0).forGetter(ZombiesPowerSwitchData::cost),
            Codec.BOOL.optionalFieldOf("emitsRedstoneWhenPowered", true).forGetter(ZombiesPowerSwitchData::emitsRedstoneWhenPowered),
            ZombiesObjectCodecs.DIMENSION_CODEC.fieldOf("dimension").forGetter(ZombiesPowerSwitchData::dimension),
            BlockPos.CODEC.optionalFieldOf("pos", BlockPos.ZERO).forGetter(ZombiesPowerSwitchData::pos),
            BlockPos.CODEC.optionalFieldOf("interactionPos").forGetter(ZombiesPowerSwitchData::interactionPos)
    ).apply(instance, ZombiesPowerSwitchData::new));

    public ZombiesPowerSwitchData {
        objectId = objectId == null ? "" : objectId.trim();
        block = block == null ? "" : block.trim();
        dimension = dimension == null ? Level.OVERWORLD : dimension;
        pos = pos == null ? BlockPos.ZERO : pos;
        interactionPos = interactionPos == null ? Optional.empty() : interactionPos;
    }
}
