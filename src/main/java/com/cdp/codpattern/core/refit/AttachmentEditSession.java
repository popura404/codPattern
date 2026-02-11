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
    private final long startedAtMs;
    private final long timeoutAtMs;
    private final int sandboxAttachmentCount;
    private final int truncatedAttachmentCount;

    public AttachmentEditSession(int bagId, String slot, int editHotbarSlot, int originalSelectedSlot,
            List<ItemStack> inventorySnapshot, long startedAtMs, long timeoutAtMs, int sandboxAttachmentCount,
            int truncatedAttachmentCount) {
        this.bagId = bagId;
        this.slot = slot;
        this.editHotbarSlot = editHotbarSlot;
        this.originalSelectedSlot = originalSelectedSlot;
        this.inventorySnapshot = new ArrayList<>(inventorySnapshot);
        this.startedAtMs = startedAtMs;
        this.timeoutAtMs = timeoutAtMs;
        this.sandboxAttachmentCount = sandboxAttachmentCount;
        this.truncatedAttachmentCount = truncatedAttachmentCount;
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

    public long getStartedAtMs() {
        return startedAtMs;
    }

    public long getTimeoutAtMs() {
        return timeoutAtMs;
    }

    public boolean isExpired(long nowMs) {
        return nowMs >= timeoutAtMs;
    }

    public int getSandboxAttachmentCount() {
        return sandboxAttachmentCount;
    }

    public int getTruncatedAttachmentCount() {
        return truncatedAttachmentCount;
    }
}
