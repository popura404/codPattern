package com.phasetranscrystal.fpsmatch.core.map;

import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class SpawnSafetyValidator {
    private SpawnSafetyValidator() {
    }

    public static boolean isSafe(ServerLevel level, ServerPlayer player, SpawnPointData data) {
        if (level == null || player == null || data == null) {
            return false;
        }

        BlockPos feet = data.getPosition();
        BlockPos head = feet.above();
        BlockPos ground = feet.below();

        BlockState groundState = level.getBlockState(ground);
        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(head);

        if (!groundState.blocksMotion()) {
            return false;
        }
        if (!feetState.getFluidState().isEmpty() || !headState.getFluidState().isEmpty()) {
            return false;
        }
        if (!feetState.getCollisionShape(level, feet).isEmpty()) {
            return false;
        }
        if (!headState.getCollisionShape(level, head).isEmpty()) {
            return false;
        }
        if (isHazardous(groundState) || isHazardous(feetState) || isHazardous(headState)) {
            return false;
        }

        double targetX = data.getX() + 0.5D;
        double targetY = data.getY();
        double targetZ = data.getZ() + 0.5D;
        AABB spawnBox = player.getBoundingBox().move(
                targetX - player.getX(),
                targetY - player.getY(),
                targetZ - player.getZ()
        );
        return level.noCollision(player, spawnBox);
    }

    private static boolean isHazardous(BlockState state) {
        return state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.LAVA)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE);
    }
}
