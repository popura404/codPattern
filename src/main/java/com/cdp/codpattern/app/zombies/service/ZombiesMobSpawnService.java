package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;
import com.cdp.codpattern.app.zombies.map.ZombiesMapObjects;
import com.cdp.codpattern.app.zombies.map.object.ZombiesZombieSpawnData;
import com.cdp.codpattern.app.zombies.model.ZombiesWaveDefinition;
import com.cdp.codpattern.app.zombies.model.ZombiesWaveMobEntry;
import com.cdp.codpattern.app.zombies.runtime.ZombiesWaveRuntimeState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class ZombiesMobSpawnService {
    public static final int DEFAULT_GLOBAL_MAX_ALIVE_ZOMBIES = 64;
    public static final String WAVE_MOB_ID_TAG = "codpattern_zombies_wave_mob_id";
    public static final String WAVE_KILL_POINTS_TAG = "codpattern_zombies_wave_kill_points";
    public static final String WAVE_ASSIST_POINTS_TAG = "codpattern_zombies_wave_assist_points";
    static final int ROOM_MONSTER_MELEE_ATTACK_INTERVAL_TICKS = 10;
    static final double ROOM_MONSTER_FOLLOW_RANGE = 128.0D;
    static final int ROOM_MONSTER_TARGET_REFRESH_INTERVAL_TICKS = 20;

    private static final AtomicInteger GLOBAL_ACTIVE_ZOMBIES = new AtomicInteger();

    private final ModeEntityOwnershipRegistry ownershipRegistry;
    private final int globalMaxAliveZombies;
    private final Supplier<List<ServerPlayer>> survivorTargetSupplier;
    private final boolean roomTargetingEnabled;

    public ZombiesMobSpawnService() {
        this(ModeEntityOwnershipRegistry.instance(), DEFAULT_GLOBAL_MAX_ALIVE_ZOMBIES);
    }

    public ZombiesMobSpawnService(ModeEntityOwnershipRegistry ownershipRegistry, int globalMaxAliveZombies) {
        this(ownershipRegistry, globalMaxAliveZombies, null);
    }

    public ZombiesMobSpawnService(
            ModeEntityOwnershipRegistry ownershipRegistry,
            int globalMaxAliveZombies,
            Supplier<List<ServerPlayer>> survivorTargetSupplier
    ) {
        this.ownershipRegistry = Objects.requireNonNull(ownershipRegistry, "ownershipRegistry");
        this.globalMaxAliveZombies = Math.max(1, globalMaxAliveZombies);
        this.survivorTargetSupplier = survivorTargetSupplier == null ? List::of : survivorTargetSupplier;
        this.roomTargetingEnabled = survivorTargetSupplier != null;
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
        applyRoomMonsterRetention(mob);
        applyRoomMonsterAttackCadence(mob);
        applyRoomMonsterTargeting(mob, survivorTargetSupplier, roomTargetingEnabled);
        attachWaveRewardMetadata(mob, mobId.get(), waveDefinition);

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

    private static void applyRoomMonsterAttackCadence(Mob mob) {
        if (mob instanceof PathfinderMob pathfinderMob) {
            pathfinderMob.goalSelector.addGoal(
                    1,
                    new RoomMonsterMeleeAttackGoal(pathfinderMob, 1.0D, false));
        }
    }

    private static void applyRoomMonsterRetention(Mob mob) {
        if (mob == null) {
            return;
        }
        AttributeInstance followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null && followRange.getBaseValue() < ROOM_MONSTER_FOLLOW_RANGE) {
            followRange.setBaseValue(ROOM_MONSTER_FOLLOW_RANGE);
        }
        mob.setPersistenceRequired();
    }

    private static void applyRoomMonsterTargeting(
            Mob mob,
            Supplier<List<ServerPlayer>> targetSupplier,
            boolean enabled
    ) {
        if (!enabled || !(mob instanceof PathfinderMob pathfinderMob)) {
            return;
        }
        pathfinderMob.targetSelector.addGoal(
                0,
                new RoomSurvivorTargetGoal(pathfinderMob, targetSupplier));
    }

    private static void attachWaveRewardMetadata(Mob mob, String rawMobId, ZombiesWaveDefinition waveDefinition) {
        if (mob == null || waveDefinition == null) {
            return;
        }
        String normalizedMobId = ZombiesWaveValidator.normalizedEntityId(rawMobId).orElse("");
        if (normalizedMobId.isBlank()) {
            return;
        }
        mob.getPersistentData().putString(WAVE_MOB_ID_TAG, normalizedMobId);
        matchingMobEntry(waveDefinition, normalizedMobId).ifPresent(entry -> {
            mob.getPersistentData().putDouble(WAVE_KILL_POINTS_TAG, entry.getKillPoints());
            mob.getPersistentData().putDouble(WAVE_ASSIST_POINTS_TAG, entry.getAssistPoints());
        });
    }

    private static Optional<ZombiesWaveMobEntry> matchingMobEntry(ZombiesWaveDefinition waveDefinition, String normalizedMobId) {
        if (waveDefinition == null || normalizedMobId == null || normalizedMobId.isBlank()) {
            return Optional.empty();
        }
        return waveDefinition.getMobs().stream()
                .filter(Objects::nonNull)
                .filter(entry -> entry.getCount() > 0)
                .filter(entry -> ZombiesWaveValidator.normalizedEntityId(entry.getEntity())
                        .map(normalizedMobId::equals)
                        .orElse(false))
                .findFirst();
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

    private static final class RoomMonsterMeleeAttackGoal extends MeleeAttackGoal {
        private RoomMonsterMeleeAttackGoal(PathfinderMob mob, double speedModifier, boolean followingTargetEvenIfNotSeen) {
            super(mob, speedModifier, followingTargetEvenIfNotSeen);
        }

        @Override
        protected int getAttackInterval() {
            return ROOM_MONSTER_MELEE_ATTACK_INTERVAL_TICKS;
        }
    }

    private static final class RoomSurvivorTargetGoal extends Goal {
        private final PathfinderMob mob;
        private final Supplier<List<ServerPlayer>> targetSupplier;
        private int nextScanDelay;
        private UUID currentRoomTargetId;

        private RoomSurvivorTargetGoal(PathfinderMob mob, Supplier<List<ServerPlayer>> targetSupplier) {
            this.mob = Objects.requireNonNull(mob, "mob");
            this.targetSupplier = targetSupplier == null ? List::of : targetSupplier;
            this.nextScanDelay = staggeredInitialDelay(mob);
            setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            return mob.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return mob.isAlive() && !mob.isRemoved();
        }

        @Override
        public void start() {
            refreshTarget();
        }

        @Override
        public void tick() {
            if (nextScanDelay > 0) {
                nextScanDelay--;
                if (!isCurrentRoomTarget(mob.getTarget())) {
                    mob.setTarget(null);
                }
                return;
            }
            refreshTarget();
            nextScanDelay = ROOM_MONSTER_TARGET_REFRESH_INTERVAL_TICKS;
        }

        @Override
        public void stop() {
            if (!isCurrentRoomTarget(mob.getTarget())) {
                mob.setTarget(null);
            }
        }

        private void refreshTarget() {
            List<ServerPlayer> targets = safeTargets();
            LivingEntity currentTarget = mob.getTarget();
            if (isCurrentRoomTarget(currentTarget) && containsCurrentTarget(targets)) {
                return;
            }
            Optional<ServerPlayer> nextTarget = nearestRoomSurvivor(targets);
            currentRoomTargetId = nextTarget.map(ServerPlayer::getUUID).orElse(null);
            mob.setTarget(nextTarget.orElse(null));
        }

        private Optional<ServerPlayer> nearestRoomSurvivor(List<ServerPlayer> targets) {
            return targets.stream()
                    .filter(this::isEligibleRoomSurvivor)
                    .min(Comparator.comparingDouble(mob::distanceToSqr));
        }

        private boolean isCurrentRoomTarget(LivingEntity target) {
            if (!(target instanceof ServerPlayer player)) {
                return false;
            }
            return currentRoomTargetId != null
                    && currentRoomTargetId.equals(player.getUUID())
                    && isEligibleRoomSurvivor(player);
        }

        private boolean isEligibleRoomSurvivor(ServerPlayer player) {
            if (player == null || !player.isAlive() || player.isSpectator()) {
                return false;
            }
            if (!player.level().dimension().equals(mob.level().dimension())) {
                return false;
            }
            double followRange = Math.max(ROOM_MONSTER_FOLLOW_RANGE, currentFollowRange(mob));
            return mob.distanceToSqr(player) <= followRange * followRange;
        }

        private boolean containsCurrentTarget(List<ServerPlayer> targets) {
            if (currentRoomTargetId == null) {
                return false;
            }
            return targets.stream()
                    .filter(Objects::nonNull)
                    .anyMatch(candidate -> currentRoomTargetId.equals(candidate.getUUID()));
        }

        private List<ServerPlayer> safeTargets() {
            try {
                List<ServerPlayer> targets = targetSupplier.get();
                return targets == null ? List.of() : targets;
            } catch (RuntimeException ignored) {
                return List.of();
            }
        }

        private static double currentFollowRange(Mob mob) {
            AttributeInstance followRange = mob == null ? null : mob.getAttribute(Attributes.FOLLOW_RANGE);
            return followRange == null ? ROOM_MONSTER_FOLLOW_RANGE : followRange.getValue();
        }

        private static int staggeredInitialDelay(Mob mob) {
            int entityId = mob == null ? 0 : mob.getId();
            return Math.floorMod(entityId, ROOM_MONSTER_TARGET_REFRESH_INTERVAL_TICKS);
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
