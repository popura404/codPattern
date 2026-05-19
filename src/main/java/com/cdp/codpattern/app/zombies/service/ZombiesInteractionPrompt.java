package com.cdp.codpattern.app.zombies.service;

import net.minecraft.core.BlockPos;

import java.util.Objects;

public record ZombiesInteractionPrompt(
        String objectType,
        String objectId,
        BlockPos pos,
        String displayKey,
        boolean interactable
) {
    public ZombiesInteractionPrompt {
        objectType = Objects.requireNonNullElse(objectType, "").trim();
        objectId = Objects.requireNonNullElse(objectId, "").trim();
        pos = pos == null ? BlockPos.ZERO : pos;
        displayKey = Objects.requireNonNullElse(displayKey, "").trim();
    }
}
