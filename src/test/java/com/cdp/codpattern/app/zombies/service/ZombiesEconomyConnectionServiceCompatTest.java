package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.zombies.model.ZombiesBuffState;
import com.cdp.codpattern.app.zombies.model.ZombiesBuffType;
import com.cdp.codpattern.app.zombies.model.ZombiesConnectionState;
import com.cdp.codpattern.app.zombies.model.ZombiesLifeState;
import com.cdp.codpattern.app.zombies.model.ZombiesPlayerRuntimeState;
import com.cdp.codpattern.app.zombies.sync.ZombiesRuntimeStateKeys;

import java.util.List;
import java.util.UUID;

public final class ZombiesEconomyConnectionServiceCompatTest {
    private ZombiesEconomyConnectionServiceCompatTest() {
    }

    public static void main(String[] args) {
        displayPointsFloorsFractionalPoints();
        totalEarnedPointsTracksAwardsButNotSpendOrRefund();
        spendSuccessDeductsPoints();
        spendFailuresKeepBalanceAndReturnExpectedErrors();
        killAndAssistsSkipsKillerAndLeftContributor();
        scoreMultiplierAppliesToKillAndAssistRewards();
        offlineGraceCountsAlivePlayersUntilTimeout();
        activeRoundReconnectEligibilityKeepsExistingStats();
    }

    private static void displayPointsFloorsFractionalPoints() {
        ZombiesPlayerStateService players = new ZombiesPlayerStateService();
        ZombiesEconomyService economy = new ZombiesEconomyService(players);
        UUID playerId = playerId(1);

        requireSuccess(economy.addPoints(playerId, 12.99D), "adding fractional points should succeed");

        require(economy.displayPoints(playerId) == 12, "display points should floor fractional points");
        requireIntPlayerValue(players, playerId, "points", 12, "player values should expose floored points");
    }

    private static void totalEarnedPointsTracksAwardsButNotSpendOrRefund() {
        ZombiesPlayerStateService players = new ZombiesPlayerStateService();
        ZombiesEconomyService economy = new ZombiesEconomyService(players);
        UUID playerId = playerId(30);

        requireSuccess(economy.addPoints(playerId, 20.5D), "setup earned points should succeed");
        requireSuccess(economy.spend(playerId, 7.25D), "spend should not affect total earned points");
        players.getOrCreate(playerId).refundPoints(4.0D);
        requireSuccess(economy.awardKill(playerId, 10.0D), "kill reward should count as earned points");

        ZombiesPlayerRuntimeState state = players.get(playerId).orElseThrow();
        requireClose(state.points(), 27.25D, "balance should include refund and kill reward");
        requireClose(state.totalEarnedPoints(), 30.5D, "total earned should include grants and kill rewards only");
        require(state.displayTotalEarnedPoints() == 30, "display total earned should floor fractional total");
        requireIntPlayerValue(players, playerId, ZombiesRuntimeStateKeys.PLAYER_TOTAL_EARNED_POINTS, 30,
                "player values should expose floored total earned points");
    }

    private static void spendSuccessDeductsPoints() {
        ZombiesPlayerStateService players = new ZombiesPlayerStateService();
        ZombiesEconomyService economy = new ZombiesEconomyService(players);
        UUID playerId = playerId(2);

        economy.addPoints(playerId, 20.5D);
        ZombiesServiceResult<Double> result = economy.spend(playerId, 7.25D);

        requireSuccess(result, "spend should succeed for an online alive player with enough points");
        requirePoints(players, playerId, 13.25D, "successful spend should deduct exact cost");
        require(economy.displayPoints(playerId) == 13, "display points should floor remaining points");
    }

    private static void spendFailuresKeepBalanceAndReturnExpectedErrors() {
        assertSpendFailureKeepsBalance(
                playerId(3),
                state -> {
                },
                3.0D,
                4.0D,
                ZombiesErrorCode.ECONOMY_NOT_ENOUGH_POINTS,
                "not enough points should fail without deducting");
        assertSpendFailureKeepsBalance(
                playerId(4),
                ZombiesPlayerRuntimeState::markDeadSpectating,
                10.0D,
                4.0D,
                ZombiesErrorCode.PLAYER_DEAD,
                "dead player should fail without deducting");
        assertSpendFailureKeepsBalance(
                playerId(5),
                state -> state.markOffline(100L),
                10.0D,
                4.0D,
                ZombiesErrorCode.PLAYER_OFFLINE,
                "offline player should fail without deducting");
        assertSpendFailureKeepsBalance(
                playerId(6),
                ZombiesPlayerRuntimeState::markLeft,
                10.0D,
                4.0D,
                ZombiesErrorCode.PLAYER_LEFT,
                "left player should fail without deducting");
    }

    private static void killAndAssistsSkipsKillerAndLeftContributor() {
        ZombiesPlayerStateService players = new ZombiesPlayerStateService();
        ZombiesEconomyService economy = new ZombiesEconomyService(players);
        UUID killerId = playerId(7);
        UUID assisterId = playerId(8);
        UUID leftContributorId = playerId(9);
        players.getOrCreate(leftContributorId).markLeft();

        ZombiesServiceResult<ZombiesEconomyService.RewardSummary> result = economy.awardKillAndAssists(
                killerId,
                List.of(killerId, assisterId, leftContributorId),
                10.0D,
                3.0D);

        requireSuccess(result, "kill and assist reward should succeed when killer is eligible");
        ZombiesEconomyService.RewardSummary summary = result.value().orElseThrow();
        require(summary.assistCount() == 1, "only eligible non-killer contributors should count as assists");
        requireState(players, killerId, 10.0D, 1, 0, "killer should receive kill points but no self assist");
        requireState(players, assisterId, 3.0D, 0, 1, "eligible contributor should receive one assist");
        requireState(players, leftContributorId, 0.0D, 0, 0, "left contributor should not receive assist credit");
    }

    private static void scoreMultiplierAppliesToKillAndAssistRewards() {
        ZombiesPlayerStateService players = new ZombiesPlayerStateService();
        ZombiesEconomyService economy = new ZombiesEconomyService(players);
        UUID killerId = playerId(20);
        UUID assisterId = playerId(21);
        players.getOrCreate(killerId).addBuff(ZombiesBuffState.defaultFor(ZombiesBuffType.SCORE_MULTIPLIER));
        players.getOrCreate(assisterId).addBuff(new ZombiesBuffState(ZombiesBuffType.SCORE_MULTIPLIER, 2.0D));

        ZombiesServiceResult<ZombiesEconomyService.RewardSummary> result = economy.awardKillAndAssists(
                killerId,
                List.of(assisterId),
                10.0D,
                3.0D);

        requireSuccess(result, "score multiplier reward should succeed");
        requireState(players, killerId, 12.5D, 1, 0,
                "killer score_multiplier should scale kill reward");
        requireState(players, assisterId, 6.0D, 0, 1,
                "assister score_multiplier should scale assist reward");
    }

    private static void offlineGraceCountsAlivePlayersUntilTimeout() {
        ZombiesPlayerStateService players = new ZombiesPlayerStateService();
        ZombiesConnectionStateService connections = new ZombiesConnectionStateService(players, 20L);
        UUID onlineId = playerId(10);
        UUID offlineId = playerId(11);

        players.markAlive(onlineId);
        players.markAlive(offlineId);
        connections.markOffline(offlineId, 100L);

        require(players.aliveCount(119L, connections.offlineGraceTicks()) == 2,
                "offline alive player should count inside offline grace");
        require(connections.isWithinOfflineGrace(offlineId, 119L),
                "connection service should report offline player inside grace");

        List<UUID> timedOut = connections.applyOfflineGraceTimeouts(121L);

        require(timedOut.equals(List.of(offlineId)), "offline player should time out after grace expires");
        ZombiesPlayerRuntimeState offlineState = players.get(offlineId).orElseThrow();
        require(offlineState.lifeState() == ZombiesLifeState.DEAD_SPECTATING,
                "timed out offline player should become dead spectating");
        require(offlineState.connectionState() == ZombiesConnectionState.OFFLINE,
                "timeout should not rewrite offline connection state");
        require(players.aliveCount(121L, connections.offlineGraceTicks()) == 1,
                "timed out offline player should no longer count as alive");
    }

    private static void activeRoundReconnectEligibilityKeepsExistingStats() {
        ZombiesPlayerStateService players = new ZombiesPlayerStateService();
        ZombiesConnectionStateService connections = new ZombiesConnectionStateService(players, 20L);
        UUID reconnectingId = playerId(12);

        ZombiesPlayerRuntimeState state = players.getOrCreate(reconnectingId);
        state.addPoints(42.0D);
        state.addKill();
        connections.markOffline(reconnectingId, 200L);

        require(players.canRestoreActiveRoundPlayer(reconnectingId),
                "offline player with existing runtime state should be eligible for active-round reconnect restore");

        connections.markOnline(reconnectingId);

        ZombiesPlayerRuntimeState restored = players.get(reconnectingId).orElseThrow();
        requireClose(restored.points(), 42.0D, "active-round reconnect should keep point balance");
        require(restored.kills() == 1, "active-round reconnect should keep kill stats");
        require(restored.connectionState() == ZombiesConnectionState.ONLINE,
                "active-round reconnect should mark the existing runtime state online");

        restored.markLeft();
        require(!players.canRestoreActiveRoundPlayer(reconnectingId),
                "explicitly left player should not be eligible for active-round reconnect restore");
        require(!players.canRestoreActiveRoundPlayer(playerId(13)),
                "unknown player should not be eligible for active-round reconnect restore");
    }

    private static void assertSpendFailureKeepsBalance(
            UUID playerId,
            StateMutation mutation,
            double startingPoints,
            double cost,
            ZombiesErrorCode expectedCode,
            String message
    ) {
        ZombiesPlayerStateService players = new ZombiesPlayerStateService();
        ZombiesEconomyService economy = new ZombiesEconomyService(players);
        economy.addPoints(playerId, startingPoints);
        ZombiesPlayerRuntimeState state = players.getOrCreate(playerId);
        mutation.apply(state);

        ZombiesServiceResult<Double> result = economy.spend(playerId, cost);

        requireFailure(result, expectedCode, message);
        requirePoints(players, playerId, startingPoints, message);
    }

    private static UUID playerId(int suffix) {
        return new UUID(0L, suffix);
    }

    private static void requireState(
            ZombiesPlayerStateService players,
            UUID playerId,
            double points,
            int kills,
            int assists,
            String message
    ) {
        ZombiesPlayerRuntimeState state = players.get(playerId).orElseThrow();
        requireClose(state.points(), points, message + ": points");
        require(state.kills() == kills, message + ": kills");
        require(state.assists() == assists, message + ": assists");
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

    @FunctionalInterface
    private interface StateMutation {
        void apply(ZombiesPlayerRuntimeState state);
    }
}
