package com.cdp.codpattern.compat.tacz;

import net.minecraft.world.item.ItemStack;

public interface TaczGateway {
    boolean isGun(ItemStack stack);

    void configureGunAmmo(ItemStack stack, int ammoMultiple);
}
