package com.cdp.codpattern.core.refit;

import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AttachmentEditSession {
    private final UUID sessionId;
    private final int bagId;
    private final String slot;
    private final int editHotbarSlot;
    private final int originalSelectedSlot;
    private final List<ItemStack> inventorySnapshot;
    private final AttachmentRefitInventory refitInventory;
    private final String expectedGunId;

    public AttachmentEditSession(int bagId, String slot, int editHotbarSlot, int originalSelectedSlot,
            List<ItemStack> inventorySnapshot, AttachmentRefitInventory refitInventory) {
        this(UUID.randomUUID(), bagId, slot, editHotbarSlot, originalSelectedSlot, inventorySnapshot, refitInventory);
    }

    public AttachmentEditSession(UUID sessionId, int bagId, String slot, int editHotbarSlot, int originalSelectedSlot,
            List<ItemStack> inventorySnapshot, AttachmentRefitInventory refitInventory) {
        this(sessionId, bagId, slot, editHotbarSlot, originalSelectedSlot, inventorySnapshot, refitInventory,
                refitInventory == null ? "" : TaczGatewayProvider.gateway().resolveGunId(refitInventory.getSelected()).orElse(""));
    }

    AttachmentEditSession(UUID sessionId, int bagId, String slot, int editHotbarSlot, int originalSelectedSlot,
            List<ItemStack> inventorySnapshot, AttachmentRefitInventory refitInventory, String expectedGunId) {
        this.sessionId = sessionId;
        this.bagId = bagId;
        this.slot = slot;
        this.editHotbarSlot = editHotbarSlot;
        this.originalSelectedSlot = originalSelectedSlot;
        this.inventorySnapshot = new ArrayList<>(inventorySnapshot);
        this.refitInventory = refitInventory;
        this.expectedGunId = expectedGunId;
    }

    public UUID getSessionId() {
        return sessionId;
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

    public boolean matchesWorkingGun(Inventory inventory, ItemStack gun) {
        return inventory.getItem(editHotbarSlot) == gun && !expectedGunId.isEmpty()
                && TaczGatewayProvider.gateway().resolveGunId(gun).filter(expectedGunId::equals).isPresent();
    }

}
