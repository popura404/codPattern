package com.cdp.codpattern.core.throwable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.AutoRegisterCapability;

@AutoRegisterCapability
public class ThrowableInventoryState {
    public static final int SLOT_COUNT = 2;
    public static final int ACTIVE_SLOT_NONE = -1;

    private final ThrowableSlotContainer container = new ThrowableSlotContainer(SLOT_COUNT, this::markDirty);
    private int activeSlot = ACTIVE_SLOT_NONE;
    private boolean dirty;

    public ThrowableSlotContainer getContainer() {
        return container;
    }

    public int getActiveSlot() {
        return activeSlot;
    }

    public void setActiveSlot(int activeSlot) {
        if (this.activeSlot == activeSlot) {
            return;
        }
        this.activeSlot = activeSlot;
        markDirty();
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }

    public void markDirty() {
        dirty = true;
    }

    public void applySync(ItemStack[] stacks, int syncedActiveSlot) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack stack = i < stacks.length ? stacks[i].copy() : ItemStack.EMPTY;
            container.replaceItem(i, stack);
        }
        activeSlot = syncedActiveSlot;
        dirty = false;
    }

    public ItemStack[] copyStacks() {
        ItemStack[] stacks = new ItemStack[SLOT_COUNT];
        for (int i = 0; i < SLOT_COUNT; i++) {
            stacks[i] = container.getItem(i).copy();
        }
        return stacks;
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                tag.put("slot_" + i, stack.save(new CompoundTag()));
            }
        }
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            String key = "slot_" + i;
            ItemStack stack = tag.contains(key) ? ItemStack.of(tag.getCompound(key)) : ItemStack.EMPTY;
            container.replaceItem(i, stack);
        }
        activeSlot = ACTIVE_SLOT_NONE;
        dirty = false;
    }
}
