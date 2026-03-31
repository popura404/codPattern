package com.cdp.codpattern.compat.tacz;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

public final class NoopTaczGateway implements TaczGateway {
    @Override
    public boolean isGun(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isValidGun(ItemStack stack) {
        return false;
    }

    @Override
    public Optional<String> resolveGunType(ItemStack stack) {
        return Optional.empty();
    }

    @Override
    public Optional<String> resolveGunId(ItemStack stack) {
        return Optional.empty();
    }

    @Override
    public Optional<String> resolveAttachmentId(ItemStack stack) {
        return Optional.empty();
    }

    @Override
    public List<String> resolveInstalledAttachmentIds(ItemStack gunStack) {
        return List.of();
    }

    @Override
    public boolean canAttach(ItemStack gunStack, ItemStack attachmentStack) {
        return false;
    }

    @Override
    public CompoundTag buildAttachmentPreset(ItemStack gunStack) {
        return new CompoundTag();
    }

    @Override
    public void applyAttachmentPreset(ItemStack gunStack, CompoundTag preset) {
    }

    @Override
    public void postAttachmentChanged(Player player, ItemStack gunStack) {
    }

    @Override
    public void configureGunAmmo(ItemStack stack, int ammoMultiple) {
    }
}
