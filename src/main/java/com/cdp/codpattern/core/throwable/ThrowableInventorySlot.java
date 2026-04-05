package com.cdp.codpattern.core.throwable;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ThrowableInventorySlot extends Slot {
    public ThrowableInventorySlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return ThrowableItemHelper.isThrowableStack(stack);
    }
}
