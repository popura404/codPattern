package com.cdp.codpattern.compat.lrtactical;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

public final class NoopLrTacticalGateway implements LrTacticalGateway {
    @Override
    public boolean isLoaded() {
        return false;
    }

    @Override
    public boolean isThrowableStack(ItemStack stack) {
        return false;
    }

    @Override
    public int getPrepareTicks(ItemStack stack) {
        return 0;
    }

    @Override
    public boolean isMeleeWeapon(ItemStack stack) {
        return false;
    }

    @Override
    public NonNullList<ItemStack> fillMeleeCategory() {
        return NonNullList.create();
    }

    @Override
    public NonNullList<ItemStack> fillThrowableCategory() {
        return NonNullList.create();
    }
}
