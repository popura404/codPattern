package com.phasetranscrystal.fpsmatch.common.item.tool;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public interface WorldToolItem {
    void handleWorldInteraction(ServerPlayer player, ItemStack stack, ToolInteractionAction action, BlockPos clickedPos);
}
