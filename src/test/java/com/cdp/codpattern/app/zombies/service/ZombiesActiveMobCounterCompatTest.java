package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;
import com.cdp.codpattern.app.zombies.runtime.ZombiesWaveRuntimeState;

import java.util.List;
import java.util.UUID;

public final class ZombiesActiveMobCounterCompatTest {
    private ZombiesActiveMobCounterCompatTest() {
    }

    public static void main(String[] args) {
        tracksRoomAndTotalCounts();
        movingEntityBetweenRoomsDoesNotDoubleCount();
        lifecycleMissingEntityRemovesRoomCounterEntry();
        reconcileRebuildsCountsFromZombiesOwnershipEntries();
    }

    private static void tracksRoomAndTotalCounts() {
        ZombiesActiveMobCounter counter = new ZombiesActiveMobCounter();
        RoomId firstRoom = RoomId.of("zombies", "counter-a");
        RoomId secondRoom = RoomId.of("zombies", "counter-b");
        UUID firstEntity = UUID.randomUUID();
        UUID secondEntity = UUID.randomUUID();

        counter.register(firstRoom, firstEntity);
        counter.register(secondRoom, secondEntity);

        require(counter.roomCount(firstRoom) == 1, "first room should count its entity");
        require(counter.roomCount(secondRoom) == 1, "second room should count its entity");
        require(counter.totalCount() == 2, "total should be the sum of room counts");

        require(counter.unregister(firstRoom, firstEntity), "unregister should remove tracked entity");
        require(counter.roomCount(firstRoom) == 0, "first room count should be empty after unregister");
        require(counter.totalCount() == 1, "total should drop without touching the other room");
    }

    private static void movingEntityBetweenRoomsDoesNotDoubleCount() {
        ZombiesActiveMobCounter counter = new ZombiesActiveMobCounter();
        RoomId firstRoom = RoomId.of("zombies", "move-a");
        RoomId secondRoom = RoomId.of("zombies", "move-b");
        UUID entityId = UUID.randomUUID();

        counter.register(firstRoom, entityId);
        counter.register(secondRoom, entityId);

        require(counter.roomCount(firstRoom) == 0, "moving an entity should remove it from the old room");
        require(counter.roomCount(secondRoom) == 1, "moving an entity should register it in the new room");
        require(counter.totalCount() == 1, "moving an entity should not double count globally");
    }

    private static void lifecycleMissingEntityRemovesRoomCounterEntry() {
        ZombiesActiveMobCounter counter = new ZombiesActiveMobCounter();
        RoomId roomId = RoomId.of("zombies", "missing-lifecycle");
        UUID entityId = UUID.randomUUID();
        ZombiesWaveRuntimeState waveState = new ZombiesWaveRuntimeState();
        ZombiesMobSpawnService spawnService = new ZombiesMobSpawnService(
                ModeEntityOwnershipRegistry.instance(),
                ZombiesMobSpawnService.DEFAULT_GLOBAL_MAX_ALIVE_ZOMBIES,
                null,
                null,
                counter);
        ZombiesMobLifecycleService lifecycleService = new ZombiesMobLifecycleService(
                ModeEntityOwnershipRegistry.instance(),
                spawnService);

        counter.register(roomId, entityId);
        waveState.registerActiveZombie(entityId);
        ZombiesMobLifecycleService.LifecycleResult result = lifecycleService.onMissing(
                roomId,
                entityId,
                waveState,
                ZombiesMobLifecycleService.TerminationReason.CLEANUP);

        require(result.unregistered(), "missing entity lifecycle should report cleanup work");
        require(waveState.activeZombies() == 0, "missing entity lifecycle should clear wave active count");
        require(counter.roomCount(roomId) == 0, "missing entity lifecycle should clear room counter");
        require(counter.totalCount() == 0, "missing entity lifecycle should clear total counter");
    }

    private static void reconcileRebuildsCountsFromZombiesOwnershipEntries() {
        ZombiesActiveMobCounter counter = new ZombiesActiveMobCounter();
        RoomId firstRoom = RoomId.of("zombies", "reconcile-a");
        RoomId secondRoom = RoomId.of("ZOMBIES", "reconcile-b");
        RoomId nonZombiesRoom = RoomId.of("team_deathmatch", "ignored");
        UUID firstEntity = UUID.randomUUID();
        UUID secondEntity = UUID.randomUUID();
        UUID ignoredEntity = UUID.randomUUID();

        counter.register(firstRoom, UUID.randomUUID());
        ZombiesActiveMobCounter.ReconcileSummary summary = counter.reconcile(List.of(
                new ModeEntityOwnershipRegistry.Entry(firstRoom, null, firstEntity),
                new ModeEntityOwnershipRegistry.Entry(secondRoom, null, secondEntity),
                new ModeEntityOwnershipRegistry.Entry(nonZombiesRoom, null, ignoredEntity)
        ), null);

        require(summary.retainedEntries() == 2, "reconcile should retain zombies entries");
        require(summary.skippedEntries() == 1, "reconcile should skip non-zombies entries");
        require(counter.roomCount(firstRoom) == 1, "reconcile should rebuild first room count");
        require(counter.roomCount(secondRoom) == 1, "reconcile should rebuild second room count");
        require(counter.totalCount() == 2, "reconcile total should come from rebuilt room sets");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
