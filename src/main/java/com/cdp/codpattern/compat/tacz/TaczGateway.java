package com.cdp.codpattern.compat.tacz;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public interface TaczGateway {
    boolean isGun(ItemStack stack);

    boolean isValidGun(ItemStack stack);

    Optional<String> resolveGunType(ItemStack stack);

    Optional<String> resolveGunId(ItemStack stack);

    boolean canAttach(ItemStack gunStack, ItemStack attachmentStack);

    CompoundTag buildAttachmentPreset(ItemStack gunStack);

    void applyAttachmentPreset(ItemStack gunStack, CompoundTag preset);

    void postAttachmentChanged(Player player, ItemStack gunStack);

    void configureGunAmmo(ItemStack stack, int ammoMultiple);
}
