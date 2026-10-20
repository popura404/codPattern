package com.cdp.codpattern.core.refit;

import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
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
    private final String configuredGunId;
    private String expectedGunId;

    public AttachmentEditSession(int bagId, String slot, int editHotbarSlot, int originalSelectedSlot,
            List<ItemStack> inventorySnapshot, AttachmentRefitInventory refitInventory) {
        this(UUID.randomUUID(), bagId, slot, editHotbarSlot, originalSelectedSlot, inventorySnapshot, refitInventory);
    }

    public AttachmentEditSession(UUID sessionId, int bagId, String slot, int editHotbarSlot, int originalSelectedSlot,
            List<ItemStack> inventorySnapshot, AttachmentRefitInventory refitInventory) {
        this(sessionId, bagId, slot, editHotbarSlot, originalSelectedSlot, inventorySnapshot, refitInventory,
                TaczGatewayProvider.gateway().resolveRefitGunId(refitInventory.getSelected()).orElse(""));
    }

    public AttachmentEditSession(UUID sessionId, int bagId, String slot, int editHotbarSlot, int originalSelectedSlot,
            List<ItemStack> inventorySnapshot, AttachmentRefitInventory refitInventory, String configuredGunId) {
        this.sessionId = sessionId;
        this.bagId = bagId;
        this.slot = slot;
        this.editHotbarSlot = editHotbarSlot;
        this.originalSelectedSlot = originalSelectedSlot;
        this.inventorySnapshot = new ArrayList<>(inventorySnapshot);
        this.refitInventory = refitInventory;
        this.configuredGunId = configuredGunId;
        this.expectedGunId = TaczGatewayProvider.gateway().resolveRefitGunId(refitInventory.getSelected()).orElse("");
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

    public String getExpectedGunId() {
        return expectedGunId;
    }

    public String getConfiguredGunId() {
        return configuredGunId;
    }

    /** Only the server's verified form transition may advance the working-gun identity. */
    public void acceptDerivedGunIdentity(String previousGunId, String derivedGunId) {
        if (!expectedGunId.equals(previousGunId) || derivedGunId == null || derivedGunId.isEmpty()
                || TaczGatewayProvider.gateway().resolveRefitGunId(refitInventory.getSelected())
                        .filter(derivedGunId::equals).isEmpty()) {
            throw new IllegalStateException("Arcana form transition does not match the working gun");
        }
        expectedGunId = derivedGunId;
    }

    public boolean matchesGunIdentity(ItemStack gun) {
        return !expectedGunId.isEmpty()
                && TaczGatewayProvider.gateway().resolveRefitGunId(gun).filter(expectedGunId::equals).isPresent();
    }

}
