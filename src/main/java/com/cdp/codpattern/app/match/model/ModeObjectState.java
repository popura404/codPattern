package com.cdp.codpattern.app.match.model;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.Objects;

public record ModeObjectState(
        String objectKey,
        String stateKey,
        BlockPos position,
        CompoundTag payload,
        long revision
) {
    public ModeObjectState {
        objectKey = Objects.requireNonNullElse(objectKey, "").trim();
        stateKey = Objects.requireNonNullElse(stateKey, "").trim();
        payload = payload == null ? new CompoundTag() : payload.copy();
        revision = Math.max(0L, revision);
    }

    @Override
    public CompoundTag payload() {
        return payload.copy();
    }
}
