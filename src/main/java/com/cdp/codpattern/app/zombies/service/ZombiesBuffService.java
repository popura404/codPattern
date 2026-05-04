package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.ModePlayerValue;
import com.cdp.codpattern.app.zombies.model.ZombiesArmorState;
import com.cdp.codpattern.app.zombies.model.ZombiesBuffState;
import com.cdp.codpattern.app.zombies.model.ZombiesBuffType;
import com.cdp.codpattern.app.zombies.model.ZombiesPlayerRuntimeState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure per-player soda buff state service.
 */
public final class ZombiesBuffService {
    private static final ZombiesErrorCode BUFF_INVALID = ZombiesErrorCode.of("buff.invalid");

    private final ZombiesEconomyService economyService;
    private final ZombiesPowerService powerService;

    public ZombiesBuffService(ZombiesEconomyService economyService, ZombiesPowerService powerService) {
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.powerService = Objects.requireNonNull(powerService, "powerService");
    }

    public ZombiesServiceResult<BuffPurchaseResult> purchaseBuff(
            UUID playerId,
            String buffId,
            double cost,
            boolean requiresPower
    ) {
        return ZombiesBuffType.fromId(buffId)
                .map(type -> purchaseBuff(playerId, type, cost, requiresPower))
                .orElseGet(() -> ZombiesServiceResult.failure(BUFF_INVALID, buffParams(buffId), ""));
    }

    public ZombiesServiceResult<BuffPurchaseResult> purchaseBuff(
            UUID playerId,
            ZombiesBuffType type,
            double cost,
            boolean requiresPower
    ) {
        if (type == null) {
            return ZombiesServiceResult.failure(BUFF_INVALID);
        }
        if (requiresPower && !powerService.isPowerOn()) {
            return ZombiesServiceResult.failure(ZombiesErrorCode.POWER_REQUIRES_POWER, buffParams(type.id()), "");
        }
        ZombiesPlayerRuntimeState currentState = economyService.state(playerId).orElse(null);
        if (currentState != null && currentState.hasBuff(type)) {
            return ZombiesServiceResult.success(new BuffPurchaseResult(currentState.buff(type).orElseThrow(), 0.0D, true));
        }

        return economyService.spendAtomically(playerId, cost, state -> {
            if (state.hasBuff(type)) {
                return ZombiesServiceResult.success(new BuffPurchaseResult(state.buff(type).orElseThrow(), 0.0D, true));
            }
            ZombiesBuffState buff = ZombiesBuffState.defaultFor(type);
            state.addBuff(buff);
            return ZombiesServiceResult.success(new BuffPurchaseResult(buff, cost, false));
        });
    }

    public ZombiesServiceResult<BuffClearResult> clearBuffsForDeathOrRevive(UUID playerId) {
        ZombiesPlayerRuntimeState state = economyService.state(playerId).orElse(null);
        if (state == null) {
            return ZombiesServiceResult.success(new BuffClearResult(0, false));
        }
        synchronized (state) {
            int cleared = state.buffs().size();
            boolean hadDoubleAmmo = state.hasBuff(ZombiesBuffType.DOUBLE_AMMO);
            if (hadDoubleAmmo) {
                state.starterWeapon()
                        .map(weapon -> weapon.withReserveAmmo(weapon.reserveAmmo() / 2))
                        .ifPresent(state::setStarterWeapon);
                state.primaryWeapon()
                        .map(weapon -> weapon.withReserveAmmo(weapon.reserveAmmo() / 2))
                        .ifPresent(state::setPrimaryWeapon);
            }
            state.clearBuffs();
            return ZombiesServiceResult.success(new BuffClearResult(cleared, hadDoubleAmmo));
        }
    }

    public double scoreMultiplier(UUID playerId) {
        return economyService.state(playerId)
                .flatMap(state -> state.buff(ZombiesBuffType.SCORE_MULTIPLIER))
                .map(ZombiesBuffState::multiplier)
                .orElse(1.0D);
    }

    public double damageTakenMultiplier(UUID playerId) {
        return economyService.state(playerId)
                .map(ZombiesBuffService::damageTakenMultiplier)
                .orElse(1.0D);
    }

    public boolean hasReactiveExplosion(UUID playerId) {
        return economyService.state(playerId)
                .map(state -> state.hasBuff(ZombiesBuffType.REACTIVE_EXPLOSION))
                .orElse(false);
    }

    static double damageTakenMultiplier(ZombiesPlayerRuntimeState state) {
        if (state == null) {
            return 1.0D;
        }
        double armorMultiplier = state.armor()
                .map(ZombiesArmorState::damageTakenMultiplier)
                .orElse(1.0D);
        double doubleHealthMultiplier = state.buff(ZombiesBuffType.DOUBLE_HEALTH)
                .map(ZombiesBuffState::multiplier)
                .filter(multiplier -> Double.isFinite(multiplier) && multiplier > 0.0D)
                .orElse(1.0D);
        double multiplier = armorMultiplier / doubleHealthMultiplier;
        return Double.isFinite(multiplier) && multiplier > 0.0D ? multiplier : 1.0D;
    }

    private static Map<String, ModePlayerValue> buffParams(String buffId) {
        Map<String, ModePlayerValue> params = new LinkedHashMap<>();
        params.put("buffId", ModePlayerValue.ofString(buffId));
        return params;
    }

    public record BuffPurchaseResult(
            ZombiesBuffState buff,
            double cost,
            boolean alreadyOwned
    ) {
        public BuffPurchaseResult {
            Objects.requireNonNull(buff, "buff");
            cost = Math.max(0.0D, cost);
        }
    }

    public record BuffClearResult(
            int clearedBuffs,
            boolean halvedReserveAmmo
    ) {
        public BuffClearResult {
            clearedBuffs = Math.max(0, clearedBuffs);
        }
    }
}
