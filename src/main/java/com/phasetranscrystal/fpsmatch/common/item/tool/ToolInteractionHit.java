package com.phasetranscrystal.fpsmatch.common.item.tool;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record ToolInteractionHit(
        BlockPos clickedBlockPos,
        Direction clickedFace,
        BlockPos placementPos
) {
    public static ToolInteractionHit fromClicked(BlockPos clickedBlockPos, Direction clickedFace) {
        if (clickedBlockPos == null || clickedFace == null) {
            return null;
        }
        return new ToolInteractionHit(clickedBlockPos, clickedFace, clickedBlockPos.relative(clickedFace));
    }
}
