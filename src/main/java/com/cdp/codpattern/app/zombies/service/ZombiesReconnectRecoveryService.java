package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.RoomId;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * Applies reconnect-time zombies cleanup before the player is returned to normal
 * room flow.
 */
public final class ZombiesReconnectRecoveryService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ZombiesReconnectRecoveryService INSTANCE = new ZombiesReconnectRecoveryService(
            ZombiesPostGameTeleportService.instance(),
            ZombiesPlayerRuntimeMarkerService.instance());

    private final ZombiesPostGameTeleportService postGameTeleportService;
    private final ZombiesPlayerRuntimeMarkerService markerService;

    public ZombiesReconnectRecoveryService(
            ZombiesPostGameTeleportService postGameTeleportService,
            ZombiesPlayerRuntimeMarkerService markerService
    ) {
        this.postGameTeleportService = postGameTeleportService == null
                ? ZombiesPostGameTeleportService.instance()
                : postGameTeleportService;
        this.markerService = markerService == null
                ? ZombiesPlayerRuntimeMarkerService.instance()
                : markerService;
    }

    public static ZombiesReconnectRecoveryService instance() {
        return INSTANCE;
    }

    public LoginRecoveryResult recoverPlayer(ServerPlayer player, RecoveryResolver resolver) {
        if (player == null || resolver == null) {
            return LoginRecoveryResult.none();
        }

        Optional<ZombiesPostGameTeleportService.PendingEndTeleport> pending =
                postGameTeleportService.consumePending(player.getUUID());
        if (pending.isPresent()) {
            ZombiesPostGameTeleportService.PendingEndTeleport value = pending.get();
            resolver.clearZombiesTemporaryState(player, value.roomId(), true);
            markerService.clearMarker(player);
            TeleportAttempt attempt = teleportToEndOrFallback(
                    player,
                    value.endTeleport().or(() -> resolver.endTeleport(value.roomId())),
                    value.roomId(),
                    "pending_endtp");
            return new LoginRecoveryResult(
                    RecoveryOutcome.PENDING_ENDTP,
                    Optional.of(value.roomId()),
                    attempt.teleported(),
                    attempt.usedFallback());
        }

        Optional<ZombiesPlayerRuntimeMarkerService.PlayerMarker> marker = markerService.readMarker(player);
        if (marker.isPresent() && marker.get().isPendingEndTeleport()) {
            ZombiesPlayerRuntimeMarkerService.PlayerMarker value = marker.get();
            resolver.clearZombiesTemporaryState(player, value.roomId(), true);
            markerService.clearMarker(player);
            TeleportAttempt attempt = teleportToEndOrFallback(
                    player,
                    value.endTeleport().or(() -> resolver.endTeleport(value.roomId())),
                    value.roomId(),
                    "persistent_pending_endtp");
            return new LoginRecoveryResult(
                    RecoveryOutcome.PERSISTENT_PENDING_ENDTP,
                    Optional.of(value.roomId()),
                    attempt.teleported(),
                    attempt.usedFallback());
        }

        if (marker.isPresent() && marker.get().isActiveRound()) {
            ZombiesPlayerRuntimeMarkerService.PlayerMarker value = marker.get();
            if (!resolver.isRoomActive(value.roomId())) {
                resolver.clearZombiesTemporaryState(player, value.roomId(), true);
                markerService.clearMarker(player);
                TeleportAttempt attempt = teleportToEndOrFallback(
                        player,
                        value.endTeleport().or(() -> resolver.endTeleport(value.roomId())),
                        value.roomId(),
                        "stale_active_marker");
                return new LoginRecoveryResult(
                        RecoveryOutcome.STALE_ACTIVE_MARKER,
                        Optional.of(value.roomId()),
                        attempt.teleported(),
                        attempt.usedFallback());
            }
            if (resolver.restoreActiveRoomPlayer(player, value.roomId())) {
                return new LoginRecoveryResult(
                        RecoveryOutcome.ACTIVE_ROUND_REJOIN,
                        Optional.of(value.roomId()),
                        false,
                        false);
            }
            return LoginRecoveryResult.none();
        }

        Optional<RoomId> inactiveRoomAtPlayer = resolver.inactiveZombiesRoomContaining(player);
        if (inactiveRoomAtPlayer.isEmpty()) {
            return LoginRecoveryResult.none();
        }
        RoomId roomId = inactiveRoomAtPlayer.get();
        resolver.clearZombiesTemporaryState(player, roomId, false);
        markerService.clearMarker(player);
        TeleportAttempt attempt = teleportToEndOrFallback(
                player,
                resolver.endTeleport(roomId),
                roomId,
                "inactive_map_position");
        return new LoginRecoveryResult(
                RecoveryOutcome.INACTIVE_MAP_POSITION,
                Optional.of(roomId),
                attempt.teleported(),
                attempt.usedFallback());
    }

    private TeleportAttempt teleportToEndOrFallback(
            ServerPlayer player,
            Optional<ZombiesPostGameTeleportService.TeleportTarget> target,
            RoomId roomId,
            String reason
    ) {
        if (target != null && target.isPresent() && markerService.teleportToTarget(player, target.get())) {
            return new TeleportAttempt(true, false);
        }
        boolean fallbackTeleported = markerService.teleportToServerFallback(player);
        LOGGER.warn(
                "Zombies reconnect recovery used fallback teleport for player {} room {} reason {}",
                player == null ? "<null>" : player.getStringUUID(),
                roomId == null ? "<unknown>" : roomId.encode(),
                reason);
        return new TeleportAttempt(fallbackTeleported, true);
    }

    public interface RecoveryResolver {
        boolean isRoomActive(RoomId roomId);

        Optional<ZombiesPostGameTeleportService.TeleportTarget> endTeleport(RoomId roomId);

        Optional<RoomId> inactiveZombiesRoomContaining(ServerPlayer player);

        void clearZombiesTemporaryState(ServerPlayer player, RoomId roomId, boolean clearInventory);

        default boolean restoreActiveRoomPlayer(ServerPlayer player, RoomId roomId) {
            return false;
        }
    }

    public enum RecoveryOutcome {
        NONE,
        PENDING_ENDTP,
        PERSISTENT_PENDING_ENDTP,
        ACTIVE_ROUND_REJOIN,
        STALE_ACTIVE_MARKER,
        INACTIVE_MAP_POSITION
    }

    public record LoginRecoveryResult(
            RecoveryOutcome outcome,
            Optional<RoomId> roomId,
            boolean teleported,
            boolean usedFallback
    ) {
        public LoginRecoveryResult {
            outcome = outcome == null ? RecoveryOutcome.NONE : outcome;
            roomId = roomId == null ? Optional.empty() : roomId;
        }

        public static LoginRecoveryResult none() {
            return new LoginRecoveryResult(RecoveryOutcome.NONE, Optional.empty(), false, false);
        }

        public boolean recovered() {
            return outcome != RecoveryOutcome.NONE;
        }
    }

    private record TeleportAttempt(boolean teleported, boolean usedFallback) {
    }
}
