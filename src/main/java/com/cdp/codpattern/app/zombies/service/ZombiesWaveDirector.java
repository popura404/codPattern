package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.zombies.map.ZombiesMapObjects;
import com.cdp.codpattern.app.zombies.model.ZombiesWaveDefinition;
import com.cdp.codpattern.app.zombies.runtime.ZombiesWaveRuntimeState;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ZombiesWaveDirector {
    public static final Set<Integer> DEFAULT_ACTIVE_SPAWN_GROUPS = Set.of(1);

    private final ZombiesMobSpawnService spawnService;
    private final Map<Integer, ZombiesWaveDefinition> wavesByNumber;
    private final int maxWave;

    public ZombiesWaveDirector(Collection<ZombiesWaveDefinition> waves) {
        this(waves, new ZombiesMobSpawnService());
    }

    public ZombiesWaveDirector(Collection<ZombiesWaveDefinition> waves, ZombiesMobSpawnService spawnService) {
        this.spawnService = Objects.requireNonNull(spawnService, "spawnService");
        this.wavesByNumber = safeWaves(waves);
        this.maxWave = wavesByNumber.keySet().stream().max(Comparator.naturalOrder()).orElse(0);
    }

    public ZombiesWaveDirector(ZombiesWaveConfigRepository.LoadResult loadResult, ZombiesMobSpawnService spawnService) {
        this(loadResult == null ? List.of() : loadResult.getWaves(), spawnService);
    }

    public int maxWave() {
        return maxWave;
    }

    public Optional<ZombiesWaveDefinition> waveDefinition(int wave) {
        return Optional.ofNullable(wavesByNumber.get(wave));
    }

    public void configureMaxWave(ZombiesWaveRuntimeState waveState) {
        if (waveState != null) {
            waveState.configureMaxWave(maxWave);
        }
    }

    public EnterWaveResult enterTargetWave(ZombiesWaveRuntimeState waveState) {
        Objects.requireNonNull(waveState, "waveState");
        int targetWave = Math.max(1, waveState.targetWave());
        ZombiesWaveDefinition definition = wavesByNumber.get(targetWave);
        waveState.configureMaxWave(maxWave);
        waveState.beginTargetWave(definition);
        if (definition == null) {
            waveState.recordSpawnFailure("wave.missing_empty");
        } else if (waveState.remainingBudget() <= 0) {
            waveState.recordSpawnFailure("wave.empty");
        }
        return new EnterWaveResult(targetWave, definition != null, waveState.remainingBudget());
    }

    public TickResult tick(
            RoomId roomId,
            ServerLevel level,
            ZombiesMapObjects mapObjects,
            ZombiesWaveRuntimeState waveState,
            long roomTick
    ) {
        return tick(roomId, level, mapObjects, waveState, roomTick, DEFAULT_ACTIVE_SPAWN_GROUPS);
    }

    public TickResult tick(
            RoomId roomId,
            ServerLevel level,
            ZombiesMapObjects mapObjects,
            ZombiesWaveRuntimeState waveState,
            long roomTick,
            Set<Integer> activeSpawnGroups
    ) {
        Objects.requireNonNull(waveState, "waveState");
        if (waveState.currentWave() <= 0 || !waveState.isBudgetInitialized()) {
            enterTargetWave(waveState);
        }
        if (waveState.isWaveComplete()) {
            return TickResult.completed();
        }

        ZombiesWaveDefinition definition = wavesByNumber.get(waveState.currentWave());
        if (definition == null || waveState.remainingBudget() <= 0) {
            if (waveState.activeZombies() <= 0) {
                waveState.markWaveComplete();
                return TickResult.completed();
            }
            return TickResult.waiting("wave.waiting_active_zombies");
        }

        if (waveState.activeZombies() >= Math.max(1, definition.getMaxAlive())) {
            waveState.recordSpawnFailure("spawn.max_alive_reached");
            return TickResult.throttled("spawn.max_alive_reached");
        }
        int interval = Math.max(1, definition.getSpawnIntervalTicks());
        long lastAttempt = waveState.lastSpawnAttemptTick();
        if (lastAttempt != Long.MIN_VALUE && roomTick - lastAttempt < interval) {
            return TickResult.waiting("spawn.interval_wait");
        }

        waveState.recordSpawnAttempt(roomTick);
        ZombiesMobSpawnService.SpawnResult spawnResult = spawnService.spawnNext(
                roomId,
                level,
                mapObjects,
                waveState,
                definition,
                activeSpawnGroups);
        if (spawnResult.spawned()) {
            return TickResult.spawned(spawnResult);
        }
        waveState.recordSpawnFailure(spawnResult.failureKey());
        if (waveState.isWaveComplete()) {
            return TickResult.completed();
        }
        return TickResult.spawnFailed(spawnResult.failureKey());
    }

    public boolean isWaveComplete(ZombiesWaveRuntimeState waveState) {
        return waveState != null && waveState.isWaveComplete();
    }

    private static Map<Integer, ZombiesWaveDefinition> safeWaves(Collection<ZombiesWaveDefinition> waves) {
        if (waves == null || waves.isEmpty()) {
            return Map.of();
        }
        return waves.stream()
                .filter(Objects::nonNull)
                .filter(ZombiesWaveDefinition::hasMobsField)
                .filter(wave -> !wave.hasWaveConflict())
                .collect(Collectors.toUnmodifiableMap(
                        ZombiesWaveDefinition::getWave,
                        Function.identity(),
                        (left, ignoredRight) -> left));
    }

    public record EnterWaveResult(
            int wave,
            boolean definitionPresent,
            int initialBudget
    ) {
    }

    public record TickResult(
            TickOutcome outcome,
            Optional<ZombiesMobSpawnService.SpawnResult> spawnResult,
            String reason
    ) {
        public TickResult {
            spawnResult = spawnResult == null ? Optional.empty() : spawnResult;
            reason = reason == null ? "" : reason;
        }

        public static TickResult spawned(ZombiesMobSpawnService.SpawnResult spawnResult) {
            return new TickResult(TickOutcome.SPAWNED, Optional.of(spawnResult), "");
        }

        public static TickResult spawnFailed(String reason) {
            return new TickResult(TickOutcome.SPAWN_FAILED, Optional.empty(), reason);
        }

        public static TickResult throttled(String reason) {
            return new TickResult(TickOutcome.THROTTLED, Optional.empty(), reason);
        }

        public static TickResult waiting(String reason) {
            return new TickResult(TickOutcome.WAITING, Optional.empty(), reason);
        }

        public static TickResult completed() {
            return new TickResult(TickOutcome.WAVE_COMPLETE, Optional.empty(), "");
        }
    }

    public enum TickOutcome {
        SPAWNED,
        SPAWN_FAILED,
        THROTTLED,
        WAITING,
        WAVE_COMPLETE
    }
}
