package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.zombies.map.object.ZombiesUltimateMachineData;
import com.cdp.codpattern.app.zombies.model.ZombiesArmorState;
import com.cdp.codpattern.app.zombies.model.ZombiesBuffState;
import com.cdp.codpattern.app.zombies.model.ZombiesBuffType;
import com.cdp.codpattern.app.zombies.model.ZombiesLifeState;
import com.cdp.codpattern.app.zombies.model.ZombiesPlayerRuntimeState;
import com.cdp.codpattern.app.zombies.model.ZombiesWeaponInstanceState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ZombiesMvp3RuntimeClosureCompatTest {
    private ZombiesMvp3RuntimeClosureCompatTest() {
    }

    public static void main(String[] args) {
        requiresPowerFailuresAndRepeatPowerDoNotSpend();
        scoreMultiplierScalesKillAndAssistRewards();
        doubleHealthArmorReductionAndReactiveExplosionArePureRoomScoped();
        intermissionRespawnRunsAfterObjectUpdateAndTeleportFailureStaysSpectating();
        pendingEndTeleportCleanupIsOneShotAndClearsOnOnlineCleanup();
    }

    private static void requiresPowerFailuresAndRepeatPowerDoNotSpend() {
        Services services = services();
        UUID playerId = playerId(1);
        services.economy.addPoints(playerId, 1_000.0D);
        services.players.getOrCreate(playerId).setPrimaryWeapon(
                new ZombiesWeaponInstanceState("tacz:m4a1", 2, 0, 1.25D, 90, 180));
        Map<String, ZombiesUltimateMachineData.UpgradeLevelData> levels = Map.of(
                "1", new ZombiesUltimateMachineData.UpgradeLevelData(300, 1.75D));

        ZombiesServiceResult<ZombiesBuffService.BuffPurchaseResult> lockedSoda =
                services.buffs.purchaseBuff(playerId, ZombiesBuffType.DOUBLE_HEALTH, 250.0D, true);
        requireFailure(lockedSoda, ZombiesErrorCode.POWER_REQUIRES_POWER,
                "requiresPower soda should fail while power is off");
        requirePoints(services.players, playerId, 1_000.0D,
                "requiresPower soda failure should not deduct points");
        require(!services.players.getOrCreate(playerId).hasBuff(ZombiesBuffType.DOUBLE_HEALTH),
                "requiresPower soda failure should not grant buff");

        ZombiesServiceResult<ZombiesUltimateMachineService.WeaponUpgradeResult> lockedUltimate =
                services.ultimate.upgradePrimaryWeapon(playerId, 1, levels, true);
        requireFailure(lockedUltimate, ZombiesErrorCode.POWER_REQUIRES_POWER,
                "requiresPower ultimate should fail while power is off");
        requirePoints(services.players, playerId, 1_000.0D,
                "requiresPower ultimate failure should not deduct points");
        require(primary(services.players, playerId).upgradeLevel() == 0,
                "requiresPower ultimate failure should not alter weapon");

        requireFailure(services.power.turnOn(playerId, 1_200.0D), ZombiesErrorCode.ECONOMY_NOT_ENOUGH_POINTS,
                "power should fail when player cannot pay");
        require(!services.power.isPowerOn(), "failed power purchase should keep shared power off");
        requirePoints(services.players, playerId, 1_000.0D, "failed power purchase should not deduct");

        requireSuccess(services.power.turnOn(playerId, 300.0D), "power purchase should succeed");
        requirePoints(services.players, playerId, 700.0D, "power purchase should deduct once");
        requireFailure(services.power.turnOn(playerId, 300.0D), ZombiesErrorCode.POWER_ALREADY_ON,
                "repeat power purchase should fail as already on");
        requirePoints(services.players, playerId, 700.0D, "repeat power purchase should not deduct");

        requireSuccess(services.buffs.purchaseBuff(playerId, ZombiesBuffType.DOUBLE_HEALTH, 250.0D, true),
                "requiresPower soda should succeed after power is on");
        requireSuccess(services.ultimate.upgradePrimaryWeapon(playerId, 1, levels, true),
                "requiresPower ultimate should succeed after power is on");
    }

    private static void scoreMultiplierScalesKillAndAssistRewards() {
        Services services = services();
        UUID killer = playerId(10);
        UUID assist = playerId(11);
        UUID plain = playerId(12);
        services.players.getOrCreate(killer).addBuff(new ZombiesBuffState(ZombiesBuffType.SCORE_MULTIPLIER, 1.5D));
        services.players.getOrCreate(assist).addBuff(new ZombiesBuffState(ZombiesBuffType.SCORE_MULTIPLIER, 2.0D));

        requireSuccess(services.economy.awardKillAndAssists(killer, List.of(assist, plain, killer), 20.0D, 5.0D),
                "kill and assist rewards should succeed");

        requirePoints(services.players, killer, 30.0D, "killer score multiplier should scale kill reward");
        requirePoints(services.players, assist, 10.0D, "assist score multiplier should scale assist reward");
        requirePoints(services.players, plain, 5.0D, "player without score multiplier should receive base assist");
    }

    private static void doubleHealthArmorReductionAndReactiveExplosionArePureRoomScoped() {
        Services services = services();
        UUID playerId = playerId(20);
        ZombiesPlayerRuntimeState state = services.players.getOrCreate(playerId);
        state.setArmor(new ZombiesArmorState(2, 0.50D));
        state.addBuff(ZombiesBuffState.defaultFor(ZombiesBuffType.DOUBLE_HEALTH));
        state.addBuff(ZombiesBuffState.defaultFor(ZombiesBuffType.REACTIVE_EXPLOSION));

        requireClose(services.buffs.damageTakenMultiplier(playerId), 0.25D,
                "double_health should halve post-armor damage taken in pure service");

        RoomId roomA = RoomId.of(BuiltInGameModes.ZOMBIES, "closure-a");
        RoomId roomB = RoomId.of(BuiltInGameModes.ZOMBIES, "closure-b");
        List<ZombiesBuffCombatService.ExplosionRequest> roomARequests = new ArrayList<>();
        List<ZombiesBuffCombatService.ExplosionRequest> roomBRequests = new ArrayList<>();
        ZombiesBuffCombatService roomACombat = new ZombiesBuffCombatService(
                roomA,
                services.players,
                null,
                request -> {
                    roomARequests.add(request);
                    return ZombiesBuffCombatService.ExplosionResult.applied(request);
                },
                100L);
        ZombiesBuffCombatService roomBCombat = new ZombiesBuffCombatService(
                roomB,
                services.players,
                null,
                request -> {
                    roomBRequests.add(request);
                    return ZombiesBuffCombatService.ExplosionResult.applied(request);
                },
                100L);

        ZombiesBuffCombatService.register(roomACombat);
        ZombiesBuffCombatService.register(roomBCombat);
        require(ZombiesBuffCombatService.serviceFor(roomA).orElseThrow() == roomACombat,
                "reactive explosion room registry should resolve room A service");
        require(ZombiesBuffCombatService.serviceFor(roomB).orElseThrow() == roomBCombat,
                "reactive explosion room registry should resolve room B service");

        ZombiesBuffCombatService.DamageApplicationResult roomAFirst =
                roomACombat.applyRoomMonsterDamage(playerId, 24.0F, 100L);
        requireClose(roomAFirst.adjustedAmount(), 6.0D,
                "room combat should apply armor and double_health reduction");
        require(roomAFirst.explosionResult().requested(), "first room A hit should request reactive explosion");

        ZombiesBuffCombatService.DamageApplicationResult roomACooldown =
                roomACombat.applyRoomMonsterDamage(playerId, 24.0F, 150L);
        require(!roomACooldown.explosionResult().requested(),
                "room A second hit inside cooldown should not request explosion");

        ZombiesBuffCombatService.DamageApplicationResult roomBFirst =
                roomBCombat.applyRoomMonsterDamage(playerId, 24.0F, 150L);
        require(roomBFirst.explosionResult().requested(),
                "room B hit at same tick should use isolated cooldown state");
        require(roomARequests.size() == 1, "room A should record only its own first explosion");
        require(roomBRequests.size() == 1, "room B should record its own explosion");
        require(roomARequests.get(0).roomId().equals(roomA), "room A explosion request should retain room id");
        require(roomBRequests.get(0).roomId().equals(roomB), "room B explosion request should retain room id");
    }

    private static void intermissionRespawnRunsAfterObjectUpdateAndTeleportFailureStaysSpectating() {
        Services services = services();
        UUID alive = playerId(30);
        UUID deadFail = playerId(31);
        UUID deadSuccess = playerId(32);
        List<UUID> members = List.of(alive, deadFail, deadSuccess);
        services.players.registerPlayers(members);
        services.players.markDeadSpectating(deadFail);
        services.players.markDeadSpectating(deadSuccess);
        services.players.getOrCreate(deadSuccess).addBuff(ZombiesBuffState.defaultFor(ZombiesBuffType.DOUBLE_AMMO));
        services.players.getOrCreate(deadSuccess).setPrimaryWeapon(
                new ZombiesWeaponInstanceState("tacz:ak47", 2, 1, 1.75D, 101, 180));

        OrderedIntermissionHarness harness = new OrderedIntermissionHarness(services.respawn);
        harness.run(members, Set.of(deadSuccess));

        require(harness.events.equals(List.of(
                        "objects_updated",
                        "teleport:" + deadFail + ":failed",
                        "teleport:" + deadSuccess + ":success",
                        "prepare:" + deadSuccess)),
                "intermission order should be object update, teleport attempts, successful prepare only");
        require(services.players.getOrCreate(deadFail).lifeState() == ZombiesLifeState.DEAD_SPECTATING,
                "failed intermission teleport should keep player dead spectating");
        require(services.players.getOrCreate(deadSuccess).lifeState() == ZombiesLifeState.ALIVE,
                "successful intermission teleport should mark player alive");
        require(primary(services.players, deadSuccess).reserveAmmo() == 50,
                "successful respawn should clear double_ammo and halve odd reserve");
    }

    private static void pendingEndTeleportCleanupIsOneShotAndClearsOnOnlineCleanup() {
        ZombiesPostGameTeleportService service = new ZombiesPostGameTeleportService();
        RoomId roomId = RoomId.of(BuiltInGameModes.ZOMBIES, "closure-pending");
        UUID online = playerId(40);
        UUID offline = playerId(41);
        ZombiesPostGameTeleportService.TeleportTarget endtp =
                new ZombiesPostGameTeleportService.TeleportTarget("minecraft:overworld", 8, 65, -3, 180.0F, 0.0F);

        ZombiesPostGameTeleportService.CleanupPendingSummary cleanup = service.recordPostGameCleanup(
                roomId,
                List.of(online, offline),
                List.of(online),
                Optional.of(endtp),
                "victory",
                1L);

        require(cleanup.pendingWritten() == 1, "cleanup should write one pending endtp for offline member");
        require(service.peekPending(online).isEmpty(), "online member should not keep pending endtp");
        ZombiesPostGameTeleportService.PendingEndTeleport pending = service.peekPending(offline).orElseThrow();
        require(pending.endTeleport().orElseThrow().equals(endtp), "pending endtp should retain cleanup target");

        ZombiesPostGameTeleportService.PendingEndTeleport consumed = service.consumePending(offline).orElseThrow();
        require(consumed.roomId().equals(roomId), "reconnect cleanup entry should retain room id");
        require(service.consumePending(offline).isEmpty(), "pending endtp should be one-shot for reconnect cleanup");

        service.recordPostGameCleanup(roomId, List.of(offline), List.of(), Optional.empty(), "defeat", 2L);
        ZombiesPostGameTeleportService.CleanupPendingSummary onlineCleanup = service.recordPostGameCleanup(
                roomId,
                List.of(offline),
                List.of(offline),
                Optional.empty(),
                "reset",
                3L);

        require(onlineCleanup.pendingCleared() == 1,
                "later cleanup with player online should clear stale pending reconnect work");
        require(service.peekPending(offline).isEmpty(), "stale pending endtp should be gone after online cleanup");
    }

    private static Services services() {
        ZombiesPlayerStateService players = new ZombiesPlayerStateService();
        ZombiesEconomyService economy = new ZombiesEconomyService(players);
        ZombiesPowerService power = new ZombiesPowerService(economy);
        ZombiesBuffService buffs = new ZombiesBuffService(economy, power);
        return new Services(
                players,
                economy,
                power,
                buffs,
                new ZombiesUltimateMachineService(economy, power),
                new ZombiesIntermissionRespawnService(players, buffs));
    }

    private static ZombiesWeaponInstanceState primary(ZombiesPlayerStateService players, UUID playerId) {
        return players.get(playerId)
                .flatMap(ZombiesPlayerRuntimeState::primaryWeapon)
                .orElseThrow(() -> new AssertionError("expected primary weapon"));
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
            ZombiesUltimateMachineService ultimate,
            ZombiesIntermissionRespawnService respawn
    ) {
    }

    private static final class OrderedIntermissionHarness {
        private final ZombiesIntermissionRespawnService respawn;
        private final List<String> events = new ArrayList<>();

        private OrderedIntermissionHarness(ZombiesIntermissionRespawnService respawn) {
            this.respawn = respawn;
        }

        private void run(List<UUID> members, Set<UUID> successfulTeleports) {
            ZombiesServiceResult<ZombiesIntermissionRespawnService.IntermissionRespawnDecision> decisionResult =
                    respawn.selectRespawnCandidates(members, 100L, 40L);
            requireSuccess(decisionResult, "respawn decision should succeed");
            ZombiesIntermissionRespawnService.IntermissionRespawnDecision decision = decisionResult.value().orElseThrow();

            events.add("objects_updated");
            for (UUID playerId : decision.respawnPlayerIds()) {
                boolean teleportSuccess = successfulTeleports.contains(playerId);
                events.add("teleport:" + playerId + ":" + (teleportSuccess ? "success" : "failed"));
                if (!teleportSuccess) {
                    continue;
                }
                requireSuccess(respawn.prepareStateForRespawn(playerId), "successful teleport should prepare respawn");
                events.add("prepare:" + playerId);
            }
        }
    }
}
