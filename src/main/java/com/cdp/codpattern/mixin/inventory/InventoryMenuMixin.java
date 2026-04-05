package com.cdp.codpattern.mixin.inventory;

import com.cdp.codpattern.core.throwable.ThrowableInventoryCapability;
import com.cdp.codpattern.core.throwable.ThrowableInventoryService;
import com.cdp.codpattern.core.throwable.ThrowableInventorySlot;
import com.cdp.codpattern.core.throwable.ThrowableItemHelper;
import com.cdp.codpattern.mixin.accessor.AbstractContainerMenuAccessor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin {
    private static final int THROWABLE_SLOT_X_START = 178;
    private static final int THROWABLE_SLOT_Y = 142;
    private static final int THROWABLE_SLOT_GAP = 18;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void codpattern$addThrowableSlots(Inventory inventory, boolean active, Player player, CallbackInfo ci) {
        AbstractContainerMenuAccessor accessor = (AbstractContainerMenuAccessor) this;
        ThrowableInventoryCapability.get(player).ifPresent(state -> {
            for (int i = 0; i < ThrowableInventoryService.SLOT_COUNT; i++) {
                accessor.codpattern$invokeAddSlot(new ThrowableInventorySlot(
                        state.getContainer(),
                        i,
                        THROWABLE_SLOT_X_START + (i * THROWABLE_SLOT_GAP),
                        THROWABLE_SLOT_Y));
            }
        });
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void codpattern$quickMoveThrowableSlots(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        AbstractContainerMenuAccessor accessor = (AbstractContainerMenuAccessor) this;
        if (index < 0 || index >= accessor.codpattern$getSlots().size()) {
            return;
        }

        Slot slot = accessor.codpattern$getSlots().get(index);
        if (slot == null || !slot.hasItem()) {
            return;
        }

        ItemStack slotStack = slot.getItem();
        ItemStack original = slotStack.copy();
        int dedicatedStart = ThrowableInventoryService.MENU_SLOT_START;
        int dedicatedEnd = dedicatedStart + ThrowableInventoryService.SLOT_COUNT;

        if (index >= dedicatedStart && index < dedicatedEnd) {
            if (!accessor.codpattern$invokeMoveItemStackTo(slotStack, InventoryMenu.INV_SLOT_START,
                    InventoryMenu.USE_ROW_SLOT_END, false)) {
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
            if (slotStack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            slot.onTake(player, slotStack);
            cir.setReturnValue(original);
            return;
        }

        if (index >= InventoryMenu.INV_SLOT_START
                && index < InventoryMenu.USE_ROW_SLOT_END
                && ThrowableItemHelper.isThrowableStack(slotStack)) {
            if (!accessor.codpattern$invokeMoveItemStackTo(slotStack, dedicatedStart, dedicatedEnd, false)) {
                return;
            }
            if (slotStack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            cir.setReturnValue(original);
        }
    }
}
