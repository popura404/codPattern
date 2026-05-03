package com.cdp.codpattern.app.zombies.map.object;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Optional;

public record ZombiesUltimateMachineData(
        String objectId,
        int maxUpgradeLevel,
        Map<String, UpgradeLevelData> levels,
        boolean requiresPower,
        ResourceKey<Level> dimension,
        BlockPos pos,
        Optional<BlockPos> interactionPos
) {
    public static final Codec<ZombiesUltimateMachineData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("objectId").forGetter(ZombiesUltimateMachineData::objectId),
            Codec.INT.optionalFieldOf("maxUpgradeLevel", 0).forGetter(ZombiesUltimateMachineData::maxUpgradeLevel),
            Codec.unboundedMap(Codec.STRING, UpgradeLevelData.CODEC)
                    .optionalFieldOf("levels", Map.<String, UpgradeLevelData>of())
                    .forGetter(ZombiesUltimateMachineData::levels),
            Codec.BOOL.optionalFieldOf("requiresPower", true).forGetter(ZombiesUltimateMachineData::requiresPower),
            ZombiesObjectCodecs.DIMENSION_CODEC.fieldOf("dimension").forGetter(ZombiesUltimateMachineData::dimension),
            BlockPos.CODEC.optionalFieldOf("pos", BlockPos.ZERO).forGetter(ZombiesUltimateMachineData::pos),
            BlockPos.CODEC.optionalFieldOf("interactionPos").forGetter(ZombiesUltimateMachineData::interactionPos)
    ).apply(instance, ZombiesUltimateMachineData::new));

    public ZombiesUltimateMachineData {
        objectId = objectId == null ? "" : objectId.trim();
        levels = levels == null ? Map.of() : Map.copyOf(levels);
        dimension = dimension == null ? Level.OVERWORLD : dimension;
        pos = pos == null ? BlockPos.ZERO : pos;
        interactionPos = interactionPos == null ? Optional.empty() : interactionPos;
    }

    public record UpgradeLevelData(
            int cost,
            double damageMultiplier
    ) {
        public static final Codec<UpgradeLevelData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("cost", 0).forGetter(UpgradeLevelData::cost),
                Codec.DOUBLE.optionalFieldOf("damageMultiplier", 1.0D).forGetter(UpgradeLevelData::damageMultiplier)
        ).apply(instance, UpgradeLevelData::new));
    }
}
