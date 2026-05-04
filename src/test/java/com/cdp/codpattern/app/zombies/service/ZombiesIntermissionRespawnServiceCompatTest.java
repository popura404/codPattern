package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.zombies.map.ZombiesMapSnapshot;
import com.cdp.codpattern.app.zombies.model.ZombiesArmorState;
import com.cdp.codpattern.app.zombies.model.ZombiesBuffType;
import com.cdp.codpattern.app.zombies.model.ZombiesLifeState;
import com.cdp.codpattern.app.zombies.model.ZombiesPlayerRuntimeState;
import com.cdp.codpattern.app.zombies.model.ZombiesWeaponInstanceState;

import java.util.List;
import java.util.UUID;

public final class ZombiesIntermissionRespawnServiceCompatTest {
    private ZombiesIntermissionRespawnServiceCompatTest() {
    }

    public static void main(String[] args) {
        singlePlayerDoesNotRespawn();
        multiplayerWithAliveMemberSelectsOnlyOnlineDeadSpectators();
        offlineAliveWithinGraceCountsAsRespawnPrerequisite();
        noAliveMemberSelectsNoRespawns();
        prepareClearsBuffsMarksAliveAndPreservesPersistentState();
        fullMemberSpawnPlanPreservesDeadPlayerSpawnIndex();
    }

    private static void singlePlayerDoesNotRespawn() {
        Services services = services();
        UUID only = playerId(1);
        services.players.registerPlayers(List.of(only));
        services.players.markDeadSpectating(only);

        ZombiesIntermissionRespawnService.IntermissionRespawnDecision decision =
                decide(services, List.of(only), 100L, 40L);

        require(!decision.hasAliveMember(), "single player decision should not require alive prerequisite");
        require(decision.respawnPlayerIds().isEmpty(), "single player should not respawn");
    }

    private static void multiplayerWithAliveMemberSelectsOnlyOnlineDeadSpectators() {
        Services services = services();
        UUID alive = playerId(10);
        UUID onlineDead = playerId(11);
        UUID offlineDead = playerId(12);
        UUID leftDead = playerId(13);
        List<UUID> members = List.of(alive, onlineDead, offlineDead, leftDead);
        services.players.registerPlayers(members);
        services.players.markDeadSpectating(onlineDead);
        services.players.markDeadSpectating(offlineDead);
        services.players.markOffline(offlineDead, 90L);
        services.players.markDeadSpectating(leftDead);
        services.players.markLeft(leftDead);

        ZombiesIntermissionRespawnService.IntermissionRespawnDecision decision =
                decide(services, members, 100L, 40L);

        require(decision.hasAliveMember(), "online alive member should satisfy respawn prerequisite");
        require(decision.respawnPlayerIds().equals(List.of(onlineDead)),
                "only online DEAD_SPECTATING members should be selected");
    }

    private static void offlineAliveWithinGraceCountsAsRespawnPrerequisite() {
        Services services = services();
        UUID offlineAlive = playerId(20);
        UUID onlineDead = playerId(21);
        List<UUID> members = List.of(offlineAlive, onlineDead);
        services.players.registerPlayers(members);
        services.players.markOffline(offlineAlive, 80L);
        services.players.markDeadSpectating(onlineDead);

        ZombiesIntermissionRespawnService.IntermissionRespawnDecision decision =
                decide(services, members, 100L, 40L);

        require(decision.hasAliveMember(), "offline alive member inside grace should count as alive");
        require(decision.respawnPlayerIds().equals(List.of(onlineDead)),
                "offline alive prerequisite should allow online dead teammate respawn");
    }

    private static void noAliveMemberSelectsNoRespawns() {
        Services services = services();
        UUID timedOutOfflineAlive = playerId(30);
        UUID onlineDead = playerId(31);
        UUID anotherOnlineDead = playerId(32);
        List<UUID> members = List.of(timedOutOfflineAlive, onlineDead, anotherOnlineDead);
        services.players.registerPlayers(members);
        services.players.markOffline(timedOutOfflineAlive, 10L);
        services.players.markDeadSpectating(onlineDead);
        services.players.markDeadSpectating(anotherOnlineDead);

        ZombiesIntermissionRespawnService.IntermissionRespawnDecision decision =
                decide(services, members, 100L, 40L);

        require(!decision.hasAliveMember(), "offline alive member outside grace should not count as alive");
        require(decision.respawnPlayerIds().isEmpty(), "no alive member should suppress respawn candidates");
    }

    private static void prepareClearsBuffsMarksAliveAndPreservesPersistentState() {
        Services services = services();
        UUID playerId = playerId(40);
        ZombiesPlayerRuntimeState state = services.players.getOrCreate(playerId);
        state.setPoints(2_000.75D);
        state.setPrimaryWeapon(new ZombiesWeaponInstanceState("tacz:m4a1", 2, 1, 1.75D, 101, 210));
        state.setArmor(new ZombiesArmorState(2, 0.65D));
        services.economy.addPoints(playerId, 500.0D);
        requireSuccess(services.power.turnOn(playerId, 100.0D), "setup power should succeed");
        requireSuccess(services.buffs.purchaseBuff(playerId, ZombiesBuffType.DOUBLE_AMMO, 250.0D, true),
                "setup double ammo buff should succeed");
        requireSuccess(services.buffs.purchaseBuff(playerId, ZombiesBuffType.DOUBLE_HEALTH, 250.0D, true),
                "setup double health buff should succeed");
        services.players.markDeadSpectating(playerId);
        double pointsBeforeRespawn = state.points();
        ZombiesWeaponInstanceState weaponBeforeRespawn = state.primaryWeapon().orElseThrow();
        ZombiesArmorState armorBeforeRespawn = state.armor().orElseThrow();

        ZombiesServiceResult<ZombiesIntermissionRespawnService.IntermissionRespawnStateChange> result =
                services.respawn.prepareStateForRespawn(playerId);

        requireSuccess(result, "prepareStateForRespawn should succeed");
        require(result.value().orElseThrow().clearedBuffs() == 2, "prepare should report cleared buffs");
        require(result.value().orElseThrow().halvedReserveAmmo(), "prepare should report double ammo reserve trim");
        require(state.lifeState() == ZombiesLifeState.ALIVE, "prepare should mark player alive");
        require(state.buffs().isEmpty(), "prepare should clear all player buffs");
        requireClose(state.points(), pointsBeforeRespawn, "prepare should preserve points");
        ZombiesWeaponInstanceState weaponAfterRespawn = state.primaryWeapon().orElseThrow();
        require(weaponAfterRespawn.gunId().equals(weaponBeforeRespawn.gunId()), "prepare should preserve gun id");
        require(weaponAfterRespawn.weaponLevel() == weaponBeforeRespawn.weaponLevel(),
                "prepare should preserve weapon level");
        require(weaponAfterRespawn.upgradeLevel() == weaponBeforeRespawn.upgradeLevel(),
                "prepare should preserve upgrade level");
        requireClose(weaponAfterRespawn.damageMultiplier(), weaponBeforeRespawn.damageMultiplier(),
                "prepare should preserve weapon multiplier");
        require(weaponAfterRespawn.reserveAmmo() == 50,
                "prepare should trim double ammo reserve by floor(current / 2)");
        ZombiesArmorState armorAfterRespawn = state.armor().orElseThrow();
        require(armorAfterRespawn.equals(armorBeforeRespawn), "prepare should preserve armor state");
    }

    private static void fullMemberSpawnPlanPreservesDeadPlayerSpawnIndex() {
        Services services = services();
        UUID aliveOne = playerId(50);
        UUID deadOne = playerId(51);
        UUID aliveTwo = playerId(52);
        UUID deadTwo = playerId(53);
        List<UUID> members = List.of(aliveOne, deadOne, aliveTwo, deadTwo);
        services.players.registerPlayers(members);
        services.players.markDeadSpectating(deadOne);
        services.players.markDeadSpectating(deadTwo);

        ZombiesIntermissionRespawnService.IntermissionRespawnDecision decision =
                decide(services, members, 100L, 40L);
        ZombiesMapSnapshot snapshot = ZombiesMapSnapshot.of(
                RoomId.of("zombies", "respawn-spawn-index"),
                "respawn-spawn-index",
                true,
                List.of(initialSpawn(), initialSpawn(), initialSpawn()),
                List.of());
        ZombiesServiceResult<ZombiesSpawnAssignmentService.ZombiesSpawnAssignmentPlan> planResult =
                new ZombiesSpawnAssignmentService().assignFromSnapshot(snapshot, decision.memberIds());
        requireSuccess(planResult, "full member spawn plan should be built");
        List<ZombiesSpawnAssignmentService.ZombiesSpawnAssignment> reviveAssignments = planResult.value()
                .orElseThrow()
                .assignments()
                .stream()
                .filter(assignment -> decision.respawnPlayerIds().contains(assignment.playerId()))
                .toList();

        require(reviveAssignments.size() == 2, "only dead candidates should be selected from full spawn plan");
        require(reviveAssignments.get(0).playerId().equals(deadOne), "first revive assignment should match member order");
        require(reviveAssignments.get(0).memberIndex() == 1, "first dead member index should be preserved");
        require(reviveAssignments.get(0).spawnIndex() == 1, "first dead spawn index should use full member order");
        require(reviveAssignments.get(1).playerId().equals(deadTwo), "second revive assignment should match member order");
        require(reviveAssignments.get(1).memberIndex() == 3, "second dead member index should be preserved");
        require(reviveAssignments.get(1).spawnIndex() == 0, "second dead spawn index should wrap full member order");
    }

    private static ZombiesIntermissionRespawnService.IntermissionRespawnDecision decide(
            Services services,
            List<UUID> members,
            long currentTick,
            long offlineGraceTicks
    ) {
        ZombiesServiceResult<ZombiesIntermissionRespawnService.IntermissionRespawnDecision> result =
                services.respawn.selectRespawnCandidates(members, currentTick, offlineGraceTicks);
        requireSuccess(result, "respawn decision should succeed");
        return result.value().orElseThrow();
    }

    private static Services services() {
        ZombiesPlayerStateService players = new ZombiesPlayerStateService();
        ZombiesEconomyService economy = new ZombiesEconomyService(players);
        ZombiesPowerService power = new ZombiesPowerService(economy);
        ZombiesBuffService buffs = new ZombiesBuffService(economy, power);
        return new Services(players, economy, power, buffs, new ZombiesIntermissionRespawnService(players, buffs));
    }

    private static void requireSuccess(ZombiesServiceResult<?> result, String message) {
        require(result.success(), message + ": " + result.code());
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

    private static ZombiesMapSnapshot.SpawnSnapshot initialSpawn() {
        return new ZombiesMapSnapshot.SpawnSnapshot("", "spawn", "INITIAL", 0, 0.0D, false);
    }

    private record Services(
            ZombiesPlayerStateService players,
            ZombiesEconomyService economy,
            ZombiesPowerService power,
            ZombiesBuffService buffs,
            ZombiesIntermissionRespawnService respawn
    ) {
    }
}
