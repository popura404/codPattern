package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ZombiesCrashRecoveryServiceCompatTest {
    private ZombiesCrashRecoveryServiceCompatTest() {
    }

    public static void main(String[] args) throws Exception {
        decodeRoomIdRejectsInvalidTags();
        stoppingRecoveryOnlyResetsRunningRooms();
        cleanupWiringStaysInsideZombiesBoundary();
    }

    private static void decodeRoomIdRejectsInvalidTags() {
        ZombiesCrashRecoveryService service = new ZombiesCrashRecoveryService(
                ModeEntityOwnershipRegistry.instance(),
                new ZombiesMapOccupancyService());

        require(service.decodeRoomId("").isEmpty(), "blank room tag should be ignored");
        require(service.decodeRoomId("not-a-room").isEmpty(), "malformed room tag should be ignored");
        RoomId decoded = service.decodeRoomId(BuiltInGameModes.ZOMBIES + "|map-a").orElseThrow();
        require(BuiltInGameModes.ZOMBIES.equals(decoded.gameType()), "decoded room should retain game type");
        require("map-a".equals(decoded.mapName()), "decoded room should retain map name");
    }

    private static void stoppingRecoveryOnlyResetsRunningRooms() {
        ZombiesMapOccupancyService occupancy = new ZombiesMapOccupancyService();
        RoomId runningRoomId = RoomId.of(BuiltInGameModes.ZOMBIES, "running");
        RoomId waitingRoomId = RoomId.of(BuiltInGameModes.ZOMBIES, "waiting");
        occupancy.acquire(runningRoomId);
        occupancy.acquire(waitingRoomId);
        TestShutdownRoom runningRoom = new TestShutdownRoom(runningRoomId, true);
        TestShutdownRoom waitingRoom = new TestShutdownRoom(waitingRoomId, false);
        ZombiesCrashRecoveryService service = new ZombiesCrashRecoveryService(
                ModeEntityOwnershipRegistry.instance(),
                occupancy);

        ZombiesCrashRecoveryService.ServerStoppingRecoverySummary summary =
                service.cleanupServerStopping(null, List.of(runningRoom, waitingRoom));

        require(summary.roomsReset() == 1, "only running rooms should be reset before stop");
        require(runningRoom.cleaned, "running room should be cleaned");
        require(!waitingRoom.cleaned, "waiting room should not be reset");
        require(!occupancy.isOccupied(runningRoomId), "stopping cleanup should clear running occupancy");
        require(!occupancy.isOccupied(waitingRoomId), "stopping cleanup should clear waiting occupancy");
    }

    private static void cleanupWiringStaysInsideZombiesBoundary() throws Exception {
        String recoveryService = Files.readString(Path.of(
                "src/main/java/com/cdp/codpattern/app/zombies/service/ZombiesCrashRecoveryService.java"));
        String zombiesMap = Files.readString(Path.of(
                "src/main/java/com/cdp/codpattern/compat/fpsmatch/map/ZombiesMap.java"));
        String lifecycleHandler = Files.readString(Path.of(
                "src/main/java/com/cdp/codpattern/event/ZombiesServerLifecycleEventHandler.java"));

        require(recoveryService.contains("cleanupResidualTaggedEntitiesForRoom("),
                "zombies recovery should expose a room-scoped NPC cleanup entry point");
        require(recoveryService.contains("!BuiltInGameModes.isZombies(decodedRoom.gameType())"),
                "cleanup filtering must reject non-zombies ownership tags");
        require(recoveryService.contains("targetRoom == null || sameRoom(decodedRoom, targetRoom)"),
                "room cleanup must match only the requested zombies map");
        require(zombiesMap.contains("cleanupResidualTaggedEntitiesForRoom("),
                "zombies map startup should clear residual NPCs for its own room");
        require(lifecycleHandler.contains("recoverServerStartup(event.getServer())"),
                "server startup should invoke zombies-only residual NPC cleanup");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class TestShutdownRoom implements ZombiesCrashRecoveryService.ShutdownRoom {
        private final RoomId roomId;
        private final boolean running;
        private boolean cleaned;

        private TestShutdownRoom(RoomId roomId, boolean running) {
            this.roomId = roomId;
            this.running = running;
        }

        @Override
        public RoomId roomId() {
            return roomId;
        }

        @Override
        public boolean running() {
            return running;
        }

        @Override
        public void cleanupForServerStopping() {
            cleaned = true;
        }
    }
}
