package com.cdp.codpattern.compat.lrtactical;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

public interface LrTacticalGateway {
    boolean isLoaded();

    boolean isThrowableStack(ItemStack stack);

    int getPrepareTicks(ItemStack stack);

    boolean isMeleeWeapon(ItemStack stack);

    NonNullList<ItemStack> fillMeleeCategory();

    NonNullList<ItemStack> fillThrowableCategory();
}
