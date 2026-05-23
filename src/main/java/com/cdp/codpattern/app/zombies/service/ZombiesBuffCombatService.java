package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.model.DamageContext;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;
import com.cdp.codpattern.app.zombies.model.ZombiesBuffType;
import com.cdp.codpattern.app.zombies.model.ZombiesPlayerRuntimeState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Runtime combat glue for player-owned zombies buffs.
 */
public final class ZombiesBuffCombatService {
    public static final long DEFAULT_REACTIVE_EXPLOSION_COOLDOWN_TICKS = 100L;
    public static final double DEFAULT_REACTIVE_EXPLOSION_RADIUS = 4.0D;
    public static final double DEFAULT_REACTIVE_EXPLOSION_DAMAGE_FRACTION = 0.15D;

    private static final ConcurrentMap<String, ZombiesBuffCombatService> SERVICES_BY_ROOM = new ConcurrentHashMap<>();

    private final RoomId roomId;
    private final ZombiesPlayerStateService playerStateService;
    private final ModeEntityOwnershipRegistry ownershipRegistry;
    private final AoeDamageExecutor aoeDamageExecutor;
    private final long reactiveExplosionCooldownTicks;
    private final ConcurrentMap<UUID, Long> lastReactiveExplosionTickByPlayer = new ConcurrentHashMap<>();

    public ZombiesBuffCombatService(
            RoomId roomId,
            ZombiesPlayerStateService playerStateService,
            ModeEntityOwnershipRegistry ownershipRegistry
    ) {
        this(
                roomId,
                playerStateService,
                ownershipRegistry,
                (AoeDamageExecutor) null,
                DEFAULT_REACTIVE_EXPLOSION_COOLDOWN_TICKS);
    }

    public ZombiesBuffCombatService(
            RoomId roomId,
            ZombiesPlayerStateService playerStateService,
            ModeEntityOwnershipRegistry ownershipRegistry,
            AoeDamageExecutor aoeDamageExecutor,
            long reactiveExplosionCooldownTicks
    ) {
        this.roomId = Objects.requireNonNull(roomId, "roomId");
        this.playerStateService = Objects.requireNonNull(playerStateService, "playerStateService");
        this.ownershipRegistry = ownershipRegistry == null ? ModeEntityOwnershipRegistry.instance() : ownershipRegistry;
        this.aoeDamageExecutor = aoeDamageExecutor == null
                ? AoeDamageExecutor.defaultExecutor(this.ownershipRegistry)
                : aoeDamageExecutor;
        this.reactiveExplosionCooldownTicks = Math.max(0L, reactiveExplosionCooldownTicks);
    }

    public ZombiesBuffCombatService(
            RoomId roomId,
            ZombiesPlayerStateService playerStateService,
            ModeEntityOwnershipRegistry ownershipRegistry,
            ExplosionHook explosionHook,
            long reactiveExplosionCooldownTicks
    ) {
        this(
                roomId,
                playerStateService,
                ownershipRegistry,
                AoeDamageExecutor.fromExplosionHook(explosionHook),
                reactiveExplosionCooldownTicks);
    }

    public static void register(ZombiesBuffCombatService service) {
        if (service != null) {
            SERVICES_BY_ROOM.put(roomKey(service.roomId()), service);
        }
    }

    public static Optional<ZombiesBuffCombatService> serviceFor(RoomId roomId) {
        return Optional.ofNullable(roomId == null ? null : SERVICES_BY_ROOM.get(roomKey(roomId)));
    }

    public RoomId roomId() {
        return roomId;
    }

    public DamageApplicationResult applyPlayerDamage(ServerPlayer victim, DamageContext context, long currentTick) {
        if (victim == null || context == null || !isPositiveFinite(context.amount())) {
            return DamageApplicationResult.notRoomMonsterDamage(context == null ? 0.0F : context.amount());
        }
        if (sameRoomMonsterAttacker(context).isEmpty()) {
            return DamageApplicationResult.notRoomMonsterDamage(context.amount());
        }
        return applyRoomMonsterDamage(victim.getUUID(), context.amount(), currentTick, victim);
    }

    public DamageApplicationResult applyRoomMonsterDamage(UUID playerId, float amount, long currentTick) {
        return applyRoomMonsterDamage(playerId, amount, currentTick, null);
    }

    public DamageApplicationResult applyRoomMonsterDamage(
            UUID playerId,
            float amount,
            long currentTick,
            ServerPlayer triggerPlayer
    ) {
        if (playerId == null || !isPositiveFinite(amount)) {
            return DamageApplicationResult.roomMonsterDamage(amount, amount, 1.0D, ExplosionResult.notRequested());
        }
        double multiplier = damageTakenMultiplier(playerId);
        float adjustedAmount = scaledDamageAmount(amount, multiplier);
        ExplosionResult explosionResult = maybeRequestReactiveExplosion(playerId, adjustedAmount, currentTick, triggerPlayer);
        return DamageApplicationResult.roomMonsterDamage(amount, adjustedAmount, multiplier, explosionResult);
    }

    public double damageTakenMultiplier(UUID playerId) {
        return playerStateService.get(playerId)
                .map(ZombiesBuffService::damageTakenMultiplier)
                .orElse(1.0D);
    }

    public double headshotDamageMultiplier(UUID playerId) {
        return playerStateService.get(playerId)
                .flatMap(state -> state.buff(ZombiesBuffType.HEADSHOT_DAMAGE))
                .map(buff -> buff.multiplier())
                .filter(multiplier -> Double.isFinite(multiplier) && multiplier > 0.0D)
                .orElse(1.0D);
    }

    public float applyHeadshotDamageMultiplier(ServerPlayer attacker, Entity hurtEntity, float currentMultiplier) {
        if (attacker == null || hurtEntity == null || !playerStateService.canInteract(attacker.getUUID())) {
            return currentMultiplier;
        }
        if (sameRoomMonster(hurtEntity).isEmpty()) {
            return currentMultiplier;
        }
        return scaledHeadshotMultiplier(currentMultiplier, headshotDamageMultiplier(attacker.getUUID()));
    }

    public Optional<LivingEntity> sameRoomMonsterAttacker(DamageContext context) {
        if (context == null) {
            return Optional.empty();
        }
        LivingEntity sourceEntity = sameRoomMonster(context.source() == null ? null : context.source().getEntity())
                .orElse(null);
        if (sourceEntity != null) {
            return Optional.of(sourceEntity);
        }
        return sameRoomMonster(context.directEntity());
    }

    public boolean isSameRoomMonster(Entity entity) {
        return sameRoomMonster(entity).isPresent();
    }

    private Optional<LivingEntity> sameRoomMonster(Entity entity) {
        if (entity == null || entity instanceof ServerPlayer) {
            return Optional.empty();
        }
        if (entity instanceof Projectile projectile) {
            return sameRoomMonster(projectile.getOwner());
        }
        if (!(entity instanceof LivingEntity livingEntity)) {
            return Optional.empty();
        }
        return ownershipRegistry.entryOf(livingEntity)
                .map(ModeEntityOwnershipRegistry.Entry::roomId)
                .filter(this::sameRoom)
                .map(ignored -> livingEntity);
    }

    private ExplosionResult maybeRequestReactiveExplosion(
            UUID playerId,
            float adjustedAmount,
            long currentTick,
            ServerPlayer triggerPlayer
    ) {
        if (playerId == null || !isPositiveFinite(adjustedAmount)) {
            return ExplosionResult.notRequested();
        }
        ZombiesPlayerRuntimeState state = playerStateService.get(playerId).orElse(null);
        if (state == null || !state.hasBuff(ZombiesBuffType.REACTIVE_EXPLOSION)) {
            return ExplosionResult.notRequested();
        }
        Long lastTriggerTick = lastReactiveExplosionTickByPlayer.get(playerId);
        if (lastTriggerTick != null && Math.max(0L, currentTick - lastTriggerTick) < reactiveExplosionCooldownTicks) {
            return ExplosionResult.cooldown(roomId, playerId, currentTick);
        }

        lastReactiveExplosionTickByPlayer.put(playerId, Math.max(0L, currentTick));
        ExplosionRequest request = new ExplosionRequest(
                roomId,
                playerId,
                Math.max(0L, currentTick),
                DEFAULT_REACTIVE_EXPLOSION_RADIUS,
                DEFAULT_REACTIVE_EXPLOSION_DAMAGE_FRACTION);
        try {
            ExplosionResult result = aoeDamageExecutor.applyReactiveExplosion(triggerPlayer, request);
            return result == null ? ExplosionResult.failed(request, "null_executor_result") : result;
        } catch (RuntimeException exception) {
            return ExplosionResult.failed(request, exception.getClass().getSimpleName());
        }
    }

    private boolean sameRoom(RoomId other) {
        return sameRoom(other, roomId);
    }

    private static boolean sameRoom(RoomId left, RoomId right) {
        return left != null
                && right != null
                && GameModeRegistry.canonicalize(left.gameType()).equals(GameModeRegistry.canonicalize(right.gameType()))
                && left.mapName().equals(right.mapName());
    }

    private static String roomKey(RoomId roomId) {
        return GameModeRegistry.canonicalize(roomId.gameType()) + "|" + roomId.mapName();
    }

    static float scaledDamageAmount(float amount, double multiplier) {
        if (!isPositiveFinite(amount) || !Double.isFinite(multiplier) || multiplier <= 0.0D || multiplier == 1.0D) {
            return amount;
        }
        double scaled = amount * multiplier;
        if (!Double.isFinite(scaled)) {
            return amount;
        }
        return scaled >= Float.MAX_VALUE ? Float.MAX_VALUE : (float) scaled;
    }

    static float scaledHeadshotMultiplier(float currentMultiplier, double buffMultiplier) {
        if (!isPositiveFinite(currentMultiplier)
                || !Double.isFinite(buffMultiplier)
                || buffMultiplier <= 0.0D
                || buffMultiplier == 1.0D) {
            return currentMultiplier;
        }
        double scaled = currentMultiplier * buffMultiplier;
        if (!Double.isFinite(scaled)) {
            return currentMultiplier;
        }
        return scaled >= Float.MAX_VALUE ? Float.MAX_VALUE : (float) scaled;
    }

    private static boolean isPositiveFinite(float amount) {
        return Float.isFinite(amount) && amount > 0.0F;
    }

    @FunctionalInterface
    public interface ExplosionHook {
        ExplosionResult requestExplosion(ExplosionRequest request);

        static ExplosionHook notImplemented() {
            return ExplosionResult::notImplemented;
        }
    }

    @FunctionalInterface
    public interface AoeDamageExecutor {
        ExplosionResult applyReactiveExplosion(ServerPlayer triggerPlayer, ExplosionRequest request);

        static AoeDamageExecutor defaultExecutor(ModeEntityOwnershipRegistry ownershipRegistry) {
            return new DefaultAoeDamageExecutor(ownershipRegistry);
        }

        static AoeDamageExecutor fromExplosionHook(ExplosionHook explosionHook) {
            ExplosionHook safeHook = explosionHook == null ? ExplosionHook.notImplemented() : explosionHook;
            return (triggerPlayer, request) -> safeHook.requestExplosion(request);
        }
    }

    private static final class DefaultAoeDamageExecutor implements AoeDamageExecutor {
        private final ModeEntityOwnershipRegistry ownershipRegistry;
        private final ZombiesWeaponItemStackService weaponItemStackService = new ZombiesWeaponItemStackService();

        private DefaultAoeDamageExecutor(ModeEntityOwnershipRegistry ownershipRegistry) {
            this.ownershipRegistry = ownershipRegistry == null ? ModeEntityOwnershipRegistry.instance() : ownershipRegistry;
        }

        @Override
        public ExplosionResult applyReactiveExplosion(ServerPlayer triggerPlayer, ExplosionRequest request) {
            if (request == null) {
                return ExplosionResult.notRequested();
            }
            if (triggerPlayer == null || triggerPlayer.level().isClientSide) {
                return ExplosionResult.failed(request, "missing_server_player");
            }

            ServerLevel level = triggerPlayer.serverLevel();
            Vec3 center = triggerPlayer.position();
            double radius = request.radius();
            double radiusSqr = radius * radius;
            AABB searchBox = triggerPlayer.getBoundingBox().inflate(radius);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, searchBox,
                    entity -> isEligibleTarget(entity, level, center, radiusSqr, request.roomId()))) {
                float desiredDamage = scaledDamageAmount(target.getMaxHealth(), request.damageMaxHealthFraction());
                if (!isPositiveFinite(desiredDamage)) {
                    continue;
                }
                target.hurt(
                        triggerPlayer.damageSources().playerAttack(triggerPlayer),
                        compensateEntityDamageMultiplier(triggerPlayer, request.roomId(), desiredDamage));
            }
            return ExplosionResult.applied(request);
        }

        private boolean isEligibleTarget(
                LivingEntity entity,
                ServerLevel level,
                Vec3 center,
                double radiusSqr,
                RoomId roomId
        ) {
            if (entity == null || entity instanceof Player || !entity.isAlive()) {
                return false;
            }
            if (!entity.level().dimension().equals(level.dimension())) {
                return false;
            }
            if (entity.distanceToSqr(center) > radiusSqr) {
                return false;
            }
            return ownershipRegistry.entryOf(entity)
                    .filter(entry -> entry.dimension().equals(level.dimension()))
                    .map(ModeEntityOwnershipRegistry.Entry::roomId)
                    .filter(other -> sameRoom(other, roomId))
                    .isPresent();
        }

        private float compensateEntityDamageMultiplier(ServerPlayer triggerPlayer, RoomId roomId, float desiredDamage) {
            if (triggerPlayer == null || !isPositiveFinite(desiredDamage)) {
                return desiredDamage;
            }
            double multiplier = weaponItemStackService.sameRoomDamageMultiplier(triggerPlayer.getMainHandItem(), roomId);
            if (!Double.isFinite(multiplier) || multiplier <= 0.0D || multiplier == 1.0D) {
                return desiredDamage;
            }
            double compensated = desiredDamage / multiplier;
            if (!Double.isFinite(compensated) || compensated <= 0.0D) {
                return desiredDamage;
            }
            return compensated >= Float.MAX_VALUE ? Float.MAX_VALUE : (float) compensated;
        }
    }

    public record ExplosionRequest(
            RoomId roomId,
            UUID playerId,
            long triggerTick,
            double radius,
            double damageMaxHealthFraction
    ) {
        public ExplosionRequest {
            Objects.requireNonNull(roomId, "roomId");
            Objects.requireNonNull(playerId, "playerId");
            triggerTick = Math.max(0L, triggerTick);
            radius = Double.isFinite(radius) && radius > 0.0D ? radius : DEFAULT_REACTIVE_EXPLOSION_RADIUS;
            damageMaxHealthFraction = Double.isFinite(damageMaxHealthFraction) && damageMaxHealthFraction > 0.0D
                    ? damageMaxHealthFraction
                    : DEFAULT_REACTIVE_EXPLOSION_DAMAGE_FRACTION;
        }
    }

    public record ExplosionResult(
            boolean requested,
            boolean aoeApplied,
            RoomId roomId,
            UUID playerId,
            long triggerTick,
            String status
    ) {
        private static final String STATUS_NOT_REQUESTED = "not_requested";
        private static final String STATUS_COOLDOWN = "cooldown";
        private static final String STATUS_AOE_NOT_IMPLEMENTED = "aoe_not_implemented";
        private static final String STATUS_APPLIED = "applied";
        private static final String STATUS_FAILED = "failed";

        public ExplosionResult {
            triggerTick = Math.max(0L, triggerTick);
            status = status == null || status.isBlank() ? STATUS_NOT_REQUESTED : status;
        }

        public static ExplosionResult notRequested() {
            return new ExplosionResult(false, false, null, null, 0L, STATUS_NOT_REQUESTED);
        }

        public static ExplosionResult cooldown(RoomId roomId, UUID playerId, long triggerTick) {
            return new ExplosionResult(false, false, roomId, playerId, triggerTick, STATUS_COOLDOWN);
        }

        public static ExplosionResult notImplemented(ExplosionRequest request) {
            return new ExplosionResult(
                    true,
                    false,
                    request.roomId(),
                    request.playerId(),
                    request.triggerTick(),
                    STATUS_AOE_NOT_IMPLEMENTED);
        }

        public static ExplosionResult applied(ExplosionRequest request) {
            return new ExplosionResult(
                    true,
                    true,
                    request.roomId(),
                    request.playerId(),
                    request.triggerTick(),
                    STATUS_APPLIED);
        }

        public static ExplosionResult failed(ExplosionRequest request, String detail) {
            String suffix = detail == null || detail.isBlank() ? "" : ":" + detail;
            return new ExplosionResult(
                    true,
                    false,
                    request.roomId(),
                    request.playerId(),
                    request.triggerTick(),
                    STATUS_FAILED + suffix);
        }
    }

    public record DamageApplicationResult(
            boolean roomMonsterDamage,
            float originalAmount,
            float adjustedAmount,
            double damageTakenMultiplier,
            ExplosionResult explosionResult
    ) {
        public DamageApplicationResult {
            originalAmount = Float.isFinite(originalAmount) ? originalAmount : 0.0F;
            adjustedAmount = Float.isFinite(adjustedAmount) ? adjustedAmount : originalAmount;
            damageTakenMultiplier = Double.isFinite(damageTakenMultiplier) && damageTakenMultiplier > 0.0D
                    ? damageTakenMultiplier
                    : 1.0D;
            explosionResult = explosionResult == null ? ExplosionResult.notRequested() : explosionResult;
        }

        public boolean amountChanged() {
            return Float.compare(originalAmount, adjustedAmount) != 0;
        }

        public static DamageApplicationResult notRoomMonsterDamage(float amount) {
            return new DamageApplicationResult(false, amount, amount, 1.0D, ExplosionResult.notRequested());
        }

        public static DamageApplicationResult roomMonsterDamage(
                float originalAmount,
                float adjustedAmount,
                double damageTakenMultiplier,
                ExplosionResult explosionResult
        ) {
            return new DamageApplicationResult(true, originalAmount, adjustedAmount, damageTakenMultiplier, explosionResult);
        }
    }
}
