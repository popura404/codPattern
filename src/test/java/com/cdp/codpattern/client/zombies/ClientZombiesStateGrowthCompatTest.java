package com.cdp.codpattern.client.zombies;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.model.ModePlayerValue;
import com.cdp.codpattern.app.match.model.ModeRuntimeStateSnapshot;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.zombies.model.ZombiesBuffType;
import com.cdp.codpattern.app.zombies.model.ZombiesTeamNames;
import com.cdp.codpattern.app.zombies.sync.ZombiesRuntimeStateKeys;
import com.cdp.codpattern.client.ClientModeRuntimeState;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ClientZombiesStateGrowthCompatTest {
    private ClientZombiesStateGrowthCompatTest() {
    }

    public static void main(String[] args) {
        missingGrowthKeysUseDefaults();
        growthKeysReadFromPlayerValues();
        activeZombieIdsReadFromPlayerValues();
        survivorStatusReadsRoomRosterRuntimeValues();
        roomTeammatesExcludeSelfAndLeftPlayers();
        resultRowsSortByScoreKillsBarriersDeathsThenName();
    }

    private static void missingGrowthKeysUseDefaults() {
        ClientModeRuntimeState.clearAll();
        ClientModeRuntimeState.update(snapshot(Map.of()));

        require(!ClientZombiesState.powerEnabled(), "missing power key should default false");
        require(ClientZombiesState.armorLevel() == 0, "missing armor key should default 0");
        require(ClientZombiesState.primaryUpgradeLevel() == 0, "missing upgrade key should default 0");
        require(!ClientZombiesState.buffEnabled(ZombiesBuffType.DOUBLE_HEALTH.id()), "missing buff key should default false");
        require(ClientZombiesState.ownedBuffIds().isEmpty(), "missing buff keys should expose no owned buffs");
    }

    private static void growthKeysReadFromPlayerValues() {
        ClientModeRuntimeState.clearAll();
        ClientModeRuntimeState.update(snapshot(Map.of(
                ZombiesRuntimeStateKeys.PLAYER_POWER_ENABLED, ModePlayerValue.ofBoolean(true),
                ZombiesRuntimeStateKeys.PLAYER_ARMOR_LEVEL, ModePlayerValue.ofInt(2),
                ZombiesRuntimeStateKeys.PLAYER_WEAPON_PRIMARY_UPGRADE, ModePlayerValue.ofInt(3),
                ZombiesRuntimeStateKeys.playerBuff(ZombiesBuffType.DOUBLE_HEALTH.id()), ModePlayerValue.ofBoolean(true),
                ZombiesRuntimeStateKeys.playerBuff(ZombiesBuffType.SCORE_MULTIPLIER.id()), ModePlayerValue.ofBoolean(true),
                ZombiesRuntimeStateKeys.playerBuff(ZombiesBuffType.DOUBLE_AMMO.id()), ModePlayerValue.ofBoolean(false)
        )));

        require(ClientZombiesState.powerEnabled(), "power key should read true");
        require(ClientZombiesState.armorLevel() == 2, "armor key should read level");
        require(ClientZombiesState.primaryUpgradeLevel() == 3, "upgrade key should read level");
        require(ClientZombiesState.buffEnabled(ZombiesBuffType.DOUBLE_HEALTH.id()), "enabled buff should read true");
        require(!ClientZombiesState.buffEnabled(ZombiesBuffType.DOUBLE_AMMO.id()), "disabled buff should read false");
        require(ClientZombiesState.ownedBuffIds().equals(List.of(
                ZombiesBuffType.DOUBLE_HEALTH.id(),
                ZombiesBuffType.SCORE_MULTIPLIER.id()
        )), "owned buff ids should include enabled buffs in stable order");
    }

    private static void activeZombieIdsReadFromPlayerValues() {
        ClientModeRuntimeState.clearAll();
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        ClientModeRuntimeState.update(snapshot(Map.of(
                ZombiesRuntimeStateKeys.ACTIVE_ZOMBIE_ENTITY_IDS,
                ModePlayerValue.ofString(first + ",invalid," + second + ",")
        )));

        require(ClientZombiesState.activeZombieEntityIds().equals(Set.of(first, second)),
                "active zombie ids should ignore malformed values and expose parsed UUIDs");
    }

    private static void survivorStatusReadsRoomRosterRuntimeValues() {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        ModeRuntimeStateSnapshot snapshot = snapshot(Map.of(
                ZombiesRuntimeStateKeys.survivorLifeState(playerId.toString()), ModePlayerValue.ofString("ALIVE"),
                ZombiesRuntimeStateKeys.survivorConnectionState(playerId.toString()), ModePlayerValue.ofString("ONLINE"),
                ZombiesRuntimeStateKeys.survivorPoints(playerId.toString()), ModePlayerValue.ofInt(1250),
                ZombiesRuntimeStateKeys.survivorTotalEarnedPoints(playerId.toString()), ModePlayerValue.ofInt(1800),
                ZombiesRuntimeStateKeys.survivorKills(playerId.toString()), ModePlayerValue.ofInt(12),
                ZombiesRuntimeStateKeys.survivorDeaths(playerId.toString()), ModePlayerValue.ofInt(1),
                ZombiesRuntimeStateKeys.survivorBarriersOpened(playerId.toString()), ModePlayerValue.ofInt(3),
                ZombiesRuntimeStateKeys.survivorArmorLevel(playerId.toString()), ModePlayerValue.ofInt(2),
                ZombiesRuntimeStateKeys.survivorHealth(playerId.toString()), ModePlayerValue.ofDouble(14.5D),
                ZombiesRuntimeStateKeys.survivorMaxHealth(playerId.toString()), ModePlayerValue.ofDouble(20.0D)
        ));

        List<ClientZombiesState.SurvivorStatus> survivors = ClientZombiesState.buildSurvivorStatuses(
                snapshot,
                Map.of(ZombiesTeamNames.SURVIVORS, List.of(player(playerId, "teammate", true))),
                UUID.fromString("00000000-0000-0000-0000-000000000999"));

        require(survivors.size() == 1, "one room survivor should be exposed");
        ClientZombiesState.SurvivorStatus survivor = survivors.get(0);
        require(survivor.playerId().equals(playerId), "survivor id should come from room roster");
        require("teammate".equals(survivor.name()), "survivor name should come from room roster");
        require("ALIVE".equals(survivor.lifeState()), "life state should come from runtime values");
        require("ONLINE".equals(survivor.connectionState()), "connection state should come from runtime values");
        require(survivor.points() == 1250, "survivor points should come from runtime values");
        require(survivor.totalEarnedPoints() == 1800, "survivor total earned should come from runtime values");
        require(survivor.kills() == 12, "survivor kills should come from runtime values");
        require(survivor.deaths() == 1, "survivor deaths should come from runtime values");
        require(survivor.barriersOpened() == 3, "survivor barriers should come from runtime values");
        require(survivor.armorLevel() == 2, "survivor armor should come from runtime values");
        requireClose(survivor.health(), 14.5D, "survivor health should come from runtime values");
        requireClose(survivor.maxHealth(), 20.0D, "survivor max health should come from runtime values");
        require(!survivor.self(), "different local player id should not be marked self");
    }

    private static void roomTeammatesExcludeSelfAndLeftPlayers() {
        UUID self = UUID.fromString("00000000-0000-0000-0000-000000000201");
        UUID teammate = UUID.fromString("00000000-0000-0000-0000-000000000202");
        UUID left = UUID.fromString("00000000-0000-0000-0000-000000000203");
        ModeRuntimeStateSnapshot snapshot = snapshot(Map.of(
                ZombiesRuntimeStateKeys.survivorConnectionState(left.toString()), ModePlayerValue.ofString("LEFT")
        ));

        List<ClientZombiesState.SurvivorStatus> survivors = ClientZombiesState.buildSurvivorStatuses(
                snapshot,
                Map.of(ZombiesTeamNames.SURVIVORS, List.of(
                        player(self, "self", true),
                        player(teammate, "teammate", true),
                        player(left, "left", true))),
                self);

        List<ClientZombiesState.SurvivorStatus> teammates = ClientZombiesState.filterRoomTeammates(survivors);
        require(teammates.size() == 1, "room teammates should exclude local player and LEFT survivor");
        require(teammates.get(0).playerId().equals(teammate), "remaining teammate should be the non-local active room survivor");
        requireClose(teammates.get(0).health(), 1.0D, "missing active teammate health should default alive");
        ClientZombiesState.SurvivorStatus leftStatus = survivors.stream()
                .filter(status -> status.playerId().equals(left))
                .findFirst()
                .orElseThrow();
        requireClose(leftStatus.health(), 0.0D, "LEFT survivor missing health should default to zero");
    }

    private static void resultRowsSortByScoreKillsBarriersDeathsThenName() {
        UUID alpha = UUID.fromString("00000000-0000-0000-0000-000000000301");
        UUID bravo = UUID.fromString("00000000-0000-0000-0000-000000000302");
        UUID charlie = UUID.fromString("00000000-0000-0000-0000-000000000303");
        UUID delta = UUID.fromString("00000000-0000-0000-0000-000000000304");
        UUID echo = UUID.fromString("00000000-0000-0000-0000-000000000305");
        ModeRuntimeStateSnapshot snapshot = snapshot(Map.ofEntries(
                Map.entry(ZombiesRuntimeStateKeys.survivorName(alpha.toString()), ModePlayerValue.ofString("alpha")),
                Map.entry(ZombiesRuntimeStateKeys.survivorTotalEarnedPoints(alpha.toString()), ModePlayerValue.ofInt(900)),
                Map.entry(ZombiesRuntimeStateKeys.survivorKills(alpha.toString()), ModePlayerValue.ofInt(9)),
                Map.entry(ZombiesRuntimeStateKeys.survivorBarriersOpened(alpha.toString()), ModePlayerValue.ofInt(1)),
                Map.entry(ZombiesRuntimeStateKeys.survivorDeaths(alpha.toString()), ModePlayerValue.ofInt(0)),
                Map.entry(ZombiesRuntimeStateKeys.survivorName(bravo.toString()), ModePlayerValue.ofString("bravo")),
                Map.entry(ZombiesRuntimeStateKeys.survivorTotalEarnedPoints(bravo.toString()), ModePlayerValue.ofInt(900)),
                Map.entry(ZombiesRuntimeStateKeys.survivorKills(bravo.toString()), ModePlayerValue.ofInt(8)),
                Map.entry(ZombiesRuntimeStateKeys.survivorBarriersOpened(bravo.toString()), ModePlayerValue.ofInt(4)),
                Map.entry(ZombiesRuntimeStateKeys.survivorDeaths(bravo.toString()), ModePlayerValue.ofInt(0)),
                Map.entry(ZombiesRuntimeStateKeys.survivorName(charlie.toString()), ModePlayerValue.ofString("charlie")),
                Map.entry(ZombiesRuntimeStateKeys.survivorTotalEarnedPoints(charlie.toString()), ModePlayerValue.ofInt(900)),
                Map.entry(ZombiesRuntimeStateKeys.survivorKills(charlie.toString()), ModePlayerValue.ofInt(8)),
                Map.entry(ZombiesRuntimeStateKeys.survivorBarriersOpened(charlie.toString()), ModePlayerValue.ofInt(3)),
                Map.entry(ZombiesRuntimeStateKeys.survivorDeaths(charlie.toString()), ModePlayerValue.ofInt(0)),
                Map.entry(ZombiesRuntimeStateKeys.survivorName(delta.toString()), ModePlayerValue.ofString("delta")),
                Map.entry(ZombiesRuntimeStateKeys.survivorTotalEarnedPoints(delta.toString()), ModePlayerValue.ofInt(900)),
                Map.entry(ZombiesRuntimeStateKeys.survivorKills(delta.toString()), ModePlayerValue.ofInt(8)),
                Map.entry(ZombiesRuntimeStateKeys.survivorBarriersOpened(delta.toString()), ModePlayerValue.ofInt(3)),
                Map.entry(ZombiesRuntimeStateKeys.survivorDeaths(delta.toString()), ModePlayerValue.ofInt(2)),
                Map.entry(ZombiesRuntimeStateKeys.survivorName(echo.toString()), ModePlayerValue.ofString("echo")),
                Map.entry(ZombiesRuntimeStateKeys.survivorTotalEarnedPoints(echo.toString()), ModePlayerValue.ofInt(700)),
                Map.entry(ZombiesRuntimeStateKeys.survivorKills(echo.toString()), ModePlayerValue.ofInt(20)),
                Map.entry(ZombiesRuntimeStateKeys.survivorBarriersOpened(echo.toString()), ModePlayerValue.ofInt(9)),
                Map.entry(ZombiesRuntimeStateKeys.survivorDeaths(echo.toString()), ModePlayerValue.ofInt(0))
        ));

        List<ClientZombiesState.ResultRow> rows = ClientZombiesState.buildResultRows(
                snapshot,
                Map.of(ZombiesTeamNames.SURVIVORS, List.of(
                        player(alpha, "alpha", true),
                        player(bravo, "bravo", true),
                        player(charlie, "charlie", true),
                        player(delta, "delta", false),
                        player(echo, "echo", true))),
                alpha);

        require(rows.stream().map(ClientZombiesState.ResultRow::playerId).toList().equals(List.of(
                alpha,
                bravo,
                charlie,
                delta,
                echo
        )), "result rows should sort by total score, kills, barriers, deaths, then name");
        require(rows.get(0).self(), "local player row should be marked self");
        require(!rows.get(3).alive(), "dead result row should expose non-alive status");
    }

    private static ModeRuntimeStateSnapshot snapshot(Map<String, ModePlayerValue> values) {
        return new ModeRuntimeStateSnapshot(
                RoomId.of(BuiltInGameModes.ZOMBIES, "growth").encode(),
                "WAVE_ACTIVE",
                0,
                List.of(),
                values,
                List.of(),
                1L);
    }

    private static PlayerInfo player(UUID playerId, String name, boolean alive) {
        return new PlayerInfo(playerId, name, false, 0, 0, 0, alive, false, 0);
    }

    private static void requireClose(double actual, double expected, String message) {
        if (Math.abs(actual - expected) > 0.000001D) {
            throw new AssertionError(message + "; expected=" + expected + "; actual=" + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
