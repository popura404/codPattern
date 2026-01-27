package com.cdp.codpattern.core.refit;

import net.minecraft.world.item.ItemStack;

public class AttachmentEditSession {
    private final int bagId;
    private final String slot;
    private final int editHotbarSlot;
    private final int originalSelectedSlot;
    private final ItemStack backupStack;

    public AttachmentEditSession(int bagId, String slot, int editHotbarSlot, int originalSelectedSlot, ItemStack backupStack) {
        this.bagId = bagId;
        this.slot = slot;
        this.editHotbarSlot = editHotbarSlot;
        this.originalSelectedSlot = originalSelectedSlot;
        this.backupStack = backupStack;
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

    public ItemStack getBackupStack() {
        return backupStack;
    }
}
