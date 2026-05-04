package com.cdp.codpattern.app.zombies.model;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public record ZombiesEquipmentSlotSnapshot(
        int inventorySlot,
        ZombiesEquipmentSlot slot,
        ItemStack itemStack,
        ZombiesWeaponInstanceState weaponState
) {
    public ZombiesEquipmentSlotSnapshot {
        inventorySlot = Math.max(0, inventorySlot);
        slot = Objects.requireNonNull(slot, "slot");
        itemStack = itemStack == null ? null : itemStack.copy();
        Objects.requireNonNull(weaponState, "weaponState");
    }

    @Override
    public ItemStack itemStack() {
        return itemStack == null ? ItemStack.EMPTY : itemStack.copy();
    }

    public boolean starter() {
        return slot == ZombiesEquipmentSlot.STARTER;
    }

    public boolean primary() {
        return slot == ZombiesEquipmentSlot.PRIMARY;
    }
}
