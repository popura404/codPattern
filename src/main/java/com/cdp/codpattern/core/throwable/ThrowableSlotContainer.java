package com.cdp.codpattern.core.throwable;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

public class ThrowableSlotContainer extends SimpleContainer {
    private final Runnable onChanged;
    private boolean suppressChangeCallback;

    public ThrowableSlotContainer(int size, Runnable onChanged) {
        super(size);
        this.onChanged = onChanged;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (!suppressChangeCallback && onChanged != null) {
            onChanged.run();
        }
    }

    public void replaceItem(int slotIndex, ItemStack stack) {
        suppressChangeCallback = true;
        try {
            super.setItem(slotIndex, stack);
        } finally {
            suppressChangeCallback = false;
        }
    }
}
