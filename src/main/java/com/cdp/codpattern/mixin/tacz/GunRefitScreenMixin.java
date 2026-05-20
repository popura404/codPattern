package com.cdp.codpattern.mixin.tacz;

import com.cdp.codpattern.app.backpack.service.BackpackAttachmentFilter;
import com.cdp.codpattern.client.refit.AttachmentRefitClientState;
import com.cdp.codpattern.core.refit.BackpackRefitSessionContext;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterClientCache;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfig;
import com.tacz.guns.client.gui.GunRefitScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = GunRefitScreen.class, remap = false, priority = 900)
public abstract class GunRefitScreenMixin {
    @Redirect(
            method = "addInventoryAttachmentButtons",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getInventory()Lnet/minecraft/world/entity/player/Inventory;"
            )
    )
    private Inventory codpattern$useRefitInventoryForCandidates(LocalPlayer player) {
        Inventory inventory = AttachmentRefitClientState.resolveRefitScreenInventory(player);
        return inventory == null ? player.getInventory() : inventory;
    }

    @Redirect(
            method = "addAttachmentTypeButtons",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getInventory()Lnet/minecraft/world/entity/player/Inventory;"
            )
    )
    private Inventory codpattern$useRefitInventoryForSlots(LocalPlayer player) {
        Inventory inventory = AttachmentRefitClientState.resolveRefitScreenInventory(player);
        return inventory == null ? player.getInventory() : inventory;
    }

    @ModifyVariable(method = "addInventoryAttachmentButtons", at = @At("STORE"), ordinal = 0)
    private Inventory codpattern$keepRefitInventoryForCandidateVariable(Inventory inventory) {
        return codpattern$preferBackpackRefitInventory(inventory);
    }

    @ModifyVariable(method = "addAttachmentTypeButtons", at = @At("STORE"), ordinal = 0)
    private Inventory codpattern$keepRefitInventoryForSlotVariable(Inventory inventory) {
        return codpattern$preferBackpackRefitInventory(inventory);
    }

    @Redirect(
            method = "addInventoryAttachmentButtons",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Inventory;getItem(I)Lnet/minecraft/world/item/ItemStack;"
            )
    )
    private ItemStack codpattern$filterBlockedAttachmentCandidates(Inventory inventory, int slotIndex) {
        ItemStack stack = inventory.getItem(slotIndex);
        WeaponFilterConfig filterConfig = WeaponFilterClientCache.get();
        if (stack.isEmpty() || filterConfig == null) {
            return stack;
        }
        if (BackpackAttachmentFilter.isAttachmentBlocked(filterConfig, stack)) {
            return ItemStack.EMPTY;
        }
        return stack;
    }

    private static Inventory codpattern$preferBackpackRefitInventory(Inventory inventory) {
        if (inventory == null || !(inventory.player instanceof LocalPlayer player)) {
            return inventory;
        }
        if (!BackpackRefitSessionContext.isBackpackRefitActive(player)) {
            return inventory;
        }
        Inventory refitInventory = AttachmentRefitClientState.resolveRefitScreenInventory(player);
        return refitInventory == null || refitInventory == player.getInventory() ? inventory : refitInventory;
    }
}
