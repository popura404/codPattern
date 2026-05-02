package com.cdp.codpattern.app.match.port;

import com.cdp.codpattern.app.match.model.PlayerRespawnContext;
import net.minecraft.server.level.ServerPlayer;

public interface ModeRespawnPolicyPort extends ModeRoomIdentityPort {
    default void onPlayerRespawn(ServerPlayer player, PlayerRespawnContext context) {
    }

    default boolean shouldDistributeBackpackOnRespawn(ServerPlayer player, PlayerRespawnContext context) {
        return false;
    }

    default boolean shouldUseMatchEndTeleport(ServerPlayer player, PlayerRespawnContext context) {
        return false;
    }
}
