package com.cdp.codpattern.compat.fpsmatch.map;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RoomRespawnStateRegistry {
    private static final Map<UUID, RespawnState> SNAPSHOTS = new ConcurrentHashMap<>();

    private RoomRespawnStateRegistry() {
    }

    public static void captureIfAbsent(ServerPlayer player) {
        if (player == null) {
            return;
        }
        SNAPSHOTS.computeIfAbsent(player.getUUID(), ignored -> new RespawnState(
                player.getRespawnDimension(),
                player.getRespawnPosition(),
                player.getRespawnAngle(),
                player.isRespawnForced()));
    }

    public static void restore(ServerPlayer player) {
        if (player == null) {
            return;
        }
        RespawnState snapshot = SNAPSHOTS.remove(player.getUUID());
        if (snapshot == null) {
            return;
        }
        if (snapshot.position() == null) {
            player.setRespawnPosition(Level.OVERWORLD, null, 0.0f, false, false);
            return;
        }
        player.setRespawnPosition(
                snapshot.dimension(),
                snapshot.position(),
                snapshot.angle(),
                snapshot.forced(),
                false);
    }

    private record RespawnState(
            ResourceKey<Level> dimension,
            BlockPos position,
            float angle,
            boolean forced
    ) {
    }
}
