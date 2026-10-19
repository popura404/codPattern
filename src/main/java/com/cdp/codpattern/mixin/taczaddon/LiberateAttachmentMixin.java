package com.cdp.codpattern.mixin.taczaddon;

import com.cdp.codpattern.core.refit.AttachmentEditSessionManager;
import com.cdp.codpattern.core.refit.BackpackRefitSessionContext;
import com.cdp.codpattern.network.handler.ClientPacketBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.mafuyu404.taczaddon.common.LiberateAttachment", remap = false)
public abstract class LiberateAttachmentMixin {
    @Inject(method = "useVirtualInventory", at = @At("HEAD"), cancellable = true, require = 1)
    private static void codpattern$disableAddonAttachmentSupplement(
            Inventory sourceInventory,
            CallbackInfoReturnable<Inventory> cir) {
        Inventory resolvedInventory = sourceInventory;
        if (sourceInventory != null && BackpackRefitSessionContext.isBackpackRefitActive(sourceInventory.player)) {
            if (sourceInventory.player instanceof ServerPlayer player) {
                Inventory refitInventory = AttachmentEditSessionManager.getRefitInventory(player);
                if (refitInventory != null) {
                    resolvedInventory = refitInventory;
                }
            } else if (sourceInventory.player.level().isClientSide) {
                resolvedInventory = ClientPacketBridge.resolveAttachmentRefitInventory(sourceInventory);
            }
        }
        // Only suppress Addon's generated attachment inventory; other virtual inventories remain untouched.
        cir.setReturnValue(resolvedInventory);
    }
}
