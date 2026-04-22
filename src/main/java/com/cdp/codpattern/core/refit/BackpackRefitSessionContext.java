package com.cdp.codpattern.core.refit;

import net.minecraft.world.entity.player.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BackpackRefitSessionContext {
    private static final Set<UUID> SERVER_ACTIVE_PLAYERS = ConcurrentHashMap.newKeySet();
    private static volatile boolean clientActive = false;

    private BackpackRefitSessionContext() {
    }

    public static void markServerActive(UUID playerId) {
        if (playerId != null) {
            SERVER_ACTIVE_PLAYERS.add(playerId);
        }
    }

    public static void clearServerActive(UUID playerId) {
        if (playerId != null) {
            SERVER_ACTIVE_PLAYERS.remove(playerId);
        }
    }

    public static void setClientActive(boolean active) {
        clientActive = active;
    }

    public static boolean isBackpackRefitActive(Player player) {
        if (player == null) {
            return false;
        }
        if (player.level().isClientSide) {
            return clientActive;
        }
        return SERVER_ACTIVE_PLAYERS.contains(player.getUUID());
    }
}
