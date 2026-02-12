package com.cdp.codpattern.compat.tacz;

import net.minecraft.world.item.ItemStack;

public final class NoopTaczGateway implements TaczGateway {
    @Override
    public boolean isGun(ItemStack stack) {
        return false;
    }

    @Override
    public void configureGunAmmo(ItemStack stack, int ammoMultiple) {
    }
}
