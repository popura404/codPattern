package com.cdp.codpattern.mixin.accessor;

import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractContainerMenu.class)
public interface AbstractContainerMenuAccessor {
    @Accessor("slots")
    NonNullList<Slot> codpattern$getSlots();

    @Invoker("addSlot")
    Slot codpattern$invokeAddSlot(Slot slot);

    @Invoker("moveItemStackTo")
    boolean codpattern$invokeMoveItemStackTo(ItemStack stack, int startIndex, int endIndex, boolean reverseDirection);
}
