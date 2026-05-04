package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.zombies.map.object.ZombiesUltimateMachineData;
import com.cdp.codpattern.app.zombies.model.ZombiesArmorState;
import com.cdp.codpattern.app.zombies.model.ZombiesBuffType;
import com.cdp.codpattern.app.zombies.model.ZombiesBuffState;
import com.cdp.codpattern.app.zombies.model.ZombiesPlayerRuntimeState;
import com.cdp.codpattern.app.zombies.model.ZombiesWeaponInstanceState;
import com.cdp.codpattern.app.zombies.sync.ZombiesRuntimeStateKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ZombiesMvp3GrowthServicesCompatTest {
    private ZombiesMvp3GrowthServicesCompatTest() {
    }

    public static void main(String[] args) {
        powerPurchaseIsSharedAtomicAndRepeatDoesNotSpend();
        buffPurchaseHonorsPowerGateAndRepeatDoesNotSpend();
        scoreMultiplierAppliesToKillAndAssistRewards();
        buffDamageTakenMultiplierCombinesArmorAndDoubleHealth();
        buffCombatServiceScalesRoomMobDamageAndRequestsReactiveExplosion();
        deathOrReviveClearRemovesBuffsAndHalvesDoubleAmmoReserve();
        ultimateMachineUpgradesPrimaryAndFailuresDoNotSpend();
    }

    private static void powerPurchaseIsSharedAtomicAndRepeatDoesNotSpend() {
        Services services = services();
        UUID playerId = playerId(1);
        services.economy.addPoints(playerId, 400.0D);

        ZombiesServiceResult<ZombiesPowerService.PowerPurchaseResult> insufficient =
                services.power.turnOn(playerId, 500.0D);

        requireFailure(insufficient, ZombiesErrorCode.ECONOMY_NOT_ENOUGH_POINTS,
                "power purchase should fail when points are insufficient");
        require(!services.power.isPowerOn(), "failed power purchase should keep power off");
        requirePoints(services.players, playerId, 400.0D, "failed power purchase should not deduct");

        requireSuccess(services.power.turnOn(playerId, 300.0D), "power purchase should succeed");
        require(services.power.isPowerOn(), "successful power purchase should turn shared power on");
        requirePoints(services.players, playerId, 100.0D, "power purchase should deduct cost");

        ZombiesServiceResult<ZombiesPowerService.PowerPurchaseResult> repeat =
                services.power.turnOn(playerId, 300.0D);

        requireFailure(repeat, ZombiesErrorCode.POWER_ALREADY_ON, "repeat power purchase should fail as already on");
        requirePoints(services.players, playerId, 100.0D, "repeat power purchase should not deduct");
    }

    private static void buffPurchaseHonorsPowerGateAndRepeatDoesNotSpend() {
        Services services = services();
        UUID playerId = playerId(2);
        services.economy.addPoints(playerId, 1_000.0D);

        ZombiesServiceResult<ZombiesBuffService.BuffPurchaseResult> locked =
                services.buffs.purchaseBuff(playerId, ZombiesBuffType.DOUBLE_HEALTH, 250.0D, true);

        requireFailure(locked, ZombiesErrorCode.POWER_REQUIRES_POWER,
                "requiresPower soda should fail before power is on");
        requirePoints(services.players, playerId, 1_000.0D, "power-gated soda failure should not deduct");

        requireSuccess(services.power.turnOn(playerId, 100.0D), "setup power purchase should succeed");
        requireSuccess(services.buffs.purchaseBuff(playerId, "double_health", 250.0D, true),
                "double_health soda should purchase after power is on");
        require(services.players.getOrCreate(playerId).hasBuff(ZombiesBuffType.DOUBLE_HEALTH),
                "double_health should be recorded on player state");
        requirePoints(services.players, playerId, 650.0D, "buff purchase should deduct cost");
        requireBooleanPlayerValue(services.players, playerId,
                ZombiesRuntimeStateKeys.playerBuff(ZombiesBuffType.DOUBLE_HEALTH.id()), true,
                "player values should expose owned double_health buff");

        ZombiesServiceResult<ZombiesBuffService.BuffPurchaseResult> repeat =
                services.buffs.purchaseBuff(playerId, ZombiesBuffType.DOUBLE_HEALTH, 250.0D, true);

        requireSuccess(repeat, "repeat buff purchase should be a no-op success");
        require(repeat.value().orElseThrow().alreadyOwned(), "repeat buff result should mark already owned");
        requirePoints(services.players, playerId, 650.0D, "repeat buff purchase should not deduct");

        requireSuccess(services.buffs.purchaseBuff(playerId, ZombiesBuffType.SCORE_MULTIPLIER, 200.0D, true),
                "score_multiplier purchase should succeed");
        requireClose(services.buffs.scoreMultiplier(playerId), 1.25D, "score multiplier should read from buff state");
    }

    private static void scoreMultiplierAppliesToKillAndAssistRewards() {
        Services services = services();
        UUID killerId = playerId(60);
        UUID assisterId = playerId(61);
        services.players.getOrCreate(killerId).addBuff(ZombiesBuffState.defaultFor(ZombiesBuffType.SCORE_MULTIPLIER));
        services.players.getOrCreate(assisterId).addBuff(new ZombiesBuffState(ZombiesBuffType.SCORE_MULTIPLIER, 2.0D));

        ZombiesServiceResult<ZombiesEconomyService.RewardSummary> result = services.economy.awardKillAndAssists(
                killerId,
                List.of(assisterId),
                10.0D,
                3.0D);

        requireSuccess(result, "score multiplier should apply through economy reward path");
        requirePoints(services.players, killerId, 12.5D, "killer score multiplier should scale kill reward");
        requirePoints(services.players, assisterId, 6.0D, "assister score multiplier should scale assist reward");
    }

    private static void buffDamageTakenMultiplierCombinesArmorAndDoubleHealth() {
        Services services = services();
        UUID playerId = playerId(6);
        ZombiesPlayerRuntimeState state = services.players.getOrCreate(playerId);
        state.setArmor(new ZombiesArmorState(2, 0.50D));

        requireClose(services.buffs.damageTakenMultiplier(playerId), 0.50D,
                "armor should reduce damage taken");

        state.addBuff(ZombiesBuffState.defaultFor(ZombiesBuffType.DOUBLE_HEALTH));
        requireClose(services.buffs.damageTakenMultiplier(playerId), 0.25D,
                "double_health should halve post-armor damage taken");

        require(!services.buffs.hasReactiveExplosion(playerId),
                "reactive explosion helper should be false when buff is missing");
        state.addBuff(ZombiesBuffState.defaultFor(ZombiesBuffType.REACTIVE_EXPLOSION));
        require(services.buffs.hasReactiveExplosion(playerId),
                "reactive explosion helper should read owned buff state");
    }

    private static void buffCombatServiceScalesRoomMobDamageAndRequestsReactiveExplosion() {
        Services services = services();
        UUID playerId = playerId(62);
        ZombiesPlayerRuntimeState state = services.players.getOrCreate(playerId);
        state.setArmor(new ZombiesArmorState(3, 0.50D));
        state.addBuff(ZombiesBuffState.defaultFor(ZombiesBuffType.DOUBLE_HEALTH));
        state.addBuff(ZombiesBuffState.defaultFor(ZombiesBuffType.REACTIVE_EXPLOSION));

        RoomId roomA = RoomId.of("zombies", "arena_a");
        List<ZombiesBuffCombatService.ExplosionRequest> roomARequests = new ArrayList<>();
        ZombiesBuffCombatService roomACombat = new ZombiesBuffCombatService(
                roomA,
                services.players,
                null,
                request -> {
                    roomARequests.add(request);
                    return ZombiesBuffCombatService.ExplosionResult.applied(request);
                },
                100L);

        ZombiesBuffCombatService.DamageApplicationResult first =
                roomACombat.applyRoomMonsterDamage(playerId, 20.0F, 100L);

        require(first.roomMonsterDamage(), "room mob damage should be marked as handled");
        requireClose(first.adjustedAmount(), 5.0D,
                "armor and double_health should reduce room mob damage before it reaches player");
        require(first.explosionResult().requested(), "reactive explosion should request on first valid hit");
        require(first.explosionResult().aoeApplied(), "test explosion hook should mark request applied");
        require(roomARequests.size() == 1, "first valid hit should call explosion hook once");
        requireClose(roomARequests.get(0).radius(), 4.0D, "reactive explosion radius should be 4 blocks");
        requireClose(roomARequests.get(0).damageMaxHealthFraction(), 0.15D,
                "reactive explosion damage fraction should be 15 percent max health");

        ZombiesBuffCombatService.DamageApplicationResult cooldown =
                roomACombat.applyRoomMonsterDamage(playerId, 20.0F, 150L);

        requireClose(cooldown.adjustedAmount(), 5.0D, "cooldown should not skip damage multiplier");
        require(!cooldown.explosionResult().requested(), "cooldown hit should not request explosion");
        require("cooldown".equals(cooldown.explosionResult().status()), "cooldown hit should report cooldown status");
        require(roomARequests.size() == 1, "cooldown hit should not call explosion hook again");

        ZombiesBuffCombatService.DamageApplicationResult afterCooldown =
                roomACombat.applyRoomMonsterDamage(playerId, 20.0F, 200L);

        require(afterCooldown.explosionResult().requested(), "hit at cooldown boundary should request explosion again");
        require(roomARequests.size() == 2, "cooldown boundary hit should call explosion hook again");

        RoomId roomB = RoomId.of("zombies", "arena_b");
        List<ZombiesBuffCombatService.ExplosionRequest> roomBRequests = new ArrayList<>();
        ZombiesBuffCombatService roomBCombat = new ZombiesBuffCombatService(
                roomB,
                services.players,
                null,
                request -> {
                    roomBRequests.add(request);
                    return ZombiesBuffCombatService.ExplosionResult.applied(request);
                },
                100L);

        ZombiesBuffCombatService.DamageApplicationResult isolatedRoom =
                roomBCombat.applyRoomMonsterDamage(playerId, 20.0F, 150L);

        require(isolatedRoom.explosionResult().requested(),
                "reactive explosion cooldown should be isolated per room service");
        require(roomBRequests.size() == 1, "other room should receive its own explosion request");

        ZombiesBuffCombatService defaultExecutorCombat = new ZombiesBuffCombatService(
                RoomId.of("zombies", "arena_default_executor"),
                services.players,
                null);
        ZombiesBuffCombatService.DamageApplicationResult defaultExecutorWithoutPlayer =
                defaultExecutorCombat.applyRoomMonsterDamage(playerId, 20.0F, 10L);

        require(defaultExecutorWithoutPlayer.explosionResult().requested(),
                "default executor should still expose an explosion request");
        require(!defaultExecutorWithoutPlayer.explosionResult().aoeApplied(),
                "default executor should require real ServerPlayer context before applying AOE");
        require(defaultExecutorWithoutPlayer.explosionResult().status().startsWith("failed:missing_server_player"),
                "default executor without a ServerPlayer should report missing context");
    }

    private static void deathOrReviveClearRemovesBuffsAndHalvesDoubleAmmoReserve() {
        Services services = services();
        UUID playerId = playerId(3);
        services.economy.addPoints(playerId, 2_000.0D);
        services.players.getOrCreate(playerId).setPrimaryWeapon(
                new ZombiesWeaponInstanceState("tacz:m4a1", 2, 0, 1.25D, 101, 210));
        requireSuccess(services.power.turnOn(playerId, 100.0D), "setup power purchase should succeed");
        requireSuccess(services.buffs.purchaseBuff(playerId, ZombiesBuffType.DOUBLE_AMMO, 250.0D, true),
                "double_ammo purchase should succeed");
        requireSuccess(services.buffs.purchaseBuff(playerId, ZombiesBuffType.SCORE_MULTIPLIER, 250.0D, true),
                "score_multiplier purchase should succeed");

        ZombiesServiceResult<ZombiesBuffService.BuffClearResult> clear =
                services.buffs.clearBuffsForDeathOrRevive(playerId);

        requireSuccess(clear, "death/revive buff clear should succeed");
        require(clear.value().orElseThrow().clearedBuffs() == 2, "clear should report removed buff count");
        ZombiesPlayerRuntimeState state = services.players.getOrCreate(playerId);
        require(state.buffs().isEmpty(), "death/revive clear should remove all buffs");
        require(primary(services.players, playerId).reserveAmmo() == 50,
                "double_ammo clear should floor current reserve ammo / 2");
        requireBooleanPlayerValue(services.players, playerId,
                ZombiesRuntimeStateKeys.playerBuff(ZombiesBuffType.DOUBLE_AMMO.id()), false,
                "player values should expose cleared double_ammo buff");
    }

    private static void ultimateMachineUpgradesPrimaryAndFailuresDoNotSpend() {
        Services services = services();
        UUID playerId = playerId(4);
        Map<String, ZombiesUltimateMachineData.UpgradeLevelData> levels = Map.of(
                "1", new ZombiesUltimateMachineData.UpgradeLevelData(500, 1.75D),
                "2", new ZombiesUltimateMachineData.UpgradeLevelData(900, 2.30D));
        services.economy.addPoints(playerId, 3_000.0D);
        services.players.getOrCreate(playerId).setPrimaryWeapon(
                new ZombiesWeaponInstanceState("tacz:m4a1", 2, 0, 1.25D, 120, 210));

        ZombiesServiceResult<ZombiesUltimateMachineService.WeaponUpgradeResult> locked =
                services.ultimate.upgradePrimaryWeapon(playerId, 2, levels, true);

        requireFailure(locked, ZombiesErrorCode.POWER_REQUIRES_POWER,
                "requiresPower ultimate should fail before power is on");
        requirePoints(services.players, playerId, 3_000.0D, "power-gated ultimate failure should not deduct");
        require(primary(services.players, playerId).upgradeLevel() == 0,
                "power-gated ultimate failure should not upgrade weapon");

        requireSuccess(services.power.turnOn(playerId, 100.0D), "setup power purchase should succeed");
        requireSuccess(services.ultimate.upgradePrimaryWeapon(playerId, 2, levels, true),
                "ultimate should upgrade current primary weapon to level 1");
        require(primary(services.players, playerId).upgradeLevel() == 1, "ultimate should write upgrade level");
        requireClose(primary(services.players, playerId).damageMultiplier(), 1.75D,
                "ultimate should write target damage multiplier");
        requirePoints(services.players, playerId, 2_400.0D, "ultimate upgrade should deduct target level cost");
        requireIntPlayerValue(services.players, playerId, ZombiesRuntimeStateKeys.PLAYER_WEAPON_PRIMARY_UPGRADE, 1,
                "player values should expose primary upgrade");

        requireSuccess(services.ultimate.upgradePrimaryWeapon(playerId, 2, levels, true),
                "ultimate should upgrade current primary weapon to level 2");
        require(primary(services.players, playerId).upgradeLevel() == 2, "ultimate should write second upgrade level");
        requireClose(primary(services.players, playerId).damageMultiplier(), 2.30D,
                "ultimate should write second target damage multiplier");
        requirePoints(services.players, playerId, 1_500.0D, "second ultimate upgrade should deduct target level cost");

        ZombiesServiceResult<ZombiesUltimateMachineService.WeaponUpgradeResult> max =
                services.ultimate.upgradePrimaryWeapon(playerId, 2, levels, true);

        requireFailure(max, ZombiesErrorCode.WEAPON_MAX_UPGRADE, "max-level ultimate should fail");
        requirePoints(services.players, playerId, 1_500.0D, "max-level ultimate failure should not deduct");

        UUID emptyPlayer = playerId(5);
        services.economy.addPoints(emptyPlayer, 1_000.0D);
        ZombiesServiceResult<ZombiesUltimateMachineService.WeaponUpgradeResult> missingWeapon =
                services.ultimate.upgradePrimaryWeapon(emptyPlayer, 2, levels, false);

        requireFailure(missingWeapon, ZombiesErrorCode.WEAPON_INVALID_CURRENT_WEAPON,
                "missing current primary should fail");
        requirePoints(services.players, emptyPlayer, 1_000.0D, "missing weapon failure should not deduct");
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

    private static void requirePoints(ZombiesPlayerStateService players, UUID playerId, double expected, String message) {
        requireClose(players.get(playerId).orElseThrow().points(), expected, message + ": balance");
    }

    private static void requireIntPlayerValue(
            ZombiesPlayerStateService players,
            UUID playerId,
            String key,
            int expected,
            String message
    ) {
        String value = players.playerValues(playerId).get(key).value();
        require(Integer.toString(expected).equals(value), message + ": expected " + expected + " but was " + value);
    }

    private static void requireBooleanPlayerValue(
            ZombiesPlayerStateService players,
            UUID playerId,
            String key,
            boolean expected,
            String message
    ) {
        String value = players.playerValues(playerId).get(key).value();
        require(Boolean.toString(expected).equals(value), message + ": expected " + expected + " but was " + value);
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
