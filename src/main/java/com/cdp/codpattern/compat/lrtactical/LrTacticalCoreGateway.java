package com.cdp.codpattern.compat.lrtactical;

import me.xjqsh.lrtactical.api.LrTacticalAPI;
import me.xjqsh.lrtactical.api.item.IMeleeWeapon;
import me.xjqsh.lrtactical.api.item.IThrowable;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

public final class LrTacticalCoreGateway implements LrTacticalGateway {
    @Override
    public boolean isLoaded() {
        return true;
    }

    @Override
    public boolean isThrowableStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        IThrowable throwable = IThrowable.of(stack);
        if (throwable == null || IThrowable.EMPTY.equals(throwable.getId(stack))) {
            return false;
        }
        return throwable.getThrowableIndex(stack).isPresent();
    }

    @Override
    public int getPrepareTicks(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        IThrowable throwable = IThrowable.of(stack);
        if (throwable == null) {
            return 0;
        }
        return Math.max(0, throwable.getMaxUsingTick(stack));
    }

    @Override
    public boolean isMeleeWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        IMeleeWeapon meleeWeapon = IMeleeWeapon.of(stack);
        if (meleeWeapon == null || IMeleeWeapon.EMPTY.equals(meleeWeapon.getId(stack))) {
            return false;
        }
        return meleeWeapon.getMeleeIndex(stack).isPresent();
    }

    @Override
    public NonNullList<ItemStack> fillMeleeCategory() {
        NonNullList<ItemStack> stacks = NonNullList.create();
        LrTacticalAPI.getMeleeIndexes().forEach(entry -> stacks.add(entry.createItemStack()));
        return stacks;
    }

    @Override
    public NonNullList<ItemStack> fillThrowableCategory() {
        NonNullList<ItemStack> stacks = NonNullList.create();
        LrTacticalAPI.getThrowableIndexes().forEach(entry -> stacks.add(entry.createItemStack()));
        return stacks;
    }
}
