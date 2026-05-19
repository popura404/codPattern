package com.cdp.codpattern.app.zombies.map.object;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Optional;

public record ZombiesWeaponWallData(
        String objectId,
        ResourceKey<Level> dimension,
        BlockPos pos,
        Optional<BlockPos> interactionPos
) {
    private static final Codec<ZombiesWeaponWallData> CORE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("objectId").forGetter(ZombiesWeaponWallData::objectId),
            ZombiesObjectCodecs.DIMENSION_CODEC.fieldOf("dimension").forGetter(ZombiesWeaponWallData::dimension),
            BlockPos.CODEC.optionalFieldOf("pos", BlockPos.ZERO).forGetter(ZombiesWeaponWallData::pos),
            BlockPos.CODEC.optionalFieldOf("interactionPos").forGetter(ZombiesWeaponWallData::interactionPos)
    ).apply(instance, ZombiesWeaponWallData::new));

    public static final Codec<ZombiesWeaponWallData> CODEC = new Codec<>() {
        @Override
        public <T> com.mojang.serialization.DataResult<com.mojang.datafixers.util.Pair<ZombiesWeaponWallData, T>> decode(
                DynamicOps<T> ops,
                T input
        ) {
            return CORE_CODEC.decode(ops, input);
        }

        @Override
        public <T> com.mojang.serialization.DataResult<T> encode(
                ZombiesWeaponWallData input,
                DynamicOps<T> ops,
                T prefix
        ) {
            RecordBuilder<T> builder = ops.mapBuilder();
            builder.add("objectId", Codec.STRING.encodeStart(ops, input.objectId()));
            builder.add("dimension", ZombiesObjectCodecs.DIMENSION_CODEC.encodeStart(ops, input.dimension()));
            builder.add("pos", BlockPos.CODEC.encodeStart(ops, input.pos()));
            input.interactionPos().ifPresent(pos -> builder.add("interactionPos", BlockPos.CODEC.encodeStart(ops, pos)));
            return builder.build(prefix);
        }
    };

    public ZombiesWeaponWallData {
        objectId = objectId == null ? "" : objectId.trim();
        dimension = dimension == null ? Level.OVERWORLD : dimension;
        pos = pos == null ? BlockPos.ZERO : pos;
        interactionPos = interactionPos == null ? Optional.empty() : interactionPos;
    }
}
