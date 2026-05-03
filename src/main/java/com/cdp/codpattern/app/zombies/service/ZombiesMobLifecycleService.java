package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;
import com.cdp.codpattern.app.zombies.runtime.ZombiesWaveRuntimeState;
import net.minecraft.world.entity.Entity;

import java.util.Objects;
import java.util.Optional;

public final class ZombiesMobLifecycleService {
    private final ModeEntityOwnershipRegistry ownershipRegistry;
    private final ZombiesMobSpawnService spawnService;

    public ZombiesMobLifecycleService() {
        this(ModeEntityOwnershipRegistry.instance(), null);
    }

    public ZombiesMobLifecycleService(
            ModeEntityOwnershipRegistry ownershipRegistry,
            ZombiesMobSpawnService spawnService
    ) {
        this.ownershipRegistry = Objects.requireNonNull(ownershipRegistry, "ownershipRegistry");
        this.spawnService = spawnService;
    }

    public LifecycleResult onKilled(RoomId roomId, Entity entity, ZombiesWaveRuntimeState waveState) {
        return unregister(roomId, entity, waveState, TerminationReason.KILLED);
    }

    public LifecycleResult onRemoved(RoomId roomId, Entity entity, ZombiesWaveRuntimeState waveState) {
        return unregister(roomId, entity, waveState, TerminationReason.REMOVED_CONSUME_BUDGET);
    }

    public LifecycleResult onRecycledForRetry(RoomId roomId, Entity entity, ZombiesWaveRuntimeState waveState) {
        return unregister(roomId, entity, waveState, TerminationReason.RECYCLED_RETRY);
    }

    public LifecycleResult onCleanup(RoomId roomId, Entity entity, ZombiesWaveRuntimeState waveState) {
        return unregister(roomId, entity, waveState, TerminationReason.CLEANUP);
    }

    public LifecycleResult unregister(
            RoomId roomId,
            Entity entity,
            ZombiesWaveRuntimeState waveState,
            TerminationReason reason
    ) {
        if (entity == null) {
            return LifecycleResult.ignored(reason, "entity_missing");
        }

        Optional<ModeEntityOwnershipRegistry.Entry> existingEntry = ownershipRegistry.entryOf(entity);
        if (existingEntry.isPresent() && roomId != null && !sameRoom(existingEntry.get().roomId(), roomId)) {
            return LifecycleResult.ignored(reason, "room_mismatch");
        }

        Optional<ModeEntityOwnershipRegistry.Entry> removedEntry = ownershipRegistry.unregister(entity);
        boolean activeRemoved = waveState != null && waveState.unregisterActiveZombie(entity.getUUID(), reason.key());
        if (activeRemoved && spawnService != null) {
            spawnService.recordMobEnded();
        }
        if (!activeRemoved && waveState != null) {
            waveState.recordLifecycleReason(reason.key());
        }
        return new LifecycleResult(removedEntry.isPresent() || activeRemoved, reason, "");
    }

    private static boolean sameRoom(RoomId left, RoomId right) {
        return left != null
                && right != null
                && left.gameType().equalsIgnoreCase(right.gameType())
                && left.mapName().equals(right.mapName());
    }

    public enum TerminationReason {
        KILLED("KILLED"),
        RECYCLED_RETRY("RECYCLED_RETRY"),
        REMOVED_CONSUME_BUDGET("REMOVED_CONSUME_BUDGET"),
        CLEANUP("CLEANUP");

        private final String key;

        TerminationReason(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    public record LifecycleResult(
            boolean unregistered,
            TerminationReason reason,
            String ignoredReason
    ) {
        public LifecycleResult {
            ignoredReason = ignoredReason == null ? "" : ignoredReason;
        }

        public static LifecycleResult ignored(TerminationReason reason, String ignoredReason) {
            return new LifecycleResult(false, reason, ignoredReason);
        }
    }
}
