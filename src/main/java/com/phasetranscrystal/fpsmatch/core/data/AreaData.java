package com.phasetranscrystal.fpsmatch.core.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record AreaData(BlockPos pos1, BlockPos pos2) {
    public static final Codec<AreaData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.optionalFieldOf("Position1", BlockPos.ZERO).forGetter(AreaData::pos1),
            BlockPos.CODEC.optionalFieldOf("Position2", BlockPos.ZERO).forGetter(AreaData::pos2)
    ).apply(instance, AreaData::new));

    public boolean isPlayerInArea(Player player) {
        return isInArea(player.position());
    }

    public boolean isBlockPosInArea(BlockPos blockPos) {
        return isInArea(Vec3.atCenterOf(blockPos));
    }

    public boolean isEntityInArea(Entity entity) {
        return isInArea(entity.position());
    }

    public boolean isInArea(Vec3 pos) {
        return getAABB().contains(pos);
    }

    public AABB getAABB() {
        return new AABB(
                Math.min(pos1.getX(), pos2.getX()),
                Math.min(pos1.getY(), pos2.getY()),
                Math.min(pos1.getZ(), pos2.getZ()),
                Math.max(pos1.getX(), pos2.getX()),
                Math.max(pos1.getY(), pos2.getY()),
                Math.max(pos1.getZ(), pos2.getZ())
        );
    }
}
