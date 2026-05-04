package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.model.RoomId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ZombiesPostGameTeleportServiceCompatTest {
    private ZombiesPostGameTeleportServiceCompatTest() {
    }

    public static void main(String[] args) {
        cleanupWritesPendingOnlyForOfflineMembers();
        consumePendingIsOneShot();
        laterCleanupClearsOnlinePending();
    }

    private static void cleanupWritesPendingOnlyForOfflineMembers() {
        ZombiesPostGameTeleportService service = new ZombiesPostGameTeleportService();
        RoomId roomId = RoomId.of(BuiltInGameModes.ZOMBIES, "pending");
        UUID online = playerId(1);
        UUID offline = playerId(2);
        ZombiesPostGameTeleportService.TeleportTarget endtp =
                new ZombiesPostGameTeleportService.TeleportTarget("minecraft:overworld", 1, 64, 2, 90.0F, 0.0F);

        ZombiesPostGameTeleportService.CleanupPendingSummary summary = service.recordPostGameCleanup(
                roomId,
                List.of(online, offline),
                List.of(online),
                Optional.of(endtp),
                "ENDING",
                7L);

        require(summary.memberCount() == 2, "cleanup summary should count members");
        require(summary.onlineCount() == 1, "cleanup summary should count online members");
        require(summary.pendingWritten() == 1, "cleanup should write one offline pending record");
        require(service.peekPending(online).isEmpty(), "online member should not receive pending endtp");
        ZombiesPostGameTeleportService.PendingEndTeleport pending = service.peekPending(offline).orElseThrow();
        require(roomId.equals(pending.roomId()), "pending room should match cleanup room");
        require(pending.endTeleport().orElseThrow().equals(endtp), "pending endtp should match cleanup target");
        require(pending.cleanupRevision() == 7L, "pending should retain cleanup revision");
    }

    private static void consumePendingIsOneShot() {
        ZombiesPostGameTeleportService service = new ZombiesPostGameTeleportService();
        RoomId roomId = RoomId.of(BuiltInGameModes.ZOMBIES, "consume");
        UUID offline = playerId(3);

        service.recordPostGameCleanup(roomId, List.of(offline), List.of(), Optional.empty(), "reset", 1L);

        require(service.consumePending(offline).isPresent(), "first consume should return pending record");
        require(service.consumePending(offline).isEmpty(), "second consume should be empty");
        require(service.pendingCount() == 0, "pending map should be empty after consume");
    }

    private static void laterCleanupClearsOnlinePending() {
        ZombiesPostGameTeleportService service = new ZombiesPostGameTeleportService();
        RoomId roomId = RoomId.of(BuiltInGameModes.ZOMBIES, "clear-online");
        UUID player = playerId(4);

        service.recordPostGameCleanup(roomId, List.of(player), List.of(), Optional.empty(), "ENDING", 1L);
        ZombiesPostGameTeleportService.CleanupPendingSummary summary = service.recordPostGameCleanup(
                roomId,
                List.of(player),
                List.of(player),
                Optional.empty(),
                "ENDING",
                2L);

        require(summary.pendingCleared() == 1, "online cleanup should clear stale pending record");
        require(service.peekPending(player).isEmpty(), "stale pending should be removed");
    }

    private static UUID playerId(int suffix) {
        return new UUID(0L, suffix);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
