package com.cdp.codpattern.app.zombies.map.object;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Optional;

public record ZombiesAmmoBoxData(
        String objectId,
        Map<String, Integer> pricesByWeaponLevel,
        ResourceKey<Level> dimension,
        BlockPos pos,
        Optional<BlockPos> interactionPos
) {
    public static final Codec<ZombiesAmmoBoxData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("objectId").forGetter(ZombiesAmmoBoxData::objectId),
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .optionalFieldOf("pricesByWeaponLevel", Map.<String, Integer>of())
                    .forGetter(ZombiesAmmoBoxData::pricesByWeaponLevel),
            ZombiesObjectCodecs.DIMENSION_CODEC.fieldOf("dimension").forGetter(ZombiesAmmoBoxData::dimension),
            BlockPos.CODEC.optionalFieldOf("pos", BlockPos.ZERO).forGetter(ZombiesAmmoBoxData::pos),
            BlockPos.CODEC.optionalFieldOf("interactionPos").forGetter(ZombiesAmmoBoxData::interactionPos)
    ).apply(instance, ZombiesAmmoBoxData::new));

    public ZombiesAmmoBoxData {
        objectId = objectId == null ? "" : objectId.trim();
        pricesByWeaponLevel = pricesByWeaponLevel == null ? Map.of() : Map.copyOf(pricesByWeaponLevel);
        dimension = dimension == null ? Level.OVERWORLD : dimension;
        pos = pos == null ? BlockPos.ZERO : pos;
        interactionPos = interactionPos == null ? Optional.empty() : interactionPos;
    }
}
