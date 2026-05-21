package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.ModePlayerValue;
import com.cdp.codpattern.app.zombies.map.object.ZombiesUltimateMachineData;
import com.cdp.codpattern.app.zombies.model.ZombiesPlayerRuntimeState;
import com.cdp.codpattern.app.zombies.model.ZombiesWeaponInstanceState;
import com.cdp.codpattern.config.zombies.ZombiesRulesConfig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure current-primary weapon upgrade service for ultimate machines.
 */
public final class ZombiesUltimateMachineService {
    private static final ZombiesErrorCode ULTIMATE_INVALID_LEVEL = ZombiesErrorCode.of("ultimate.invalid_level");

    private final ZombiesEconomyService economyService;
    private final ZombiesPowerService powerService;

    public ZombiesUltimateMachineService(ZombiesEconomyService economyService, ZombiesPowerService powerService) {
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.powerService = Objects.requireNonNull(powerService, "powerService");
    }

    public ZombiesServiceResult<WeaponUpgradeResult> upgradePrimaryWeapon(
            UUID playerId,
            ZombiesUltimateMachineData machine
    ) {
        if (machine == null) {
            return ZombiesServiceResult.failure(ULTIMATE_INVALID_LEVEL);
        }
        return upgradePrimaryWeapon(playerId, machine.maxUpgradeLevel(), machine.levels(), machine.requiresPower(), null);
    }

    public ZombiesServiceResult<WeaponUpgradeResult> upgradePrimaryWeapon(
            UUID playerId,
            int maxUpgradeLevel,
            Map<String, ZombiesUltimateMachineData.UpgradeLevelData> levels,
            boolean requiresPower
    ) {
        return upgradePrimaryWeapon(playerId, maxUpgradeLevel, levels, requiresPower, null);
    }

    public ZombiesServiceResult<WeaponUpgradeResult> upgradePrimaryWeapon(
            UUID playerId,
            ZombiesRulesConfig.UltimateMachine rules,
            boolean requiresPower
    ) {
        return upgradePrimaryWeapon(playerId, rules, requiresPower, null);
    }

    public ZombiesServiceResult<WeaponUpgradeResult> upgradePrimaryWeapon(
            UUID playerId,
            ZombiesUltimateMachineData machine,
            WeaponUpgradeCommitGuard commitGuard
    ) {
        if (machine == null) {
            return ZombiesServiceResult.failure(ULTIMATE_INVALID_LEVEL);
        }
        return upgradePrimaryWeapon(
                playerId,
                machine.maxUpgradeLevel(),
                machine.levels(),
                machine.requiresPower(),
                commitGuard);
    }

    public ZombiesServiceResult<WeaponUpgradeResult> upgradePrimaryWeapon(
            UUID playerId,
            int maxUpgradeLevel,
            Map<String, ZombiesUltimateMachineData.UpgradeLevelData> levels,
            boolean requiresPower,
            WeaponUpgradeCommitGuard commitGuard
    ) {
        return upgradePrimaryWeaponResolved(playerId, maxUpgradeLevel, legacyLevels(levels), requiresPower, commitGuard);
    }

    public ZombiesServiceResult<WeaponUpgradeResult> upgradePrimaryWeapon(
            UUID playerId,
            ZombiesRulesConfig.UltimateMachine rules,
            boolean requiresPower,
            WeaponUpgradeCommitGuard commitGuard
    ) {
        if (rules == null) {
            return ZombiesServiceResult.failure(ULTIMATE_INVALID_LEVEL);
        }
        int maxUpgradeLevel = Math.max(0, rules.getMaxUpgradeLevel() == null ? 0 : rules.getMaxUpgradeLevel());
        return upgradePrimaryWeaponResolved(
                playerId,
                maxUpgradeLevel,
                ruleLevels(rules.getLevels()),
                requiresPower,
                commitGuard);
    }

    private ZombiesServiceResult<WeaponUpgradeResult> upgradePrimaryWeaponResolved(
            UUID playerId,
            int maxUpgradeLevel,
            Map<String, LevelData> levels,
            boolean requiresPower,
            WeaponUpgradeCommitGuard commitGuard
    ) {
        if (requiresPower && !powerService.isPowerOn()) {
            return ZombiesServiceResult.failure(ZombiesErrorCode.POWER_REQUIRES_POWER);
        }

        ZombiesPlayerRuntimeState currentState = economyService.state(playerId).orElse(null);
        ZombiesWeaponInstanceState currentWeapon = currentState == null ? null : currentState.primaryWeapon().orElse(null);
        if (!isValidCurrentWeapon(currentWeapon)) {
            return ZombiesServiceResult.failure(ZombiesErrorCode.WEAPON_INVALID_CURRENT_WEAPON);
        }
        if (currentWeapon.upgradeLevel() >= maxUpgradeLevel) {
            return ZombiesServiceResult.failure(ZombiesErrorCode.WEAPON_MAX_UPGRADE, weaponParams(currentWeapon), "");
        }

        int targetLevel = currentWeapon.upgradeLevel() + 1;
        LevelData target = levelData(levels, targetLevel);
        if (target == null || !ZombiesWeaponInstanceState.isValidDamageMultiplier(target.damageMultiplier())) {
            return ZombiesServiceResult.failure(ULTIMATE_INVALID_LEVEL, weaponParams(currentWeapon), "");
        }

        return economyService.spendAtomically(playerId, target.cost(), state -> {
            ZombiesWeaponInstanceState lockedWeapon = state.primaryWeapon().orElse(null);
            if (!isValidCurrentWeapon(lockedWeapon)) {
                return ZombiesServiceResult.failure(ZombiesErrorCode.WEAPON_INVALID_CURRENT_WEAPON);
            }
            if (lockedWeapon.upgradeLevel() >= maxUpgradeLevel) {
                return ZombiesServiceResult.failure(ZombiesErrorCode.WEAPON_MAX_UPGRADE, weaponParams(lockedWeapon), "");
            }
            int lockedTargetLevel = lockedWeapon.upgradeLevel() + 1;
            LevelData lockedTarget = levelData(levels, lockedTargetLevel);
            if (!Objects.equals(target, lockedTarget)) {
                return ZombiesServiceResult.failure(ULTIMATE_INVALID_LEVEL, weaponParams(lockedWeapon), "");
            }

            ZombiesWeaponInstanceState upgradedWeapon =
                    lockedWeapon.withUpgrade(lockedTargetLevel, lockedTarget.damageMultiplier());
            ZombiesServiceResult<?> guardResult = commitGuard == null
                    ? ZombiesServiceResult.ok()
                    : commitGuard.beforeCommit(lockedWeapon, upgradedWeapon);
            if (guardResult == null || !guardResult.success()) {
                return ZombiesServiceResult.failure(
                        guardResult == null ? ZombiesErrorCode.WEAPON_INVALID_CURRENT_WEAPON : guardResult.code(),
                        guardResult == null ? Map.of() : guardResult.params(),
                        guardResult == null ? "" : guardResult.logMessage());
            }
            state.setPrimaryWeapon(upgradedWeapon);
            return ZombiesServiceResult.success(new WeaponUpgradeResult(upgradedWeapon, lockedTarget.cost()));
        });
    }

    private static boolean isValidCurrentWeapon(ZombiesWeaponInstanceState weapon) {
        return weapon != null
                && ZombiesWeaponInstanceState.isValidGunId(weapon.gunId())
                && ZombiesWeaponInstanceState.isValidWeaponLevel(weapon.weaponLevel());
    }

    private static LevelData levelData(
            Map<String, LevelData> levels,
            int targetLevel
    ) {
        if (levels == null) {
            return null;
        }
        return levels.get(Integer.toString(targetLevel));
    }

    private static Map<String, LevelData> legacyLevels(Map<String, ZombiesUltimateMachineData.UpgradeLevelData> levels) {
        Map<String, LevelData> normalized = new LinkedHashMap<>();
        if (levels == null) {
            return normalized;
        }
        levels.forEach((level, data) -> {
            String key = Objects.requireNonNullElse(level, "").trim();
            if (!key.isBlank() && data != null) {
                normalized.put(key, new LevelData(data.cost(), data.damageMultiplier()));
            }
        });
        return normalized;
    }

    private static Map<String, LevelData> ruleLevels(Map<String, ZombiesRulesConfig.UpgradeLevel> levels) {
        Map<String, LevelData> normalized = new LinkedHashMap<>();
        if (levels == null) {
            return normalized;
        }
        levels.forEach((level, data) -> {
            String key = Objects.requireNonNullElse(level, "").trim();
            if (!key.isBlank() && data != null) {
                int cost = data.getCost() == null ? 0 : data.getCost();
                double damageMultiplier = data.getDamageMultiplier() == null ? 0.0D : data.getDamageMultiplier();
                normalized.put(key, new LevelData(cost, damageMultiplier));
            }
        });
        return normalized;
    }

    private static Map<String, ModePlayerValue> weaponParams(ZombiesWeaponInstanceState weapon) {
        Map<String, ModePlayerValue> params = new LinkedHashMap<>();
        params.put("gunId", ModePlayerValue.ofString(weapon == null ? "" : weapon.gunId()));
        params.put("weaponLevel", ModePlayerValue.ofInt(weapon == null ? 0 : weapon.weaponLevel()));
        params.put("upgradeLevel", ModePlayerValue.ofInt(weapon == null ? 0 : weapon.upgradeLevel()));
        return params;
    }

    public record WeaponUpgradeResult(
            ZombiesWeaponInstanceState weapon,
            double cost
    ) {
        public WeaponUpgradeResult {
            Objects.requireNonNull(weapon, "weapon");
            cost = Math.max(0.0D, cost);
        }
    }

    @FunctionalInterface
    public interface WeaponUpgradeCommitGuard {
        ZombiesServiceResult<?> beforeCommit(
                ZombiesWeaponInstanceState currentWeapon,
                ZombiesWeaponInstanceState upgradedWeapon);
    }

    private record LevelData(int cost, double damageMultiplier) {
    }
}
