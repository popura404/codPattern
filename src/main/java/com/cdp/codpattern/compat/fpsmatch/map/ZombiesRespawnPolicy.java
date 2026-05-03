package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.model.PlayerRespawnContext;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.port.ModeRespawnPolicyPort;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

public class ZombiesRespawnPolicy implements ModeRespawnPolicyPort {
    private final RoomId roomId;
    private final String modeDisplayNameKey;
    private final RespawnHook respawnHook;

    public ZombiesRespawnPolicy(RoomId roomId, String modeDisplayNameKey, RespawnHook respawnHook) {
        this.roomId = Objects.requireNonNull(roomId, "roomId");
        this.modeDisplayNameKey = modeDisplayNameKey == null || modeDisplayNameKey.isBlank()
                ? GameModeRegistry.getOrDefault(roomId.gameType()).displayNameKey()
                : modeDisplayNameKey;
        this.respawnHook = respawnHook == null ? (player, context) -> { } : respawnHook;
    }

    @Override
    public RoomId roomId() {
        return roomId;
    }

    @Override
    public String gameType() {
        return roomId.gameType();
    }

    @Override
    public String mapName() {
        return roomId.mapName();
    }

    @Override
    public String modeDisplayNameKey() {
        return modeDisplayNameKey;
    }

    @Override
    public void onPlayerRespawn(ServerPlayer player, PlayerRespawnContext context) {
        respawnHook.onPlayerRespawn(player, context);
    }

    @Override
    public boolean shouldDistributeBackpackOnRespawn(ServerPlayer player, PlayerRespawnContext context) {
        return false;
    }

    @Override
    public boolean shouldUseMatchEndTeleport(ServerPlayer player, PlayerRespawnContext context) {
        return false;
    }

    @FunctionalInterface
    public interface RespawnHook {
        void onPlayerRespawn(ServerPlayer player, PlayerRespawnContext context);
    }
}
