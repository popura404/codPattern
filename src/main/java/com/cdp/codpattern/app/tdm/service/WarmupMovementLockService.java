package com.cdp.codpattern.app.tdm.service;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

public final class WarmupMovementLockService {
    private static final UUID MOVEMENT_LOCK_ID = UUID.fromString("d64c2068-2b6b-4c7c-9c3d-18af2d12a5b9");
    private static final AttributeModifier MOVEMENT_LOCK = new AttributeModifier(
            MOVEMENT_LOCK_ID,
            "codpattern_warmup_lock",
            -1.0D,
            AttributeModifier.Operation.MULTIPLY_TOTAL);

    private WarmupMovementLockService() {
    }

    public static void lock(ServerPlayer player) {
        if (player == null) {
            return;
        }
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null || movementSpeed.hasModifier(MOVEMENT_LOCK)) {
            return;
        }
        movementSpeed.addTransientModifier(MOVEMENT_LOCK);
        player.setSprinting(false);
    }

    public static void unlock(ServerPlayer player) {
        if (player == null) {
            return;
        }
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null) {
            return;
        }
        movementSpeed.removeModifier(MOVEMENT_LOCK_ID);
    }
}
