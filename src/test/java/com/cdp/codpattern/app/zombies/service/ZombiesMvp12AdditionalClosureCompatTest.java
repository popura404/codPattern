package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.ModeObjectState;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;
import com.cdp.codpattern.app.zombies.map.object.ZombiesBarrierData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesWeaponWallData;
import com.cdp.codpattern.client.ClientModeObjectState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ZombiesMvp12AdditionalClosureCompatTest {
    private ZombiesMvp12AdditionalClosureCompatTest() {
    }

    public static void main(String[] args) {
        cleanupParticipantFailureStopsDependentCleanupWithoutReleasingOccupancy();
        repeatedCleanupWithoutResidueStillCompletesAndReportsNoOccupancyRelease();
        objectStoreResetDropsPreviousSnapshotRevisionAndOffer();
        objectStoreResetRevisionLetsClientAcceptNextRoundState();
    }

    private static void cleanupParticipantFailureStopsDependentCleanupWithoutReleasingOccupancy() {
        RoomId roomId = RoomId.of("zombies", "mvp12-cleanup-failure");
        ZombiesMapOccupancyService occupancy = ZombiesMapOccupancyService.instance();
        occupancy.forceRelease(roomId.gameType(), roomId.mapName());
        require(occupancy.acquire(roomId).success(), "setup should acquire map occupancy");

        TrackingCleanupHooks hooks = new TrackingCleanupHooks();
        TrackingParticipant first = TrackingParticipant.success(10, "first");
        TrackingParticipant failing = TrackingParticipant.failure(20, "failing", ZombiesErrorCode.of("cleanup.participant_failed"));
        TrackingParticipant skipped = TrackingParticipant.success(30, "skipped");
        ZombiesCleanupService cleanupService = new ZombiesCleanupService(
                ModeEntityOwnershipRegistry.instance(),
                occupancy,
                hooks,
                List.of(skipped, failing, first));

        ZombiesServiceResult<ZombiesCleanupService.CleanupSummary> result =
                cleanupService.cleanup(roomId, "participant-failure", dimension -> null);

        requireFailure(result, ZombiesErrorCode.of("cleanup.participant_failed"),
                "failing cleanup participant should fail cleanup");
        require(first.ran, "lower-order participant should run before failing participant");
        require(failing.ran, "failing participant should run");
        require(!skipped.ran, "higher-order participant should not run after failure");
        require(hooks.beforeCleanupCount == 1, "beforeCleanup should run before participant failure");
        require(hooks.coreClearCount() == 0, "participant failure should not clear dependent runtime state");
        require(hooks.afterCleanupCount == 0, "participant failure should not report completed cleanup");
        require(hooks.afterOccupancyReleasedCount == 0, "participant failure should not release occupancy");
        require(occupancy.isOccupied(roomId), "participant failure should leave map occupancy for retry/debug");

        occupancy.forceRelease(roomId.gameType(), roomId.mapName());
    }

    private static void repeatedCleanupWithoutResidueStillCompletesAndReportsNoOccupancyRelease() {
        RoomId roomId = RoomId.of("zombies", "mvp12-cleanup-idempotent");
        ZombiesMapOccupancyService occupancy = ZombiesMapOccupancyService.instance();
        occupancy.forceRelease(roomId.gameType(), roomId.mapName());
        require(occupancy.acquire(roomId).success(), "setup should acquire map occupancy");

        TrackingCleanupHooks hooks = new TrackingCleanupHooks();
        ZombiesCleanupService cleanupService = new ZombiesCleanupService(
                ModeEntityOwnershipRegistry.instance(),
                occupancy,
                hooks,
                List.of(TrackingParticipant.success(0, "stable")));

        ZombiesServiceResult<ZombiesCleanupService.CleanupSummary> first =
                cleanupService.cleanup(roomId, "first", dimension -> null);
        ZombiesServiceResult<ZombiesCleanupService.CleanupSummary> second =
                cleanupService.cleanup(roomId, "second", dimension -> null);

        requireSuccess(first, "first cleanup should succeed");
        requireSuccess(second, "second cleanup should also succeed without residue");
        require(first.value().orElseThrow().cleanupRevision() == 1L,
                "first cleanup should expose revision 1");
        require(second.value().orElseThrow().cleanupRevision() == 2L,
                "second cleanup should expose revision 2");
        require(first.value().orElseThrow().occupancyReleased(), "first cleanup should release acquired occupancy");
        require(!second.value().orElseThrow().occupancyReleased(),
                "second cleanup should report no occupancy left to release");
        require(second.value().orElseThrow().entities().registeredEntries() == 0,
                "second cleanup should not find stale registered entities");
        require(hooks.beforeCleanupCount == 2, "both cleanup attempts should run before hook");
        require(hooks.coreClearCount() == 12, "both cleanup attempts should run every core clear hook");
        require(hooks.afterCleanupCount == 2, "both cleanup attempts should complete");
        require(!occupancy.isOccupied(roomId), "cleanup should leave map unoccupied");
    }

    private static void objectStoreResetDropsPreviousSnapshotRevisionAndOffer() {
        ZombiesObjectStateStore store = new ZombiesObjectStateStore();
        ZombiesWeaponWallData oldWall = weaponWall("shared-wall", "tacz:old");
        ZombiesWeaponWallData newWall = weaponWall("shared-wall", "tacz:new");
        ZombiesBarrierData oldBarrier = barrier("barrier-old", 2);
        ZombiesBarrierData newBarrier = barrier("barrier-new", 3);

        store.resetObjects(List.of(oldBarrier), List.of(oldWall), List.of(), List.of(), 1, 5);
        requireSuccess(store.clearBarrierGroup(2, List.of(oldBarrier)),
                "setup barrier clear should succeed");
        long purchasedRevision = store.markWeaponWallPurchased(oldWall);
        require(purchasedRevision > 0L, "setup wall purchase should advance revision");
        long oldRoomRevision = store.revision();

        store.resetObjects(List.of(newBarrier), List.of(newWall), List.of(), List.of(), 1, 5);
        List<ModeObjectState> states = store.objectStates(List.of(newBarrier), List.of(newWall), List.of(), List.of());
        ModeObjectState wallState = state(states, "shared-wall");
        ModeObjectState barrierState = state(states, "barrier-new");

        require(wallState.revision() > purchasedRevision,
                "reset should expose a new wall revision above the previous round purchase");
        require(barrierState.revision() > oldRoomRevision,
                "reset should expose new object revisions above the previous round room revision");
        require(store.revision() > oldRoomRevision, "reset should advance room object revision monotonically");
        require("tacz:new".equals(wallState.payload().getString("gunId")),
                "reset should expose the new snapshot weapon offer");
        require(!barrierState.payload().getBoolean("cleared"), "reset should make new barrier uncleared");
        require(barrierState.payload().getInt("group") == 3, "reset should expose new barrier group");
        require(states.stream().noneMatch(value -> "barrier-old".equals(value.objectKey())),
                "reset object states should not include objects absent from the new snapshot");
    }

    private static void objectStoreResetRevisionLetsClientAcceptNextRoundState() {
        String roomKey = "zombies:reset-object-revision";
        ClientModeObjectState.clear(roomKey);
        try {
            ZombiesObjectStateStore store = new ZombiesObjectStateStore();
            ZombiesWeaponWallData oldWall = weaponWall("shared-wall", "tacz:old");
            ZombiesWeaponWallData newWall = weaponWall("shared-wall", "tacz:new");

            store.resetObjects(List.of(), List.of(oldWall), List.of(), List.of(), 1, 5);
            long oldPurchaseRevision = store.markWeaponWallPurchased(oldWall);
            List<ModeObjectState> oldStates = store.objectStates(List.of(), List.of(oldWall), List.of(), List.of());
            long oldSyncRevision = maxRevision(oldStates);
            require(oldSyncRevision >= oldPurchaseRevision, "setup sync revision should include purchase revision");
            ClientModeObjectState.replaceRoomStates(roomKey, oldStates, oldSyncRevision);

            store.resetObjects(List.of(), List.of(newWall), List.of(), List.of(), 1, 5);
            List<ModeObjectState> nextRoundStates = store.objectStates(List.of(), List.of(newWall), List.of(), List.of());
            long nextRoundSyncRevision = maxRevision(nextRoundStates);

            require(nextRoundSyncRevision > oldSyncRevision,
                    "reset object sync revision should stay above previous round revision");
            ClientModeObjectState.replaceRoomStates(roomKey, nextRoundStates, nextRoundSyncRevision);

            ModeObjectState retained = ClientModeObjectState.roomStates(roomKey).get("shared-wall");
            require(retained != null, "client should retain next round object state");
            require("tacz:new".equals(retained.payload().getString("gunId")),
                    "client should accept next round object state instead of dropping it as stale");
            require(ClientModeObjectState.revision(roomKey).orElseThrow() == nextRoundSyncRevision,
                    "client room object revision should advance to next round revision");
        } finally {
            ClientModeObjectState.clear(roomKey);
        }
    }

    private static ZombiesWeaponWallData weaponWall(String objectId, String gunId) {
        return new ZombiesWeaponWallData(
                objectId,
                2,
                1.25D,
                600,
                210,
                List.of(3),
                List.of(new ZombiesWeaponWallData.RarityPoolData("common", 1, 100.0D, 0.0D)),
                List.of(new ZombiesWeaponWallData.WeaponCandidateData(gunId, Map.of("common", 1.0D))),
                dimension(),
                new BlockPos(1, 64, 1),
                Optional.empty());
    }

    private static ZombiesBarrierData barrier(String objectId, int group) {
        return new ZombiesBarrierData(
                objectId,
                group,
                750,
                true,
                dimension(),
                new BlockPos(group, 64, 5),
                new BlockPos(group, 66, 7),
                new BlockPos(group, 65, 5));
    }

    private static ModeObjectState state(List<ModeObjectState> states, String objectKey) {
        return states.stream()
                .filter(state -> objectKey.equals(state.objectKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing object state " + objectKey));
    }

    private static long maxRevision(List<ModeObjectState> states) {
        return states.stream()
                .mapToLong(ModeObjectState::revision)
                .max()
                .orElse(0L);
    }

    private static void requireSuccess(ZombiesServiceResult<?> result, String message) {
        require(result.success(), message + ": " + result.code());
    }

    private static void requireFailure(ZombiesServiceResult<?> result, ZombiesErrorCode expectedCode, String message) {
        require(!result.success(), message + ": expected failure");
        require(expectedCode.equals(result.code()),
                message + ": expected " + expectedCode + " but was " + result.code());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ResourceKey<Level> dimension() {
        try {
            Constructor<ResourceKey> constructor =
                    ResourceKey.class.getDeclaredConstructor(ResourceLocation.class, ResourceLocation.class);
            constructor.setAccessible(true);
            return (ResourceKey<Level>) constructor.newInstance(
                    resourceLocation("minecraft:dimension"),
                    resourceLocation("minecraft:overworld"));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("failed to create test dimension key", exception);
        }
    }

    private static ResourceLocation resourceLocation(String value) {
        ResourceLocation location = ResourceLocation.tryParse(value);
        if (location == null) {
            throw new AssertionError("invalid resource location " + value);
        }
        return location;
    }

    private static final class TrackingCleanupHooks implements ZombiesCleanupService.Hooks {
        private int beforeCleanupCount;
        private int objectClears;
        private int playerClears;
        private int readyClears;
        private int voteClears;
        private int lifecycleClears;
        private int hudClears;
        private int afterOccupancyReleasedCount;
        private int afterCleanupCount;

        @Override
        public void beforeCleanup(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
            beforeCleanupCount++;
        }

        @Override
        public void clearObjectRuntime(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
            objectClears++;
        }

        @Override
        public void clearPlayerRuntime(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
            playerClears++;
        }

        @Override
        public void clearReadyState(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
            readyClears++;
        }

        @Override
        public void clearStartVote(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
            voteClears++;
        }

        @Override
        public void clearLifecycleRuntime(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
            lifecycleClears++;
        }

        @Override
        public void clearHudState(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
            hudClears++;
        }

        @Override
        public void afterOccupancyReleased(ZombiesCleanupParticipant.ZombiesCleanupContext context, boolean released) {
            afterOccupancyReleasedCount++;
        }

        @Override
        public void afterCleanup(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
            afterCleanupCount++;
        }

        private int coreClearCount() {
            return objectClears + playerClears + readyClears + voteClears + lifecycleClears + hudClears;
        }
    }

    private static final class TrackingParticipant implements ZombiesCleanupParticipant {
        private final int order;
        private final String name;
        private final ZombiesErrorCode failureCode;
        private boolean ran;

        private TrackingParticipant(int order, String name, ZombiesErrorCode failureCode) {
            this.order = order;
            this.name = name;
            this.failureCode = failureCode;
        }

        private static TrackingParticipant success(int order, String name) {
            return new TrackingParticipant(order, name, null);
        }

        private static TrackingParticipant failure(int order, String name, ZombiesErrorCode code) {
            return new TrackingParticipant(order, name, code);
        }

        @Override
        public int order() {
            return order;
        }

        @Override
        public ZombiesServiceResult<Void> cleanup(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
            ran = true;
            if (failureCode != null) {
                return ZombiesServiceResult.failure(failureCode, Map.of(), name);
            }
            return ZombiesServiceResult.ok();
        }
    }
}
