package com.cdp.codpattern.mixin.refit;

import com.cdp.codpattern.core.refit.RefitPreviewContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ItemHandlerHelper.class, remap = false)
public abstract class RefitItemReturnMixin {
    @Inject(method = "giveItemToPlayer(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;I)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private static void codpattern$absorbPreviewAmmo(Player player, ItemStack stack, int preferredSlot,
                                                    CallbackInfo ci) {
        if (RefitPreviewContext.isActiveFor(player)) {
            // Leave the caller's ammunition state changes intact, but never create inventory items or drops.
            ci.cancel();
        }
    }
}
