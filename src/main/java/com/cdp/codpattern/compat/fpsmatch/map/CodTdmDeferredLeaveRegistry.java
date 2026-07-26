package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.match.runtime.player.DeferredPlayerActionRegistry;
import com.cdp.codpattern.app.tdm.service.WarmupMovementLockService;
import com.cdp.codpattern.core.throwable.ThrowableInventoryService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.UUID;

public final class CodTdmDeferredLeaveRegistry {
    private static final DeferredPlayerActionRegistry<Boolean> PENDING = new DeferredPlayerActionRegistry<>();

    private CodTdmDeferredLeaveRegistry() {
    }

    public static void register(UUID playerId) {
        if (playerId == null) {
            return;
        }
        PENDING.put(playerId, Boolean.TRUE);
    }

    public static boolean applyIfPresent(ServerPlayer player) {
        if (player == null || PENDING.consume(player.getUUID()).isEmpty()) {
            return false;
        }
        player.setGameMode(GameType.ADVENTURE);
        WarmupMovementLockService.unlock(player);
        RoomRespawnStateRegistry.restore(player);
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
