package com.cdp.codpattern.mixin.accessor;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(Inventory.class)
public interface InventoryAccessor {
    @Accessor("items")
    @Mutable
    void codpattern$setItems(NonNullList<ItemStack> items);

    @Accessor("compartments")
    @Mutable
    void codpattern$setCompartments(List<NonNullList<ItemStack>> compartments);
}
