package com.cdp.codpattern.core.refit;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class AttachmentEditSession {
    private final int bagId;
    private final String slot;
    private final int editHotbarSlot;
    private final int originalSelectedSlot;
    private final List<ItemStack> inventorySnapshot;
    private final AttachmentRefitInventory refitInventory;

    public AttachmentEditSession(int bagId, String slot, int editHotbarSlot, int originalSelectedSlot,
            List<ItemStack> inventorySnapshot, AttachmentRefitInventory refitInventory) {
        this.bagId = bagId;
        this.slot = slot;
        this.editHotbarSlot = editHotbarSlot;
        this.originalSelectedSlot = originalSelectedSlot;
        this.inventorySnapshot = new ArrayList<>(inventorySnapshot);
        this.refitInventory = refitInventory;
    }

    public int getBagId() {
        return bagId;
    }

    public String getSlot() {
        return slot;
    }

    public int getEditHotbarSlot() {
        return editHotbarSlot;
    }

    public int getOriginalSelectedSlot() {
        return originalSelectedSlot;
    }

    public List<ItemStack> getInventorySnapshot() {
        return inventorySnapshot;
    }

    public AttachmentRefitInventory getRefitInventory() {
        return refitInventory;
    }

}
