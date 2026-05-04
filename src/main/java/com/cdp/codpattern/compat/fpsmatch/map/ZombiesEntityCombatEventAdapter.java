package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.model.DamageDecision;
import com.cdp.codpattern.app.match.model.DeathDecision;
import com.cdp.codpattern.app.match.model.EntityDamageContext;
import com.cdp.codpattern.app.match.model.EntityDeathContext;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.port.ModeEntityCombatEventPort;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;
import com.cdp.codpattern.app.zombies.service.ZombiesEconomyService;
import com.cdp.codpattern.app.zombies.service.ZombiesPlayerStateService;
import com.cdp.codpattern.app.zombies.service.ZombiesWeaponItemStackService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ZombiesEntityCombatEventAdapter implements ModeEntityCombatEventPort {
    private final RoomId roomId;
    private final String modeDisplayNameKey;
    private final ZombiesEconomyService economyService;
    private final ZombiesPlayerStateService playerStateService;
    private final ModeEntityOwnershipRegistry ownershipRegistry;
    private final RewardResolver rewardResolver;
    private final LifecycleHook lifecycleHook;
    private final ZombiesWeaponItemStackService weaponItemStackService;
    private final ConcurrentMap<UUID, Set<UUID>> contributorsByEntity = new ConcurrentHashMap<>();

    public ZombiesEntityCombatEventAdapter(
            RoomId roomId,
            String modeDisplayNameKey,
            ZombiesEconomyService economyService,
            ZombiesPlayerStateService playerStateService,
            ModeEntityOwnershipRegistry ownershipRegistry,
            RewardResolver rewardResolver,
            LifecycleHook lifecycleHook
    ) {
        this(
                roomId,
                modeDisplayNameKey,
                economyService,
                playerStateService,
                ownershipRegistry,
                rewardResolver,
                lifecycleHook,
                new ZombiesWeaponItemStackService());
    }

    public ZombiesEntityCombatEventAdapter(
            RoomId roomId,
            String modeDisplayNameKey,
            ZombiesEconomyService economyService,
            ZombiesPlayerStateService playerStateService,
            ModeEntityOwnershipRegistry ownershipRegistry,
            RewardResolver rewardResolver,
            LifecycleHook lifecycleHook,
            ZombiesWeaponItemStackService weaponItemStackService
    ) {
        this.roomId = Objects.requireNonNull(roomId, "roomId");
        this.modeDisplayNameKey = modeDisplayNameKey == null || modeDisplayNameKey.isBlank()
                ? GameModeRegistry.getOrDefault(roomId.gameType()).displayNameKey()
                : modeDisplayNameKey;
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.playerStateService = Objects.requireNonNull(playerStateService, "playerStateService");
        this.ownershipRegistry = ownershipRegistry == null ? ModeEntityOwnershipRegistry.instance() : ownershipRegistry;
        this.rewardResolver = rewardResolver == null ? RewardResolver.defaults() : rewardResolver;
        this.lifecycleHook = lifecycleHook == null ? (entity, reason) -> { } : lifecycleHook;
        this.weaponItemStackService = Objects.requireNonNull(weaponItemStackService, "weaponItemStackService");
    }

    @Override
    public RoomId roomId() {
        return roomId;
    }

    @Override
    public String gameType() {
        return GameModeRegistry.canonicalize(roomId.gameType());
    }

    @Override
    public String mapName() {
        return roomId.mapName();
    }

    @Override
    public String modeDisplayNameKey() {
        return modeDisplayNameKey;
    }

    @Override
    public DamageDecision onEntityHurt(LivingEntity entity, EntityDamageContext context) {
        if (entity == null || context == null || !isOwnedByThisRoom(entity) || !isPositiveFinite(context.amount())) {
            return DamageDecision.passThrough();
        }
        ServerPlayer attacker = context.attacker().orElse(null);
        if (attacker == null) {
            return DamageDecision.passThrough();
        }
        if (!playerStateService.canInteract(attacker.getUUID())) {
            return DamageDecision.cancel();
        }
        contributorsByEntity
                .computeIfAbsent(entity.getUUID(), ignored -> ConcurrentHashMap.newKeySet())
                .add(attacker.getUUID());
        double damageMultiplier = weaponItemStackService.sameRoomDamageMultiplier(attacker.getMainHandItem(), roomId);
        float adjustedAmount = scaledDamageAmount(context.amount(), damageMultiplier);
        return adjustedAmount == context.amount()
                ? DamageDecision.passThrough()
                : DamageDecision.setAmount(adjustedAmount);
    }

    @Override
    public DeathDecision onEntityDeath(LivingEntity entity, EntityDeathContext context) {
        if (entity == null || context == null || !isOwnedByThisRoom(entity)) {
            return DeathDecision.passThrough();
        }

        UUID entityId = entity.getUUID();
        ServerPlayer killer = context.killer().orElse(null);
        if (killer != null && playerStateService.get(killer.getUUID()).isPresent()) {
            Set<UUID> contributors = new LinkedHashSet<>(
                    contributorsByEntity.getOrDefault(entityId, Set.of()));
            contributors.add(killer.getUUID());
            economyService.awardKillAndAssists(
                    killer.getUUID(),
                    contributors,
                    rewardResolver.killPoints(entity),
                    rewardResolver.assistPoints(entity));
        }
        contributorsByEntity.remove(entityId);
        ownershipRegistry.unregister(entity);
        lifecycleHook.onEntityLifecycle(entity, EntityDeathReason.KILLED);
        return DeathDecision.passThrough();
    }

    public void forgetEntity(UUID entityId) {
        if (entityId != null) {
            contributorsByEntity.remove(entityId);
        }
    }

    private boolean isOwnedByThisRoom(LivingEntity entity) {
        return ownershipRegistry.entryOf(entity)
                .map(ModeEntityOwnershipRegistry.Entry::roomId)
                .map(this::sameRoom)
                .orElse(false);
    }

    private boolean sameRoom(RoomId other) {
        return other != null
                && GameModeRegistry.canonicalize(other.gameType()).equals(gameType())
                && other.mapName().equals(mapName());
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

    private static boolean isPositiveFinite(float amount) {
        return Float.isFinite(amount) && amount > 0.0F;
    }

    public interface RewardResolver {
        default double killPoints(LivingEntity entity) {
            return ZombiesEconomyService.DEFAULT_KILL_POINTS;
        }

        default double assistPoints(LivingEntity entity) {
            return ZombiesEconomyService.DEFAULT_ASSIST_POINTS;
        }

        static RewardResolver defaults() {
            return new RewardResolver() {
            };
        }
    }

    @FunctionalInterface
    public interface LifecycleHook {
        void onEntityLifecycle(LivingEntity entity, EntityDeathReason reason);
    }

    public enum EntityDeathReason {
        KILLED,
        RECYCLED_RETRY,
        REMOVED_CONSUME_BUDGET,
        CLEANUP
    }
}
