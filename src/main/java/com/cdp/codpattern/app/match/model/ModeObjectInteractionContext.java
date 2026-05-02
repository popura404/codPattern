package com.cdp.codpattern.app.match.model;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public record ModeObjectInteractionContext(
        RoomId roomId,
        InteractionHand hand,
        BlockPos blockPos,
        Direction face,
        Entity targetEntity,
        ItemStack itemStack
) {
    public ModeObjectInteractionContext {
        itemStack = itemStack == null ? ItemStack.EMPTY : itemStack.copy();
    }
}
