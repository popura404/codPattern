package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.zombies.map.object.ZombiesUltimateMachineData;
import com.cdp.codpattern.app.zombies.model.ZombiesBuffState;
import com.cdp.codpattern.app.zombies.model.ZombiesBuffType;
import com.cdp.codpattern.app.zombies.model.ZombiesPlayerRuntimeState;
import com.cdp.codpattern.app.zombies.model.ZombiesWeaponInstanceState;

import java.util.Map;
import java.util.UUID;

public final class ZombiesMvp3AdditionalClosureCompatTest {
    private ZombiesMvp3AdditionalClosureCompatTest() {
    }

    public static void main(String[] args) {
        nonRequiresPowerGrowthWorksBeforePowerOn();
        doubleAmmoClearHalvesStarterAndPrimaryOnlyWhenOwned();
        ultimateInvalidLevelConfigurationDoesNotSpendOrMutateWeapon();
    }

    private static void nonRequiresPowerGrowthWorksBeforePowerOn() {
        Services services = services();
        UUID playerId = playerId(1);
        services.economy.addPoints(playerId, 1_000.0D);
        services.players.getOrCreate(playerId).setPrimaryWeapon(
                new ZombiesWeaponInstanceState("tacz:m4a1", 2, 0, 1.25D, 120, 210));
        Map<String, ZombiesUltimateMachineData.UpgradeLevelData> levels = Map.of(
                "1", new ZombiesUltimateMachineData.UpgradeLevelData(300, 1.75D));

        require(!services.power.isPowerOn(), "setup should leave shared power off");
        requireSuccess(services.buffs.purchaseBuff(playerId, ZombiesBuffType.DOUBLE_HEALTH, 200.0D, false),
                "non-requiresPower soda should be purchasable before power is on");
        require(services.players.getOrCreate(playerId).hasBuff(ZombiesBuffType.DOUBLE_HEALTH),
                "non-requiresPower soda should grant buff");
        requireSuccess(services.ultimate.upgradePrimaryWeapon(playerId, 1, levels, false),
                "non-requiresPower ultimate should upgrade before power is on");
        require(primary(services.players, playerId).upgradeLevel() == 1,
                "non-requiresPower ultimate should write upgrade level");
        requireClose(primary(services.players, playerId).damageMultiplier(), 1.75D,
                "non-requiresPower ultimate should write configured multiplier");
        requirePoints(services.players, playerId, 500.0D,
                "non-requiresPower growth should spend only successful soda and ultimate costs");
        require(!services.power.isPowerOn(), "non-requiresPower growth should not flip shared power state");
    }

    private static void doubleAmmoClearHalvesStarterAndPrimaryOnlyWhenOwned() {
        Services services = services();
        UUID playerId = playerId(2);
        ZombiesPlayerRuntimeState state = services.players.getOrCreate(playerId);
        state.setStarterWeapon(new ZombiesWeaponInstanceState("tacz:glock_17", 0, 0, 1.0D, 41, 82));
        state.setPrimaryWeapon(new ZombiesWeaponInstanceState("tacz:ak47", 2, 1, 1.75D, 101, 210));
        state.addBuff(ZombiesBuffState.defaultFor(ZombiesBuffType.SCORE_MULTIPLIER));

        ZombiesServiceResult<ZombiesBuffService.BuffClearResult> scoreOnlyClear =
                services.buffs.clearBuffsForDeathOrRevive(playerId);

        requireSuccess(scoreOnlyClear, "score-only clear should succeed");
        require(scoreOnlyClear.value().orElseThrow().clearedBuffs() == 1,
                "score-only clear should report one removed buff");
        require(!scoreOnlyClear.value().orElseThrow().halvedReserveAmmo(),
                "score-only clear should not report ammo halving");
        require(primary(services.players, playerId).reserveAmmo() == 101,
                "clear without double_ammo should preserve primary reserve");
        require(starter(services.players, playerId).reserveAmmo() == 41,
                "clear without double_ammo should preserve starter reserve");

        state.addBuff(ZombiesBuffState.defaultFor(ZombiesBuffType.DOUBLE_AMMO));
        state.addBuff(ZombiesBuffState.defaultFor(ZombiesBuffType.DOUBLE_HEALTH));

        ZombiesServiceResult<ZombiesBuffService.BuffClearResult> doubleAmmoClear =
                services.buffs.clearBuffsForDeathOrRevive(playerId);

        requireSuccess(doubleAmmoClear, "double_ammo clear should succeed");
        require(doubleAmmoClear.value().orElseThrow().clearedBuffs() == 2,
                "double_ammo clear should report removed buff count");
        require(doubleAmmoClear.value().orElseThrow().halvedReserveAmmo(),
                "double_ammo clear should report reserve ammo halving");
        require(primary(services.players, playerId).reserveAmmo() == 50,
                "double_ammo clear should floor primary reserve / 2");
        require(starter(services.players, playerId).reserveAmmo() == 20,
                "double_ammo clear should floor starter reserve / 2");
        require(state.buffs().isEmpty(), "double_ammo clear should remove all buffs");
    }

    private static void ultimateInvalidLevelConfigurationDoesNotSpendOrMutateWeapon() {
        Services services = services();
        UUID missingLevelPlayer = playerId(3);
        services.economy.addPoints(missingLevelPlayer, 1_000.0D);
        services.players.getOrCreate(missingLevelPlayer).setPrimaryWeapon(
                new ZombiesWeaponInstanceState("tacz:m4a1", 2, 0, 1.25D, 120, 210));
        requireSuccess(services.power.turnOn(missingLevelPlayer, 100.0D),
                "setup power should succeed before invalid ultimate checks");

        ZombiesServiceResult<ZombiesUltimateMachineService.WeaponUpgradeResult> missingTarget =
                services.ultimate.upgradePrimaryWeapon(
                        missingLevelPlayer,
                        2,
                        Map.of("2", new ZombiesUltimateMachineData.UpgradeLevelData(500, 2.0D)),
                        true);

        requireFailure(missingTarget, ZombiesErrorCode.of("ultimate.invalid_level"),
                "ultimate should fail when target level data is missing");
        requirePoints(services.players, missingLevelPlayer, 900.0D,
                "missing target level failure should not deduct");
        require(primary(services.players, missingLevelPlayer).upgradeLevel() == 0,
                "missing target level failure should not mutate upgrade level");
        requireClose(primary(services.players, missingLevelPlayer).damageMultiplier(), 1.25D,
                "missing target level failure should preserve damage multiplier");

        UUID invalidMultiplierPlayer = playerId(4);
        services.economy.addPoints(invalidMultiplierPlayer, 1_000.0D);
        services.players.getOrCreate(invalidMultiplierPlayer).setPrimaryWeapon(
                new ZombiesWeaponInstanceState("tacz:ak47", 2, 0, 1.35D, 90, 180));

        ZombiesServiceResult<ZombiesUltimateMachineService.WeaponUpgradeResult> invalidMultiplier =
                services.ultimate.upgradePrimaryWeapon(
                        invalidMultiplierPlayer,
                        1,
                        Map.of("1", new ZombiesUltimateMachineData.UpgradeLevelData(500, 0.0D)),
                        true);

        requireFailure(invalidMultiplier, ZombiesErrorCode.of("ultimate.invalid_level"),
                "ultimate should fail when target multiplier is invalid");
        requirePoints(services.players, invalidMultiplierPlayer, 1_000.0D,
                "invalid multiplier failure should not deduct");
        require(primary(services.players, invalidMultiplierPlayer).upgradeLevel() == 0,
                "invalid multiplier failure should not mutate upgrade level");
        requireClose(primary(services.players, invalidMultiplierPlayer).damageMultiplier(), 1.35D,
                "invalid multiplier failure should preserve damage multiplier");
    }

    private static Services services() {
        ZombiesPlayerStateService players = new ZombiesPlayerStateService();
        ZombiesEconomyService economy = new ZombiesEconomyService(players);
        ZombiesPowerService power = new ZombiesPowerService(economy);
        return new Services(
                players,
                economy,
                power,
                new ZombiesBuffService(economy, power),
                new ZombiesUltimateMachineService(economy, power));
    }

    private static ZombiesWeaponInstanceState primary(ZombiesPlayerStateService players, UUID playerId) {
        return players.get(playerId)
                .flatMap(ZombiesPlayerRuntimeState::primaryWeapon)
                .orElseThrow(() -> new AssertionError("expected primary weapon"));
    }

    private static ZombiesWeaponInstanceState starter(ZombiesPlayerStateService players, UUID playerId) {
        return players.get(playerId)
                .flatMap(ZombiesPlayerRuntimeState::starterWeapon)
                .orElseThrow(() -> new AssertionError("expected starter weapon"));
    }

    private static void requirePoints(ZombiesPlayerStateService players, UUID playerId, double expected, String message) {
        requireClose(players.get(playerId).orElseThrow().points(), expected, message + ": balance");
    }

    private static void requireSuccess(ZombiesServiceResult<?> result, String message) {
        require(result.success(), message + ": " + result.code());
    }

    private static void requireFailure(ZombiesServiceResult<?> result, ZombiesErrorCode expectedCode, String message) {
        require(!result.success(), message + ": expected failure");
        require(expectedCode.equals(result.code()),
                message + ": expected " + expectedCode + " but was " + result.code());
    }

    private static void requireClose(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < 0.000001D,
                message + ": expected " + expected + " but was " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static UUID playerId(int suffix) {
        return new UUID(0L, suffix);
    }

    private record Services(
            ZombiesPlayerStateService players,
            ZombiesEconomyService economy,
            ZombiesPowerService power,
            ZombiesBuffService buffs,
            ZombiesUltimateMachineService ultimate
    ) {
    }
}
