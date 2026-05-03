package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;
import com.cdp.codpattern.app.zombies.map.ZombiesMapObjects;
import com.cdp.codpattern.app.zombies.map.object.ZombiesZombieSpawnData;
import com.cdp.codpattern.app.zombies.model.ZombiesWaveDefinition;
import com.cdp.codpattern.app.zombies.runtime.ZombiesWaveRuntimeState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class ZombiesMobSpawnService {
    public static final int DEFAULT_GLOBAL_MAX_ALIVE_ZOMBIES = 64;

    private static final AtomicInteger GLOBAL_ACTIVE_ZOMBIES = new AtomicInteger();

    private final ModeEntityOwnershipRegistry ownershipRegistry;
    private final int globalMaxAliveZombies;

    public ZombiesMobSpawnService() {
        this(ModeEntityOwnershipRegistry.instance(), DEFAULT_GLOBAL_MAX_ALIVE_ZOMBIES);
    }

    public ZombiesMobSpawnService(ModeEntityOwnershipRegistry ownershipRegistry, int globalMaxAliveZombies) {
        this.ownershipRegistry = Objects.requireNonNull(ownershipRegistry, "ownershipRegistry");
        this.globalMaxAliveZombies = Math.max(1, globalMaxAliveZombies);
    }

    public SpawnResult spawnNext(
            RoomId roomId,
            ServerLevel level,
            ZombiesMapObjects mapObjects,
            ZombiesWaveRuntimeState waveState,
            ZombiesWaveDefinition waveDefinition,
            Set<Integer> activeSpawnGroups
    ) {
        Objects.requireNonNull(waveState, "waveState");
        if (roomId == null || level == null) {
            return SpawnResult.failure(SpawnFailureReason.INVALID_CONTEXT);
        }
        if (waveState.remainingBudget() <= 0) {
            return SpawnResult.failure(SpawnFailureReason.NO_BUDGET);
        }
        if (waveState.activeZombies() >= safeMaxAlive(waveDefinition)) {
            return SpawnResult.failure(SpawnFailureReason.MAX_ALIVE_REACHED);
        }
        if (GLOBAL_ACTIVE_ZOMBIES.get() >= globalMaxAliveZombies) {
            return SpawnResult.failure(SpawnFailureReason.GLOBAL_CAP_REACHED);
        }

        Optional<String> mobId = nextSupportedMobId(waveState);
        if (mobId.isEmpty()) {
            return SpawnResult.failure(SpawnFailureReason.UNSUPPORTED_MOB_ID);
        }

        List<ZombiesZombieSpawnData> candidates = spawnCandidates(level, mapObjects, activeSpawnGroups);
        if (candidates.isEmpty()) {
            return SpawnResult.failure(SpawnFailureReason.NO_AVAILABLE_SPAWN);
        }
        List<ZombiesZombieSpawnData> loadedCandidates = candidates.stream()
                .filter(spawn -> level.hasChunkAt(spawn.pos()))
                .toList();
        if (loadedCandidates.isEmpty()) {
            return SpawnResult.failure(SpawnFailureReason.CHUNK_UNAVAILABLE);
        }

        ZombiesZombieSpawnData spawn = chooseSpawn(level, loadedCandidates);
        Mob mob = createSupportedMob(level, mobId.get());
        if (mob == null) {
            return SpawnResult.failure(SpawnFailureReason.ENTITY_CREATE_FAILED);
        }
        mob.moveTo(
                spawn.pos().getX() + 0.5D,
                spawn.pos().getY(),
                spawn.pos().getZ() + 0.5D,
                spawn.yaw(),
                spawn.pitch());
        applyWaveAttributes(mob, waveDefinition);

        if (!level.addFreshEntity(mob)) {
            return SpawnResult.failure(SpawnFailureReason.ENTITY_ADD_FAILED);
        }
        if (!waveState.consumeBudget(mobId.get())) {
            mob.discard();
            return SpawnResult.failure(SpawnFailureReason.NO_BUDGET);
        }
        ownershipRegistry.register(roomId, mob);
        waveState.registerActiveZombie(mob.getUUID());
        GLOBAL_ACTIVE_ZOMBIES.incrementAndGet();
        return SpawnResult.spawned(mob, mobId.get(), spawn.objectId());
    }

    public void recordMobEnded() {
        GLOBAL_ACTIVE_ZOMBIES.updateAndGet(value -> Math.max(0, value - 1));
    }

    public int globalActiveZombies() {
        return GLOBAL_ACTIVE_ZOMBIES.get();
    }

    public int globalMaxAliveZombies() {
        return globalMaxAliveZombies;
    }

    private static int safeMaxAlive(ZombiesWaveDefinition waveDefinition) {
        return waveDefinition == null ? 1 : Math.max(1, waveDefinition.getMaxAlive());
    }

    private static List<ZombiesZombieSpawnData> spawnCandidates(
            ServerLevel level,
            ZombiesMapObjects mapObjects,
            Set<Integer> activeSpawnGroups
    ) {
        ZombiesMapObjects objects = mapObjects == null ? ZombiesMapObjects.EMPTY : mapObjects;
        Set<Integer> groups = activeSpawnGroups == null || activeSpawnGroups.isEmpty() ? Set.of(1) : Set.copyOf(activeSpawnGroups);
        List<ZombiesZombieSpawnData> candidates = new ArrayList<>();
        for (ZombiesZombieSpawnData spawn : objects.zombieSpawns()) {
            if (spawn == null || spawn.weight() <= 0.0D || !groups.contains(spawn.group())) {
                continue;
            }
            if (!level.dimension().equals(spawn.dimension())) {
                continue;
            }
            candidates.add(spawn);
        }
        return candidates;
    }

    private static Optional<String> nextSupportedMobId(ZombiesWaveRuntimeState waveState) {
        return waveState.remainingBudgetByMobIdSnapshot().entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .map(Map.Entry::getKey)
                .filter(ZombiesWaveValidator::isSupportedEntityId)
                .findFirst();
    }

    private static ZombiesZombieSpawnData chooseSpawn(ServerLevel level, List<ZombiesZombieSpawnData> candidates) {
        double totalWeight = candidates.stream()
                .mapToDouble(spawn -> Math.max(0.0D, spawn.weight()))
                .sum();
        if (totalWeight <= 0.0D) {
            return candidates.get(0);
        }
        double selected = level.random.nextDouble() * totalWeight;
        double cursor = 0.0D;
        for (ZombiesZombieSpawnData candidate : candidates) {
            cursor += Math.max(0.0D, candidate.weight());
            if (selected <= cursor) {
                return candidate;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    private static void applyWaveAttributes(Mob mob, ZombiesWaveDefinition waveDefinition) {
        if (waveDefinition == null) {
            return;
        }
        multiplyAttribute(mob, Attributes.MAX_HEALTH, waveDefinition.getHealthMultiplier());
        multiplyAttribute(mob, Attributes.MOVEMENT_SPEED, waveDefinition.getSpeedMultiplier());
        multiplyAttribute(mob, Attributes.ATTACK_DAMAGE, waveDefinition.getDamageMultiplier());
        mob.setHealth(mob.getMaxHealth());
    }

    private static void multiplyAttribute(Mob mob, net.minecraft.world.entity.ai.attributes.Attribute attribute, double multiplier) {
        if (!Double.isFinite(multiplier) || multiplier <= 0.0D) {
            return;
        }
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(instance.getBaseValue() * multiplier);
        }
    }

    public enum SpawnFailureReason {
        INVALID_CONTEXT("spawn.invalid_context"),
        NO_BUDGET("spawn.no_budget"),
        MAX_ALIVE_REACHED("spawn.max_alive_reached"),
        GLOBAL_CAP_REACHED("spawn.global_cap_reached"),
        UNSUPPORTED_MOB_ID("spawn.unsupported_mob_id"),
        NO_AVAILABLE_SPAWN("spawn.no_available_spawn"),
        CHUNK_UNAVAILABLE("spawn.chunk_unavailable"),
        ENTITY_CREATE_FAILED("spawn.entity_create_failed"),
        ENTITY_ADD_FAILED("spawn.entity_add_failed");

        private final String key;

        SpawnFailureReason(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    public record SpawnResult(
            boolean spawned,
            Optional<Mob> entity,
            Optional<String> mobId,
            Optional<String> spawnObjectId,
            Optional<SpawnFailureReason> failureReason
    ) {
        public SpawnResult {
            entity = entity == null ? Optional.empty() : entity;
            mobId = mobId == null ? Optional.empty() : mobId;
            spawnObjectId = spawnObjectId == null ? Optional.empty() : spawnObjectId;
            failureReason = failureReason == null ? Optional.empty() : failureReason;
        }

        public static SpawnResult spawned(Mob entity, String mobId, String spawnObjectId) {
            return new SpawnResult(true, Optional.of(entity), Optional.of(mobId),
                    Optional.ofNullable(spawnObjectId), Optional.empty());
        }

        public static SpawnResult failure(SpawnFailureReason reason) {
            return new SpawnResult(false, Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.ofNullable(reason));
        }

        public String failureKey() {
            return failureReason.map(SpawnFailureReason::key).orElse("");
        }
    }

    private static Mob createSupportedMob(ServerLevel level, String rawMobId) {
        String mobId = ZombiesWaveValidator.normalizedEntityId(rawMobId).orElse("");
        return switch (mobId) {
            case ZombiesWaveValidator.VANILLA_ZOMBIE_ID -> EntityType.ZOMBIE.create(level);
            case ZombiesWaveValidator.VANILLA_HUSK_ID -> EntityType.HUSK.create(level);
            default -> null;
        };
    }
}
