package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;
import com.cdp.codpattern.app.zombies.map.ZombiesMapObjects;
import com.cdp.codpattern.app.zombies.map.object.ZombiesZombieSpawnData;
import com.cdp.codpattern.app.zombies.model.ZombiesWaveDefinition;
import com.cdp.codpattern.app.zombies.model.ZombiesWaveMobEntry;
import com.cdp.codpattern.app.zombies.runtime.ZombiesWaveRuntimeState;
import com.cdp.codpattern.config.zombies.ZombiesRulesConfig;
import com.cdp.codpattern.config.zombies.ZombiesRulesRepository;
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
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
    public static final String WAVE_RECYCLE_COUNT_TAG = "codpattern_zombies_wave_recycle_count";
    static final int ROOM_MONSTER_MELEE_ATTACK_INTERVAL_TICKS = 10;
    static final double ROOM_MONSTER_FOLLOW_RANGE = 128.0D;
    static final int ROOM_MONSTER_TARGET_REFRESH_INTERVAL_TICKS = 20;
    static final int ROOM_MONSTER_WOLF_ANGER_TICKS = 20 * 60 * 60;
    static final float ROOM_MONSTER_MAX_UP_STEP = 1.25F;
    static final int ROOM_MONSTER_OBSTACLE_JUMP_COOLDOWN_TICKS = 8;
    static final double ROOM_MONSTER_OBSTACLE_PROBE_DISTANCE = 0.8D;
    static final double ROOM_MONSTER_OBSTACLE_PROBE_INFLATE = 0.05D;
    static final double ROOM_MONSTER_OBSTACLE_CLEARANCE_HEIGHT = 1.25D;
    static final double ROOM_MONSTER_DROP_DOWN_MIN_HEIGHT = 2.0D;
    static final double ROOM_MONSTER_DROP_DOWN_PROBE_DISTANCE = 0.9D;
    static final double ROOM_MONSTER_DROP_DOWN_FORWARD_SPEED = 0.28D;
    static final double ROOM_MONSTER_DROP_DOWN_JUMP_SPEED = 0.25D;

    private static final AtomicInteger GLOBAL_ACTIVE_ZOMBIES = new AtomicInteger();

    private final ModeEntityOwnershipRegistry ownershipRegistry;
    private final int globalMaxAliveZombies;
    private final Supplier<List<ServerPlayer>> survivorTargetSupplier;
    private final Supplier<ZombiesRulesConfig.SpawnPointWeighting> spawnPointWeightingSupplier;
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
        this(
                ownershipRegistry,
                globalMaxAliveZombies,
                survivorTargetSupplier,
                () -> ZombiesRulesRepository.getConfig().getSpawnPointWeighting());
    }

    public ZombiesMobSpawnService(
            ModeEntityOwnershipRegistry ownershipRegistry,
            int globalMaxAliveZombies,
            Supplier<List<ServerPlayer>> survivorTargetSupplier,
            Supplier<ZombiesRulesConfig.SpawnPointWeighting> spawnPointWeightingSupplier
    ) {
        this.ownershipRegistry = Objects.requireNonNull(ownershipRegistry, "ownershipRegistry");
        this.globalMaxAliveZombies = Math.max(1, globalMaxAliveZombies);
        this.survivorTargetSupplier = survivorTargetSupplier == null ? List::of : survivorTargetSupplier;
        this.spawnPointWeightingSupplier = spawnPointWeightingSupplier == null
                ? () -> ZombiesRulesRepository.getConfig().getSpawnPointWeighting()
                : spawnPointWeightingSupplier;
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

        ZombiesZombieSpawnData spawn = chooseSpawn(
                level,
                loadedCandidates,
                survivorTargets(level),
                spawnPointWeighting());
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
        applySpawnedMobSpecialRules(mob);
        applyWaveAttributes(mob, waveDefinition);
        applyRoomMonsterRetention(mob);
        applyRoomMonsterObstacleJumping(mob);
        applyRoomMonsterAttackCadence(mob);
        applyRoomMonsterTargeting(mob, survivorTargetSupplier, roomTargetingEnabled);
        attachWaveRewardMetadata(mob, mobId.get(), waveDefinition);
        attachRecycleCountMetadata(mob, mobId.get(), waveState);

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

    private ZombiesRulesConfig.SpawnPointWeighting spawnPointWeighting() {
        ZombiesRulesConfig.SpawnPointWeighting weighting = spawnPointWeightingSupplier.get();
        return weighting == null ? new ZombiesRulesConfig.SpawnPointWeighting() : weighting;
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

    private List<ServerPlayer> survivorTargets(ServerLevel level) {
        if (level == null) {
            return List.of();
        }
        try {
            List<ServerPlayer> targets = survivorTargetSupplier.get();
            if (targets == null || targets.isEmpty()) {
                return List.of();
            }
            return targets.stream()
                    .filter(Objects::nonNull)
                    .filter(ServerPlayer::isAlive)
                    .filter(player -> !player.isSpectator())
                    .filter(player -> player.level().dimension().equals(level.dimension()))
                    .toList();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private static ZombiesZombieSpawnData chooseSpawn(
            ServerLevel level,
            List<ZombiesZombieSpawnData> candidates,
            List<ServerPlayer> survivorTargets,
            ZombiesRulesConfig.SpawnPointWeighting weighting
    ) {
        ServerPlayer pressureTarget = choosePressureTarget(level, survivorTargets);
        double totalWeight = candidates.stream()
                .mapToDouble(spawn -> effectiveSpawnWeight(spawn, pressureTarget, survivorTargets, weighting))
                .sum();
        if (totalWeight <= 0.0D) {
            return candidates.get(0);
        }
        double selected = level.random.nextDouble() * totalWeight;
        double cursor = 0.0D;
        for (ZombiesZombieSpawnData candidate : candidates) {
            cursor += effectiveSpawnWeight(candidate, pressureTarget, survivorTargets, weighting);
            if (selected <= cursor) {
                return candidate;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    private static ServerPlayer choosePressureTarget(ServerLevel level, List<ServerPlayer> survivorTargets) {
        if (level == null || survivorTargets == null || survivorTargets.isEmpty()) {
            return null;
        }
        return survivorTargets.get(level.random.nextInt(survivorTargets.size()));
    }

    private static double effectiveSpawnWeight(
            ZombiesZombieSpawnData spawn,
            ServerPlayer pressureTarget,
            List<ServerPlayer> survivorTargets,
            ZombiesRulesConfig.SpawnPointWeighting weighting
    ) {
        if (spawn == null || spawn.weight() <= 0.0D) {
            return 0.0D;
        }
        double targetDistance = pressureTarget == null ? Double.NaN : distanceToSpawn(spawn, pressureTarget);
        double nearestDistance = nearestDistanceToSpawn(spawn, survivorTargets);
        return effectiveSpawnWeight(spawn.weight(), targetDistance, nearestDistance, weighting);
    }

    static double effectiveSpawnWeight(
            double baseWeight,
            double targetDistance,
            double nearestDistance,
            ZombiesRulesConfig.SpawnPointWeighting weighting
    ) {
        if (!Double.isFinite(baseWeight) || baseWeight <= 0.0D) {
            return 0.0D;
        }
        return baseWeight * distanceMultiplier(targetDistance, nearestDistance, weighting);
    }

    static double distanceMultiplier(
            double targetDistance,
            double nearestDistance,
            ZombiesRulesConfig.SpawnPointWeighting weighting
    ) {
        if (weighting == null || !Boolean.TRUE.equals(weighting.getEnabled())) {
            return 1.0D;
        }
        double tooCloseDistance = positiveFiniteOrDefault(weighting.getTooCloseDistance(), 8.0D);
        double idealMinDistance = Math.max(
                tooCloseDistance,
                positiveFiniteOrDefault(weighting.getIdealMinDistance(), 24.0D));
        double idealMaxDistance = Math.max(
                idealMinDistance,
                positiveFiniteOrDefault(weighting.getIdealMaxDistance(), 56.0D));
        double farDistance = Math.max(
                idealMaxDistance,
                positiveFiniteOrDefault(weighting.getFarDistance(), 112.0D));
        double minMultiplier = positiveFiniteOrDefault(weighting.getMinMultiplier(), 0.65D);
        double maxMultiplier = positiveFiniteOrDefault(weighting.getMaxMultiplier(), 1.20D);
        if (minMultiplier > maxMultiplier) {
            double swappedMin = maxMultiplier;
            maxMultiplier = minMultiplier;
            minMultiplier = swappedMin;
        }
        double idealMultiplier = clamp(
                positiveFiniteOrDefault(weighting.getIdealMultiplier(), 1.15D),
                minMultiplier,
                maxMultiplier);
        double farMultiplier = clamp(
                positiveFiniteOrDefault(weighting.getFarMultiplier(), 0.85D),
                minMultiplier,
                maxMultiplier);

        double multiplier = targetDistanceMultiplier(
                targetDistance,
                tooCloseDistance,
                idealMinDistance,
                idealMaxDistance,
                farDistance,
                minMultiplier,
                idealMultiplier,
                farMultiplier);
        if (Double.isFinite(nearestDistance) && nearestDistance < tooCloseDistance) {
            multiplier = Math.min(multiplier, minMultiplier);
        }
        return clamp(multiplier, minMultiplier, maxMultiplier);
    }

    private static double targetDistanceMultiplier(
            double targetDistance,
            double tooCloseDistance,
            double idealMinDistance,
            double idealMaxDistance,
            double farDistance,
            double minMultiplier,
            double idealMultiplier,
            double farMultiplier
    ) {
        if (!Double.isFinite(targetDistance)) {
            return 1.0D;
        }
        if (targetDistance < tooCloseDistance) {
            return minMultiplier;
        }
        if (targetDistance < idealMinDistance) {
            return lerp(
                    minMultiplier,
                    idealMultiplier,
                    ratio(targetDistance, tooCloseDistance, idealMinDistance));
        }
        if (targetDistance <= idealMaxDistance) {
            return idealMultiplier;
        }
        if (targetDistance < farDistance) {
            return lerp(
                    idealMultiplier,
                    farMultiplier,
                    ratio(targetDistance, idealMaxDistance, farDistance));
        }
        return farMultiplier;
    }

    private static double nearestDistanceToSpawn(
            ZombiesZombieSpawnData spawn,
            List<ServerPlayer> survivorTargets
    ) {
        if (spawn == null || survivorTargets == null || survivorTargets.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        double nearest = Double.POSITIVE_INFINITY;
        for (ServerPlayer target : survivorTargets) {
            if (target == null) {
                continue;
            }
            nearest = Math.min(nearest, distanceToSpawn(spawn, target));
        }
        return nearest;
    }

    private static double distanceToSpawn(ZombiesZombieSpawnData spawn, ServerPlayer target) {
        if (spawn == null || target == null || spawn.pos() == null) {
            return Double.NaN;
        }
        double dx = target.getX() - (spawn.pos().getX() + 0.5D);
        double dy = target.getY() - spawn.pos().getY();
        double dz = target.getZ() - (spawn.pos().getZ() + 0.5D);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double positiveFiniteOrDefault(Double value, double defaultValue) {
        return value == null || !Double.isFinite(value) || value <= 0.0D ? defaultValue : value;
    }

    private static double ratio(double value, double min, double max) {
        if (max <= min) {
            return 1.0D;
        }
        return clamp((value - min) / (max - min), 0.0D, 1.0D);
    }

    private static double lerp(double from, double to, double ratio) {
        return from + (to - from) * clamp(ratio, 0.0D, 1.0D);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
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
        if (mob.maxUpStep() < ROOM_MONSTER_MAX_UP_STEP) {
            mob.setMaxUpStep(ROOM_MONSTER_MAX_UP_STEP);
        }
        mob.setPersistenceRequired();
    }

    private static void applyRoomMonsterObstacleJumping(Mob mob) {
        if (mob instanceof PathfinderMob pathfinderMob) {
            pathfinderMob.goalSelector.addGoal(
                    0,
                    new RoomMonsterObstacleJumpGoal(pathfinderMob));
        }
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

    private static void applySpawnedMobSpecialRules(Mob mob) {
        if (mob instanceof Wolf wolf) {
            wolf.setTame(false);
            wolf.setOrderedToSit(false);
            wolf.setRemainingPersistentAngerTime(ROOM_MONSTER_WOLF_ANGER_TICKS);
        }
    }

    private static void applyRoomTargetSpecialRules(PathfinderMob mob, ServerPlayer target) {
        if (mob instanceof Wolf wolf && target != null) {
            wolf.setTame(false);
            wolf.setOrderedToSit(false);
            wolf.setPersistentAngerTarget(target.getUUID());
            wolf.setRemainingPersistentAngerTime(ROOM_MONSTER_WOLF_ANGER_TICKS);
        }
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

    private static void attachRecycleCountMetadata(Mob mob, String mobId, ZombiesWaveRuntimeState waveState) {
        if (mob == null || waveState == null || mobId == null || mobId.isBlank()) {
            return;
        }
        mob.getPersistentData().putInt(WAVE_RECYCLE_COUNT_TAG, waveState.consumeRequeuedRecycleCount(mobId));
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

    private static final class RoomMonsterObstacleJumpGoal extends Goal {
        private final PathfinderMob mob;
        private int cooldownTicks;
        private Vec3 pendingJumpImpulse;

        private RoomMonsterObstacleJumpGoal(PathfinderMob mob) {
            this.mob = Objects.requireNonNull(mob, "mob");
            setFlags(EnumSet.of(Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (cooldownTicks > 0) {
                cooldownTicks--;
                return false;
            }
            pendingJumpImpulse = null;
            LivingEntity target = mob.getTarget();
            if (target == null
                    || !target.isAlive()
                    || !mob.onGround()
                    || mob.isInWaterOrBubble()
                    || mob.isInLava()
                    || mob.distanceToSqr(target) <= 2.25D) {
                return false;
            }
            if (hasLowFrontObstacle(target)) {
                return true;
            }
            pendingJumpImpulse = dropDownImpulse(target);
            return pendingJumpImpulse != null;
        }

        @Override
        public void start() {
            mob.getJumpControl().jump();
            if (pendingJumpImpulse != null) {
                Vec3 currentMovement = mob.getDeltaMovement();
                mob.setDeltaMovement(
                        currentMovement.x + pendingJumpImpulse.x,
                        Math.max(currentMovement.y, ROOM_MONSTER_DROP_DOWN_JUMP_SPEED),
                        currentMovement.z + pendingJumpImpulse.z);
                pendingJumpImpulse = null;
            }
            cooldownTicks = ROOM_MONSTER_OBSTACLE_JUMP_COOLDOWN_TICKS;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        private Vec3 dropDownImpulse(LivingEntity target) {
            if (mob.getY() - target.getY() < ROOM_MONSTER_DROP_DOWN_MIN_HEIGHT) {
                return null;
            }
            Vec3 direction = directionToTarget(target);
            if (direction.horizontalDistanceSqr() < 0.0001D) {
                return null;
            }
            AABB frontBox = mob.getBoundingBox()
                    .move(
                            direction.x * ROOM_MONSTER_DROP_DOWN_PROBE_DISTANCE,
                            0.0D,
                            direction.z * ROOM_MONSTER_DROP_DOWN_PROBE_DISTANCE)
                    .inflate(ROOM_MONSTER_OBSTACLE_PROBE_INFLATE, 0.0D, ROOM_MONSTER_OBSTACLE_PROBE_INFLATE);
            if (!mob.level().noCollision(mob, frontBox)) {
                return null;
            }
            AABB dropBox = frontBox.move(0.0D, -1.0D, 0.0D);
            if (!mob.level().noCollision(mob, dropBox)) {
                return null;
            }
            return new Vec3(
                    direction.x * ROOM_MONSTER_DROP_DOWN_FORWARD_SPEED,
                    0.0D,
                    direction.z * ROOM_MONSTER_DROP_DOWN_FORWARD_SPEED);
        }

        private boolean hasLowFrontObstacle(LivingEntity target) {
            Vec3 direction = directionToTarget(target);
            if (direction.horizontalDistanceSqr() < 0.0001D) {
                return false;
            }
            AABB probeBox = mob.getBoundingBox()
                    .move(
                            direction.x * ROOM_MONSTER_OBSTACLE_PROBE_DISTANCE,
                            0.0D,
                            direction.z * ROOM_MONSTER_OBSTACLE_PROBE_DISTANCE)
                    .inflate(ROOM_MONSTER_OBSTACLE_PROBE_INFLATE, 0.0D, ROOM_MONSTER_OBSTACLE_PROBE_INFLATE);
            if (mob.level().noCollision(mob, probeBox)) {
                return false;
            }
            AABB clearanceBox = probeBox.move(0.0D, ROOM_MONSTER_OBSTACLE_CLEARANCE_HEIGHT, 0.0D);
            return mob.level().noCollision(mob, clearanceBox);
        }

        private Vec3 directionToTarget(LivingEntity target) {
            double dx = target.getX() - mob.getX();
            double dz = target.getZ() - mob.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > 0.001D) {
                return new Vec3(dx / distance, 0.0D, dz / distance);
            }
            double yawRadians = Math.toRadians(mob.getYRot());
            return new Vec3(-Math.sin(yawRadians), 0.0D, Math.cos(yawRadians));
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
            nextTarget.ifPresent(target -> applyRoomTargetSpecialRules(mob, target));
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
            case ZombiesWaveValidator.VANILLA_WITHER_SKELETON_ID -> EntityType.WITHER_SKELETON.create(level);
            case ZombiesWaveValidator.VANILLA_CREEPER_ID -> EntityType.CREEPER.create(level);
            case ZombiesWaveValidator.VANILLA_WOLF_ID -> EntityType.WOLF.create(level);
            case ZombiesWaveValidator.VANILLA_SILVERFISH_ID -> EntityType.SILVERFISH.create(level);
            default -> null;
        };
    }
}
