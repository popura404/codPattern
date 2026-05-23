package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.zombies.model.ZombiesBuffState;
import com.cdp.codpattern.app.zombies.model.ZombiesBuffType;
import com.cdp.codpattern.app.zombies.model.ZombiesPlayerRuntimeState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Objects;
import java.util.UUID;

public final class ZombiesBuffRuntimeEffectService {
    static final UUID SPEED_BOOST_MODIFIER_ID = UUID.fromString("d45893da-8942-47a8-9dc1-9e0d38fa0e64");
    private static final String SPEED_BOOST_MODIFIER_NAME = "codpattern.zombies.speed_boost";

    private final ZombiesPlayerStateService playerStateService;

    public ZombiesBuffRuntimeEffectService(ZombiesPlayerStateService playerStateService) {
        this.playerStateService = Objects.requireNonNull(playerStateService, "playerStateService");
    }

    public void syncPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        syncSpeedBoost(player, speedMultiplier(player.getUUID()));
    }

    public void clearPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.removeModifier(SPEED_BOOST_MODIFIER_ID);
        }
    }

    private void syncSpeedBoost(ServerPlayer player, double multiplier) {
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null) {
            return;
        }
        movementSpeed.removeModifier(SPEED_BOOST_MODIFIER_ID);
        if (!Double.isFinite(multiplier) || multiplier <= 1.0D) {
            return;
        }
        movementSpeed.addTransientModifier(new AttributeModifier(
                SPEED_BOOST_MODIFIER_ID,
                SPEED_BOOST_MODIFIER_NAME,
                multiplier - 1.0D,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    private double speedMultiplier(UUID playerId) {
        return playerStateService.get(playerId)
                .filter(ZombiesPlayerRuntimeState::canInteract)
                .flatMap(state -> state.buff(ZombiesBuffType.SPEED_BOOST))
                .map(ZombiesBuffState::multiplier)
                .filter(multiplier -> Double.isFinite(multiplier) && multiplier > 1.0D)
                .orElse(1.0D);
    }
}
