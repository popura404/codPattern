package com.cdp.codpattern.core.refit;

import com.cdp.codpattern.mixin.accessor.InventoryAccessor;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class AttachmentRefitInventory extends Inventory {
    private static final int HOTBAR_SIZE = 9;
    private static final int VANILLA_MAIN_INVENTORY_SIZE = 36;

    private final int gunSlot;
    private final List<ItemStack> attachmentCandidates;
    private ItemStack gunStack;

    public AttachmentRefitInventory(Player player, int gunSlot, ItemStack gunStack, List<ItemStack> attachmentCandidates) {
        super(player);
        this.gunSlot = Math.max(0, Math.min(HOTBAR_SIZE - 1, gunSlot));
        this.gunStack = gunStack == null ? ItemStack.EMPTY : gunStack;
        this.attachmentCandidates = attachmentCandidates;
        this.selected = this.gunSlot;
        syncBackingItems();
    }

    @Override
    public int getContainerSize() {
        return HOTBAR_SIZE + attachmentCandidates.size() + 1;
    }

    @Override
    public boolean isEmpty() {
        if (!gunStack.isEmpty()) {
            return false;
        }
        for (ItemStack candidate : attachmentCandidates) {
            if (candidate != null && !candidate.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot == gunSlot) {
            return gunStack;
        }
        if (slot < 0 || slot < HOTBAR_SIZE) {
            return ItemStack.EMPTY;
        }
        int candidateIndex = slot - HOTBAR_SIZE;
        if (candidateIndex < 0 || candidateIndex >= attachmentCandidates.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack candidate = attachmentCandidates.get(candidateIndex);
        return candidate == null ? ItemStack.EMPTY : candidate;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        ItemStack normalized = normalize(stack);
        if (slot == gunSlot) {
            gunStack = normalized;
            syncBackingItems();
            return;
        }
        if (slot < HOTBAR_SIZE) {
            return;
        }
        int candidateIndex = slot - HOTBAR_SIZE;
        if (candidateIndex < 0) {
            return;
        }
        if (candidateIndex < attachmentCandidates.size()) {
            if (normalized.isEmpty()) {
                attachmentCandidates.remove(candidateIndex);
            } else {
                attachmentCandidates.set(candidateIndex, normalized);
            }
            syncBackingItems();
            return;
        }
        if (candidateIndex == attachmentCandidates.size() && !normalized.isEmpty()) {
            attachmentCandidates.add(normalized);
            syncBackingItems();
        }
    }

    @Override
    public ItemStack getSelected() {
        return gunStack;
    }

    @Override
    public int getFreeSlot() {
        return HOTBAR_SIZE + attachmentCandidates.size();
    }

    @Override
    public boolean add(ItemStack stack) {
        ItemStack normalized = normalize(stack);
        if (normalized.isEmpty()) {
            return false;
        }
        attachmentCandidates.add(normalized);
        syncBackingItems();
        return true;
    }

    @Override
    public void clearContent() {
        gunStack = ItemStack.EMPTY;
        attachmentCandidates.clear();
        syncBackingItems();
    }

    private void syncBackingItems() {
        NonNullList<ItemStack> backingItems = NonNullList.withSize(Math.max(VANILLA_MAIN_INVENTORY_SIZE, getContainerSize()),
                ItemStack.EMPTY);
        backingItems.set(gunSlot, gunStack);
        for (int i = 0; i < attachmentCandidates.size(); i++) {
            backingItems.set(HOTBAR_SIZE + i, normalize(attachmentCandidates.get(i)));
        }
        InventoryAccessor accessor = (InventoryAccessor) this;
        accessor.codpattern$setItems(backingItems);
        accessor.codpattern$setCompartments(List.of(this.items, this.armor, this.offhand));
    }

    private static ItemStack normalize(ItemStack stack) {
        return stack == null ? ItemStack.EMPTY : stack;
    }
}
