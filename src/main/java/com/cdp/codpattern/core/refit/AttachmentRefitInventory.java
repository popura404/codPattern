package com.cdp.codpattern.core.refit;

import com.cdp.codpattern.mixin.accessor.InventoryAccessor;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * TaCZ's Inventory interface over a fixed session catalog with no player-inventory size limit.
 * Candidate slots are templates, not owned items: reads return copies and inventory writes
 * never consume, replace, or append candidates. Only the working gun is mutable.
 */
public class AttachmentRefitInventory extends Inventory {
    private static final int HOTBAR_SIZE = 9;
    private static final int VANILLA_MAIN_INVENTORY_SIZE = 36;

    private final int gunSlot;
    private final List<ItemStack> attachmentCandidates;
    private ItemStack gunStack;

    public AttachmentRefitInventory(Player player, int gunSlot, ItemStack gunStack, List<ItemStack> attachmentCandidates) {
        super(player);
        this.gunSlot = Math.max(0, Math.min(HOTBAR_SIZE - 1, gunSlot));
        this.gunStack = normalize(gunStack);
        this.attachmentCandidates = copyCandidates(attachmentCandidates);
        this.selected = this.gunSlot;
        InventoryAccessor accessor = (InventoryAccessor) this;
        accessor.codpattern$setItems(new CatalogItems());
        accessor.codpattern$setCompartments(List.of(this.items, this.armor, this.offhand));
    }

    @Override
    public int getContainerSize() {
        return HOTBAR_SIZE + attachmentCandidates.size() + 1;
    }

    public int getCandidateCount() {
        return attachmentCandidates.size();
    }

    /** Includes empty positions so a snapshot cannot change any candidate's slot number. */
    public List<ItemStack> snapshotCandidates() {
        return copyCandidates(attachmentCandidates);
    }

    @Override
    public boolean isEmpty() {
        if (!gunStack.isEmpty()) {
            return false;
        }
        for (ItemStack candidate : attachmentCandidates) {
            if (!candidate.isEmpty()) {
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
        int candidateIndex = slot - HOTBAR_SIZE;
        if (candidateIndex < 0 || candidateIndex >= attachmentCandidates.size()) {
            return ItemStack.EMPTY;
        }
        return attachmentCandidates.get(candidateIndex).copy();
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot == gunSlot) {
            gunStack = normalize(stack);
        }
        // TaCZ writes the previously installed attachment into the source slot.
        // In a catalog session that return is acknowledged without replacing its template.
    }

    @Override
    public ItemStack getSelected() {
        return gunStack;
    }

    @Override
    public int getFreeSlot() {
        // Keep a stable empty return slot for callers that use setItem instead of add.
        return HOTBAR_SIZE + attachmentCandidates.size();
    }

    @Override
    public boolean add(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        // Match Inventory's successful insertion contract, including consuming the input.
        // These are preview returns; retaining them would mutate the catalog or spawn items.
        stack.setCount(0);
        return true;
    }

    @Override
    public boolean add(int slot, ItemStack stack) {
        return add(stack);
    }

    @Override
    public void placeItemBackInInventory(ItemStack stack) {
        add(stack);
    }

    @Override
    public void placeItemBackInInventory(ItemStack stack, boolean sendPacket) {
        // Catalog slots must never be sent as slot updates for the real player inventory.
        add(stack);
    }

    @Override
    public void clearContent() {
        gunStack = ItemStack.EMPTY;
        armor.clear();
        offhand.clear();
    }

    /**
     * Some TaCZ/Addon code accesses Inventory.items directly. A read-through view gives
     * that path exactly the same copy/write semantics as getItem/setItem, without keeping
     * a second mutable candidate store. Like vanilla's fixed-size NonNullList, this view
     * rejects structural list operations; callers return items through set/add on Inventory.
     * The minimum backing size supports vanilla main-inventory iteration without imposing
     * a catalog capacity limit.
     */
    private final class CatalogItems extends NonNullList<ItemStack> {
        private CatalogItems() {
            super(List.of(), ItemStack.EMPTY);
        }

        @Override
        public int size() {
            return Math.max(VANILLA_MAIN_INVENTORY_SIZE, getContainerSize());
        }

        @Override
        public ItemStack get(int index) {
            Objects.checkIndex(index, size());
            return getItem(index);
        }

        @Override
        public ItemStack set(int index, ItemStack element) {
            Objects.checkIndex(index, size());
            Objects.requireNonNull(element);
            ItemStack previous = getItem(index);
            setItem(index, element);
            return previous;
        }

        @Override
        public void add(int index, ItemStack element) {
            throw new UnsupportedOperationException("Candidate slots are fixed for the session");
        }

        @Override
        public ItemStack remove(int index) {
            throw new UnsupportedOperationException("Candidate slots are fixed for the session");
        }

        @Override
        public void clear() {
            gunStack = ItemStack.EMPTY;
        }
    }

    private static List<ItemStack> copyCandidates(List<ItemStack> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<ItemStack> copy = new ArrayList<>(candidates.size());
        for (ItemStack candidate : candidates) {
            copy.add(normalize(candidate).copy());
        }
        return copy;
    }

    private static ItemStack normalize(ItemStack stack) {
        return stack == null ? ItemStack.EMPTY : stack;
    }
}
