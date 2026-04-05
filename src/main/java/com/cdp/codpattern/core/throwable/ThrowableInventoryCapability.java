package com.cdp.codpattern.core.throwable;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

import java.util.Optional;

public final class ThrowableInventoryCapability {
    public static final Capability<ThrowableInventoryState> CAPABILITY =
            CapabilityManager.get(new CapabilityToken<>() {
            });

    private ThrowableInventoryCapability() {
    }

    public static Optional<ThrowableInventoryState> get(Player player) {
        if (player == null) {
            return Optional.empty();
        }
        return player.getCapability(CAPABILITY).resolve();
    }
}
