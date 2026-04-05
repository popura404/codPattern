package com.cdp.codpattern.core.throwable;

import net.minecraft.world.item.ItemStack;

public final class ThrowableUseSession {
    private static final int STARTUP_GRACE_TICKS = 5;

    private final int dedicatedSlotIndex;
    private final int hotbarSlotIndex;
    private final ItemStack originalHotbarStack;
    private int startupGraceTicks = STARTUP_GRACE_TICKS;
    private boolean usingStarted;

    public ThrowableUseSession(int dedicatedSlotIndex, int hotbarSlotIndex, ItemStack originalHotbarStack) {
        this.dedicatedSlotIndex = dedicatedSlotIndex;
        this.hotbarSlotIndex = hotbarSlotIndex;
        this.originalHotbarStack = originalHotbarStack.copy();
    }

    public int dedicatedSlotIndex() {
        return dedicatedSlotIndex;
    }

    public int hotbarSlotIndex() {
        return hotbarSlotIndex;
    }

    public ItemStack originalHotbarStack() {
        return originalHotbarStack.copy();
    }

    public void markUsingStarted() {
        usingStarted = true;
        startupGraceTicks = 0;
    }

    public boolean usingStarted() {
        return usingStarted;
    }

    public boolean tickStartupGrace() {
        if (usingStarted || startupGraceTicks <= 0) {
            return false;
        }
        startupGraceTicks--;
        return startupGraceTicks <= 0;
    }
}
