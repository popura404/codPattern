package com.cdp.codpattern.app.match.editor;

import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;

public record ModeAreaData(
        String layerKey,
        ResourceKey<Level> dimension,
        AreaData area,
        String scopeKey,
        CompoundTag payload
) {
    public ModeAreaData {
        Objects.requireNonNull(layerKey, "layerKey");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(area, "area");
        scopeKey = scopeKey == null ? "" : scopeKey.trim();
        payload = payload == null ? new CompoundTag() : payload.copy();
    }

    @Override
    public CompoundTag payload() {
        return payload.copy();
    }
}
