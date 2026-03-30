package com.cdp.codpattern.app.tdm.service;

import com.cdp.codpattern.compat.fpsmatch.FpsMatchGatewayProvider;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RoomFoodLockService {
    private static final int LOCKED_FOOD_LEVEL = 20;
    private static final Set<UUID> ALLOWED_HEAL_PLAYERS = ConcurrentHashMap.newKeySet();

    private RoomFoodLockService() {
    }

    public static boolean isRoomPlayer(ServerPlayer player) {
        return player != null && FpsMatchGatewayProvider.gateway().findPlayerRoomReadPort(player).isPresent();
    }

    public static void enforce(ServerPlayer player) {
        if (player == null) {
            return;
        }
        player.getFoodData().setFoodLevel(LOCKED_FOOD_LEVEL);
        player.getFoodData().setSaturation(0.0f);
        player.getFoodData().setExhaustion(0.0f);
    }

    public static void allowCustomHeal(ServerPlayer player, Runnable healAction) {
        if (player == null || healAction == null) {
            return;
        }
        UUID playerId = player.getUUID();
        ALLOWED_HEAL_PLAYERS.add(playerId);
        try {
            healAction.run();
        } finally {
            ALLOWED_HEAL_PLAYERS.remove(playerId);
        }
    }

    public static boolean shouldBlockHeal(ServerPlayer player) {
        return isRoomPlayer(player) && !ALLOWED_HEAL_PLAYERS.contains(player.getUUID());
    }
}
