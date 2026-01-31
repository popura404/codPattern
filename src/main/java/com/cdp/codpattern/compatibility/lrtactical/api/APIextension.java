package com.cdp.codpattern.compatibility.lrtactical.api;

import me.xjqsh.lrtactical.api.LrTacticalAPI;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

public final class APIextension {

    /**
     * 获取所有lr melee物品的实例。用于填充物品栏。
     */
    public static NonNullList<ItemStack> fillLRItemCategory(Boolean isMelee){
        NonNullList<ItemStack> stacks = NonNullList.create();
        if (isMelee){
            LrTacticalAPI.getMeleeIndexes().stream().forEach(entry ->{
                ItemStack stack = entry.createItemStack();
                stacks.add(stack);
        });}else {
            LrTacticalAPI.getThrowableIndexes().stream().forEach(entry ->{
                ItemStack stack = entry.createItemStack();
                stacks.add(stack);
            });
        }
        return stacks;
    }
}
