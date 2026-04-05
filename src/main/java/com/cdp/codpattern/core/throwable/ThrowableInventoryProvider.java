package com.cdp.codpattern.core.throwable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ThrowableInventoryProvider implements ICapabilitySerializable<CompoundTag> {
    private final ThrowableInventoryState state = new ThrowableInventoryState();
    private final LazyOptional<ThrowableInventoryState> optional = LazyOptional.of(() -> state);

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull net.minecraftforge.common.capabilities.Capability<T> cap,
            @Nullable Direction side) {
        return ThrowableInventoryCapability.CAPABILITY.orEmpty(cap, optional);
    }

    @Override
    public CompoundTag serializeNBT() {
        return state.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        state.deserializeNBT(nbt);
    }
}
