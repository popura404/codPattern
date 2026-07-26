package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.runtime.player.DeferredPlayerActionRegistry;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Stores post-game end teleport work for members that were offline when a zombies
 * round settled. The in-memory record covers normal reconnects in the same server
 * runtime; persistent player markers cover crash/restart recovery.
 */
public final class ZombiesPostGameTeleportService {
    private static final ZombiesPostGameTeleportService INSTANCE = new ZombiesPostGameTeleportService();

    private final DeferredPlayerActionRegistry<PendingEndTeleport> pendingByPlayer =
            new DeferredPlayerActionRegistry<>();

    public static ZombiesPostGameTeleportService instance() {
        return INSTANCE;
    }

    public CleanupPendingSummary recordPostGameCleanup(
            RoomId roomId,
            Collection<UUID> memberIds,
            Collection<UUID> onlinePlayerIds,
            Optional<TeleportTarget> endTeleport,
            String reason,
            long cleanupRevision
    ) {
        Objects.requireNonNull(roomId, "roomId");
        Set<UUID> onlineIds = new HashSet<>(onlinePlayerIds == null ? List.of() : onlinePlayerIds);
        Optional<TeleportTarget> safeTarget = endTeleport == null ? Optional.empty() : endTeleport;
        int memberCount = 0;
        int pendingWritten = 0;
        int pendingCleared = 0;

        if (memberIds != null) {
            for (UUID playerId : memberIds) {
                if (playerId == null) {
                    continue;
                }
                memberCount++;
                if (onlineIds.contains(playerId)) {
                    if (pendingByPlayer.remove(playerId)) {
                        pendingCleared++;
                    }
                    continue;
                }
                pendingByPlayer.put(playerId, new PendingEndTeleport(
                        playerId,
                        roomId,
                        safeTarget,
                        reason,
                        cleanupRevision,
                        System.currentTimeMillis()));
                pendingWritten++;
            }
        }

        return new CleanupPendingSummary(
                roomId,
                memberCount,
                onlineIds.size(),
                pendingWritten,
                pendingCleared);
    }

    public Optional<PendingEndTeleport> peekPending(UUID playerId) {
        return pendingByPlayer.peek(playerId);
    }

    public Optional<PendingEndTeleport> consumePending(UUID playerId) {
        return pendingByPlayer.consume(playerId);
    }

    public void clearPending(UUID playerId) {
        if (playerId != null) {
            pendingByPlayer.remove(playerId);
        }
    }

    public int pendingCount() {
        return pendingByPlayer.size();
    }

    public void clear() {
        pendingByPlayer.clear();
    }

    public record TeleportTarget(
            String dimensionId,
            int x,
            int y,
            int z,
            float yaw,
            float pitch
    ) {
        public TeleportTarget {
            dimensionId = Objects.requireNonNullElse(dimensionId, "").trim();
        }

        public boolean hasDimension() {
            return !dimensionId.isBlank();
        }
    }

    public record PendingEndTeleport(
            UUID playerId,
            RoomId roomId,
            Optional<TeleportTarget> endTeleport,
            String reason,
            long cleanupRevision,
            long writtenAtEpochMillis
    ) {
        public PendingEndTeleport {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(roomId, "roomId");
            endTeleport = endTeleport == null ? Optional.empty() : endTeleport;
            reason = Objects.requireNonNullElse(reason, "").trim();
            cleanupRevision = Math.max(0L, cleanupRevision);
            writtenAtEpochMillis = Math.max(0L, writtenAtEpochMillis);
        }
    }

    public record CleanupPendingSummary(
            RoomId roomId,
            int memberCount,
            int onlineCount,
            int pendingWritten,
            int pendingCleared
    ) {
        public CleanupPendingSummary {
            Objects.requireNonNull(roomId, "roomId");
            memberCount = Math.max(0, memberCount);
            onlineCount = Math.max(0, onlineCount);
            pendingWritten = Math.max(0, pendingWritten);
            pendingCleared = Math.max(0, pendingCleared);
        }
    }
}
