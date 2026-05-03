package com.cdp.codpattern.app.zombies.map.object;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record ZombiesWeaponWallData(
        String objectId,
        int weaponLevel,
        double levelDamageMultiplier,
        int price,
        List<Integer> refreshWaves,
        List<RarityPoolData> rarityPools,
        List<WeaponCandidateData> weapons,
        ResourceKey<Level> dimension,
        BlockPos pos,
        Optional<BlockPos> interactionPos
) {
    public static final Codec<ZombiesWeaponWallData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("objectId").forGetter(ZombiesWeaponWallData::objectId),
            Codec.INT.optionalFieldOf("weaponLevel", 1).forGetter(ZombiesWeaponWallData::weaponLevel),
            Codec.DOUBLE.optionalFieldOf("levelDamageMultiplier", 1.0D).forGetter(ZombiesWeaponWallData::levelDamageMultiplier),
            Codec.INT.optionalFieldOf("price", 0).forGetter(ZombiesWeaponWallData::price),
            Codec.INT.listOf().optionalFieldOf("refreshWaves", List.of()).forGetter(ZombiesWeaponWallData::refreshWaves),
            RarityPoolData.CODEC.listOf().optionalFieldOf("rarityPools", List.of()).forGetter(ZombiesWeaponWallData::rarityPools),
            WeaponCandidateData.CODEC.listOf().optionalFieldOf("weapons", List.of()).forGetter(ZombiesWeaponWallData::weapons),
            ZombiesObjectCodecs.DIMENSION_CODEC.fieldOf("dimension").forGetter(ZombiesWeaponWallData::dimension),
            BlockPos.CODEC.optionalFieldOf("pos", BlockPos.ZERO).forGetter(ZombiesWeaponWallData::pos),
            BlockPos.CODEC.optionalFieldOf("interactionPos").forGetter(ZombiesWeaponWallData::interactionPos)
    ).apply(instance, ZombiesWeaponWallData::new));

    public ZombiesWeaponWallData {
        objectId = objectId == null ? "" : objectId.trim();
        refreshWaves = refreshWaves == null ? List.of() : List.copyOf(refreshWaves);
        rarityPools = rarityPools == null ? List.of() : List.copyOf(rarityPools);
        weapons = weapons == null ? List.of() : List.copyOf(weapons);
        dimension = dimension == null ? Level.OVERWORLD : dimension;
        pos = pos == null ? BlockPos.ZERO : pos;
        interactionPos = interactionPos == null ? Optional.empty() : interactionPos;
    }

    public record RarityPoolData(
            String id,
            int rank,
            double baseWeight,
            double waveFactor
    ) {
        public static final Codec<RarityPoolData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(RarityPoolData::id),
                Codec.INT.optionalFieldOf("rank", 0).forGetter(RarityPoolData::rank),
                Codec.DOUBLE.optionalFieldOf("baseWeight", 0.0D).forGetter(RarityPoolData::baseWeight),
                Codec.DOUBLE.optionalFieldOf("waveFactor", 0.0D).forGetter(RarityPoolData::waveFactor)
        ).apply(instance, RarityPoolData::new));

        public RarityPoolData {
            id = id == null ? "" : id.trim();
        }
    }

    public record WeaponCandidateData(
            String gunId,
            Map<String, Double> weightsByRarity
    ) {
        public static final Codec<WeaponCandidateData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("gunId").forGetter(WeaponCandidateData::gunId),
                Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                        .optionalFieldOf("weightsByRarity", Map.<String, Double>of())
                        .forGetter(WeaponCandidateData::weightsByRarity)
        ).apply(instance, WeaponCandidateData::new));

        public WeaponCandidateData {
            gunId = gunId == null ? "" : gunId.trim();
            weightsByRarity = weightsByRarity == null ? Map.of() : Map.copyOf(weightsByRarity);
        }
    }
}
