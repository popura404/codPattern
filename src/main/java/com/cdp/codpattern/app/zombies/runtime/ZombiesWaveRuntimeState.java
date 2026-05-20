package com.cdp.codpattern.app.zombies.runtime;

import com.cdp.codpattern.app.zombies.model.ZombiesWaveDefinition;
import com.cdp.codpattern.app.zombies.model.ZombiesWaveMobEntry;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal wave counters owned by the lifecycle skeleton; spawning/director services can attach richer state later.
 */
public final class ZombiesWaveRuntimeState {
    private int currentWave;
    private int targetWave = 1;
    private int maxWave;
    private int activeZombies;
    private int remainingBudget;
    private int waveTimeTicks;
    private int waveCompleteDelayTicks;
    private boolean waveComplete;
    private boolean budgetInitialized;
    private long lastSpawnAttemptTick = Long.MIN_VALUE;
    private int nextSpawnIntervalTicks;
    private String recentSpawnFailureReason = "";
    private String recentLifecycleReason = "";
    private final Map<String, Integer> remainingBudgetByMobId = new HashMap<>();
    private final Map<String, Deque<Integer>> requeuedRecycleCountsByMobId = new HashMap<>();
    private final Set<UUID> activeZombieEntityIds = ConcurrentHashMap.newKeySet();

    public int currentWave() {
        return currentWave;
    }

    public int targetWave() {
        return targetWave;
    }

    public int maxWave() {
        return maxWave;
    }

    public int activeZombies() {
        return activeZombies;
    }

    public int remainingBudget() {
        return remainingBudget;
    }

    public int waveTimeTicks() {
        return waveTimeTicks;
    }

    public int waveCompleteDelayTicks() {
        return waveCompleteDelayTicks;
    }

    public boolean isWaveComplete() {
        return waveComplete || (currentWave > 0 && remainingBudget <= 0 && activeZombies <= 0);
    }

    public boolean isBudgetInitialized() {
        return budgetInitialized;
    }

    public long lastSpawnAttemptTick() {
        return lastSpawnAttemptTick;
    }

    public int nextSpawnIntervalTicks() {
        return nextSpawnIntervalTicks;
    }

    public Optional<String> recentSpawnFailureReason() {
        return recentSpawnFailureReason == null || recentSpawnFailureReason.isBlank()
                ? Optional.empty()
                : Optional.of(recentSpawnFailureReason);
    }

    public Optional<String> recentLifecycleReason() {
        return recentLifecycleReason == null || recentLifecycleReason.isBlank()
                ? Optional.empty()
                : Optional.of(recentLifecycleReason);
    }

    public Map<String, Integer> remainingBudgetByMobIdSnapshot() {
        return Map.copyOf(remainingBudgetByMobId);
    }

    public Set<UUID> activeZombieEntityIdsSnapshot() {
        return Set.copyOf(activeZombieEntityIds);
    }

    public void prepareTargetWave(int targetWave) {
        this.targetWave = Math.max(1, targetWave);
        budgetInitialized = false;
        waveComplete = false;
        waveTimeTicks = 0;
        waveCompleteDelayTicks = 0;
    }

    public void beginTargetWave() {
        currentWave = Math.max(1, targetWave);
        budgetInitialized = false;
        waveComplete = false;
        waveTimeTicks = 0;
        waveCompleteDelayTicks = 0;
    }

    public void beginTargetWave(ZombiesWaveDefinition definition) {
        beginTargetWave();
        initializeBudget(definition);
    }

    public void initializeBudget(ZombiesWaveDefinition definition) {
        remainingBudgetByMobId.clear();
        requeuedRecycleCountsByMobId.clear();
        activeZombieEntityIds.clear();
        activeZombies = 0;
        remainingBudget = 0;
        waveCompleteDelayTicks = 0;
        recentSpawnFailureReason = "";
        recentLifecycleReason = "";
        lastSpawnAttemptTick = Long.MIN_VALUE;
        nextSpawnIntervalTicks = 0;
        budgetInitialized = true;
        if (definition == null) {
            waveComplete = true;
            return;
        }
        for (ZombiesWaveMobEntry mob : definition.getMobs()) {
            if (mob == null || mob.getCount() <= 0 || mob.getEntity() == null || mob.getEntity().isBlank()) {
                continue;
            }
            String mobId = mob.getEntity().trim();
            remainingBudgetByMobId.merge(mobId, mob.getCount(), Integer::sum);
            remainingBudget += mob.getCount();
        }
        waveComplete = remainingBudget <= 0;
    }

    public void configureMaxWave(int maxWave) {
        this.maxWave = Math.max(0, maxWave);
    }

    public void setActiveZombies(int activeZombies) {
        this.activeZombies = Math.max(0, activeZombies);
        resetWaveCompleteDelayIfIncomplete();
    }

    public void setRemainingBudget(int remainingBudget) {
        this.remainingBudget = Math.max(0, remainingBudget);
        resetWaveCompleteDelayIfIncomplete();
    }

    public void recordSpawnAttempt(long roomTick) {
        recordSpawnAttempt(roomTick, nextSpawnIntervalTicks);
    }

    public void recordSpawnAttempt(long roomTick, int nextSpawnIntervalTicks) {
        lastSpawnAttemptTick = roomTick;
        configureNextSpawnIntervalTicks(nextSpawnIntervalTicks);
    }

    public void configureNextSpawnIntervalTicks(int nextSpawnIntervalTicks) {
        this.nextSpawnIntervalTicks = Math.max(0, nextSpawnIntervalTicks);
    }

    public void recordSpawnFailure(String reason) {
        recentSpawnFailureReason = reason == null ? "" : reason.trim();
    }

    public void recordLifecycleReason(String reason) {
        recentLifecycleReason = reason == null ? "" : reason.trim();
    }

    public Optional<String> nextBudgetedMobId() {
        return remainingBudgetByMobId.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public boolean consumeBudget(String mobId) {
        if (mobId == null || mobId.isBlank()) {
            return false;
        }
        String key = mobId.trim();
        int count = remainingBudgetByMobId.getOrDefault(key, 0);
        if (count <= 0 || remainingBudget <= 0) {
            return false;
        }
        if (count == 1) {
            remainingBudgetByMobId.remove(key);
        } else {
            remainingBudgetByMobId.put(key, count - 1);
        }
        remainingBudget = Math.max(0, remainingBudget - 1);
        waveComplete = remainingBudget <= 0 && activeZombies <= 0;
        resetWaveCompleteDelayIfIncomplete();
        recentSpawnFailureReason = "";
        return true;
    }

    public boolean registerActiveZombie(UUID entityId) {
        if (entityId == null || !activeZombieEntityIds.add(entityId)) {
            return false;
        }
        activeZombies = activeZombieEntityIds.size();
        waveComplete = remainingBudget <= 0 && activeZombies <= 0;
        resetWaveCompleteDelayIfIncomplete();
        return true;
    }

    public boolean unregisterActiveZombie(UUID entityId, String reason) {
        if (entityId == null || !activeZombieEntityIds.remove(entityId)) {
            return false;
        }
        activeZombies = activeZombieEntityIds.size();
        recordLifecycleReason(reason);
        waveComplete = remainingBudget <= 0 && activeZombies <= 0;
        resetWaveCompleteDelayIfIncomplete();
        return true;
    }

    public boolean requeueBudget(String mobId) {
        return requeueBudget(mobId, 0);
    }

    public boolean requeueBudget(String mobId, int recycleCount) {
        if (mobId == null || mobId.isBlank()) {
            return false;
        }
        String key = mobId.trim();
        remainingBudgetByMobId.merge(key, 1, Integer::sum);
        requeuedRecycleCountsByMobId
                .computeIfAbsent(key, ignored -> new ArrayDeque<>())
                .addLast(Math.max(0, recycleCount));
        remainingBudget++;
        waveComplete = false;
        resetWaveCompleteDelay();
        return true;
    }

    public int consumeRequeuedRecycleCount(String mobId) {
        if (mobId == null || mobId.isBlank()) {
            return 0;
        }
        String key = mobId.trim();
        Deque<Integer> counts = requeuedRecycleCountsByMobId.get(key);
        if (counts == null || counts.isEmpty()) {
            return 0;
        }
        int recycleCount = Math.max(0, counts.removeFirst());
        if (counts.isEmpty()) {
            requeuedRecycleCountsByMobId.remove(key);
        }
        return recycleCount;
    }

    public void markWaveComplete() {
        this.waveComplete = true;
        this.remainingBudget = 0;
        this.activeZombies = 0;
        this.waveCompleteDelayTicks = 0;
        activeZombieEntityIds.clear();
        remainingBudgetByMobId.clear();
        requeuedRecycleCountsByMobId.clear();
    }

    public boolean tickWaveCompleteDelay(int requiredTicks) {
        if (requiredTicks <= 0) {
            return true;
        }
        waveCompleteDelayTicks++;
        return waveCompleteDelayTicks >= requiredTicks;
    }

    public void resetWaveCompleteDelay() {
        waveCompleteDelayTicks = 0;
    }

    public void tickWaveTime() {
        waveTimeTicks++;
    }

    public void reset() {
        currentWave = 0;
        targetWave = 1;
        maxWave = 0;
        activeZombies = 0;
        remainingBudget = 0;
        waveTimeTicks = 0;
        waveCompleteDelayTicks = 0;
        waveComplete = false;
        budgetInitialized = false;
        lastSpawnAttemptTick = Long.MIN_VALUE;
        nextSpawnIntervalTicks = 0;
        recentSpawnFailureReason = "";
        recentLifecycleReason = "";
        remainingBudgetByMobId.clear();
        requeuedRecycleCountsByMobId.clear();
        activeZombieEntityIds.clear();
    }

    private void resetWaveCompleteDelayIfIncomplete() {
        if (!isWaveComplete()) {
            resetWaveCompleteDelay();
        }
    }
}
