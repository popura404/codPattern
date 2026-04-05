package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.core.throwable.ThrowableInventoryService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CodTdmDeferredLeaveRegistry {
    private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();

    private CodTdmDeferredLeaveRegistry() {
    }

    public static void register(UUID playerId) {
        if (playerId == null) {
            return;
        }
        PENDING.add(playerId);
    }

    public static boolean applyIfPresent(ServerPlayer player) {
        if (player == null || !PENDING.remove(player.getUUID())) {
            return false;
        }
        player.setGameMode(GameType.ADVENTURE);
        player.getInventory().clearContent();
        ThrowableInventoryService.clearRuntime(player);
        player.containerMenu.broadcastChanges();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.removeAllEffects();
        ThrowableInventoryService.sync(player);
        return true;
    }
}
