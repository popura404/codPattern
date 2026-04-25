package com.cdp.codpattern.core.throwable;

import com.cdp.codpattern.compat.lrtactical.LrTacticalGatewayProvider;
import net.minecraft.world.item.ItemStack;

public final class ThrowableItemHelper {
    private ThrowableItemHelper() {
    }

    public static boolean isThrowableStack(ItemStack stack) {
        return LrTacticalGatewayProvider.gateway().isThrowableStack(stack);
    }

    public static int getPrepareTicks(ItemStack stack) {
        return LrTacticalGatewayProvider.gateway().getPrepareTicks(stack);
    }
}
