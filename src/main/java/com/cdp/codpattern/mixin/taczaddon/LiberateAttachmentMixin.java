package com.cdp.codpattern.mixin.taczaddon;

import com.cdp.codpattern.app.backpack.service.BackpackAttachmentFilter;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterClientCache;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfig;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfigRepository;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.mafuyu404.taczaddon.common.LiberateAttachment", remap = false)
public abstract class LiberateAttachmentMixin {
    @Inject(method = "useVirtualInventory", at = @At("RETURN"), cancellable = true, require = 0)
    private static void codpattern$stripBlockedAttachmentsFromVirtualInventory(
            Inventory sourceInventory,
            CallbackInfoReturnable<Inventory> cir) {
        Inventory resolvedInventory = cir.getReturnValue();
        if (resolvedInventory == null || resolvedInventory == sourceInventory) {
            return;
        }

        WeaponFilterConfig filterConfig = WeaponFilterConfigRepository.getConfig();
        if (filterConfig == null) {
            filterConfig = WeaponFilterClientCache.get();
        }
        if (filterConfig == null) {
            return;
        }

        for (int slot = 0; slot < resolvedInventory.getContainerSize(); slot++) {
            ItemStack stack = resolvedInventory.getItem(slot);
            if (stack.isEmpty() || !BackpackAttachmentFilter.isAttachmentBlocked(filterConfig, stack)) {
                continue;
            }
            resolvedInventory.setItem(slot, ItemStack.EMPTY);
        }

        cir.setReturnValue(resolvedInventory);
    }
}
