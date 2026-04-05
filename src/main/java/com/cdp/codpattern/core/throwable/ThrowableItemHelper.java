package com.cdp.codpattern.core.throwable;

import me.xjqsh.lrtactical.api.item.IThrowable;
import net.minecraft.world.item.ItemStack;

public final class ThrowableItemHelper {
    private ThrowableItemHelper() {
    }

    public static boolean isThrowableStack(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof IThrowable throwable)) {
            return false;
        }
        return throwable.getThrowableIndex(stack).isPresent();
    }

    public static int getPrepareTicks(ItemStack stack) {
        if (!isThrowableStack(stack) || !(stack.getItem() instanceof IThrowable throwable)) {
            return 0;
        }
        return Math.max(0, throwable.getMaxUsingTick(stack));
    }
}
