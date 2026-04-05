package com.cdp.codpattern.event.client;

import net.minecraft.world.item.ItemStack;

record ThrowableClientUsePrediction(int dedicatedSlotIndex, int hotbarSlotIndex, ItemStack originalHotbarStack) {
    ThrowableClientUsePrediction {
        originalHotbarStack = originalHotbarStack.copy();
    }
}
