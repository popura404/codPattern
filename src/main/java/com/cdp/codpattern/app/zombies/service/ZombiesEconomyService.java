package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.ModePlayerValue;
import com.cdp.codpattern.app.zombies.model.ZombiesBuffState;
import com.cdp.codpattern.app.zombies.model.ZombiesBuffType;
import com.cdp.codpattern.app.zombies.model.ZombiesConnectionState;
import com.cdp.codpattern.app.zombies.model.ZombiesPlayerRuntimeState;
import com.cdp.codpattern.app.zombies.model.ZombiesWaveMobEntry;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ZombiesEconomyService {
    public static final int DEFAULT_KILL_POINTS = 10;
    public static final int DEFAULT_ASSIST_POINTS = 3;

    private final ZombiesPlayerStateService playerStateService;

    public ZombiesEconomyService(ZombiesPlayerStateService playerStateService) {
        this.playerStateService = playerStateService;
    }

    public Optional<ZombiesPlayerRuntimeState> state(UUID playerId) {
        return playerStateService.get(playerId);
    }

    ZombiesPlayerRuntimeState stateOrCreate(UUID playerId) {
        return playerStateService.getOrCreate(playerId);
    }

    public int displayPoints(UUID playerId) {
        return playerStateService.get(playerId)
                .map(ZombiesPlayerRuntimeState::displayPoints)
                .orElse(0);
    }

    public void recordBarrierOpened(UUID playerId) {
        if (playerId != null) {
            playerStateService.getOrCreate(playerId).addBarrierOpened();
        }
    }

    public ZombiesServiceResult<Double> addPoints(UUID playerId, double amount) {
        if (!Double.isFinite(amount) || amount < 0.0D) {
            return ZombiesServiceResult.failure(ZombiesErrorCode.ECONOMY_INVALID_COST);
        }
        ZombiesPlayerRuntimeState state = playerStateService.getOrCreate(playerId);
        state.addPoints(amount);
        return ZombiesServiceResult.success(state.points());
    }

    public ZombiesServiceResult<Double> awardKill(UUID killerId, ZombiesWaveMobEntry mobEntry) {
        return awardKill(killerId, rewardOrDefault(mobEntry == null ? null : mobEntry.getKillPoints(), DEFAULT_KILL_POINTS));
    }

    public ZombiesServiceResult<Double> awardKill(UUID killerId, double killPoints) {
        ZombiesPlayerRuntimeState killer = playerStateService.getOrCreate(killerId);
        if (killer.connectionState().isLeft()) {
            return ZombiesServiceResult.failure(ZombiesErrorCode.PLAYER_LEFT);
        }
        killer.addKill();
        killer.addPoints(applyScoreMultiplier(killerId, killPoints));
        return ZombiesServiceResult.success(killer.points());
    }

    public ZombiesServiceResult<Double> awardAssist(UUID contributorId, ZombiesWaveMobEntry mobEntry) {
        return awardAssist(contributorId, rewardOrDefault(
                mobEntry == null ? null : mobEntry.getAssistPoints(),
                DEFAULT_ASSIST_POINTS
        ));
    }

    public ZombiesServiceResult<Double> awardAssist(UUID contributorId, double assistPoints) {
        ZombiesPlayerRuntimeState contributor = playerStateService.getOrCreate(contributorId);
        if (contributor.connectionState().isLeft()) {
            return ZombiesServiceResult.failure(ZombiesErrorCode.PLAYER_LEFT);
        }
        contributor.addAssist();
        contributor.addPoints(applyScoreMultiplier(contributorId, assistPoints));
        return ZombiesServiceResult.success(contributor.points());
    }

    public ZombiesServiceResult<RewardSummary> awardKillAndAssists(
            UUID killerId,
            Collection<UUID> contributors,
            ZombiesWaveMobEntry mobEntry
    ) {
        int killPoints = rewardOrDefault(mobEntry == null ? null : mobEntry.getKillPoints(), DEFAULT_KILL_POINTS);
        int assistPoints = rewardOrDefault(mobEntry == null ? null : mobEntry.getAssistPoints(), DEFAULT_ASSIST_POINTS);
        return awardKillAndAssists(killerId, contributors, killPoints, assistPoints);
    }

    public ZombiesServiceResult<RewardSummary> awardKillAndAssists(
            UUID killerId,
            Collection<UUID> contributors,
            double killPoints,
            double assistPoints
    ) {
        if (killerId == null) {
            return ZombiesServiceResult.failure(ZombiesErrorCode.of("economy.missing_killer"));
        }
        ZombiesServiceResult<Double> killResult = awardKill(killerId, killPoints);
        if (!killResult.success()) {
            return ZombiesServiceResult.failure(killResult.code(), killResult.params(), killResult.logMessage());
        }
        int awardedAssists = 0;
        if (contributors != null) {
            for (UUID contributorId : contributors) {
                if (contributorId != null && !contributorId.equals(killerId)) {
                    ZombiesServiceResult<Double> assistResult = awardAssist(contributorId, assistPoints);
                    if (assistResult.success()) {
                        awardedAssists++;
                    }
                }
            }
        }
        return ZombiesServiceResult.success(new RewardSummary(killerId, killPoints, awardedAssists, assistPoints));
    }

    public ZombiesServiceResult<Double> spend(UUID playerId, double cost) {
        if (!ZombiesPlayerRuntimeState.isValidCost(cost)) {
            return ZombiesServiceResult.failure(ZombiesErrorCode.ECONOMY_INVALID_COST, costParams(cost), "Invalid zombies spend cost");
        }
        ZombiesPlayerRuntimeState state = playerStateService.getOrCreate(playerId);
        synchronized (state) {
            ZombiesServiceResult<Void> eligibility = validateSpendEligibility(state, cost);
            if (!eligibility.success()) {
                return ZombiesServiceResult.failure(eligibility.code(), eligibility.params(), eligibility.logMessage());
            }
            state.spendPoints(cost);
            return ZombiesServiceResult.success(state.points());
        }
    }

    public <T> ZombiesServiceResult<T> spendAtomically(UUID playerId, double cost, SpendAction<T> action) {
        if (!ZombiesPlayerRuntimeState.isValidCost(cost)) {
            return ZombiesServiceResult.failure(ZombiesErrorCode.ECONOMY_INVALID_COST, costParams(cost), "Invalid zombies spend cost");
        }
        ZombiesPlayerRuntimeState state = playerStateService.getOrCreate(playerId);
        synchronized (state) {
            ZombiesServiceResult<Void> eligibility = validateSpendEligibility(state, cost);
            if (!eligibility.success()) {
                return ZombiesServiceResult.failure(eligibility.code(), eligibility.params(), eligibility.logMessage());
            }
            ZombiesServiceResult<T> result = action == null ? ZombiesServiceResult.success(null) : action.apply(state);
            if (!result.success()) {
                return result;
            }
            state.spendPoints(cost);
            return result;
        }
    }

    public ZombiesServiceResult<Void> validateSpendEligibility(UUID playerId, double cost) {
        return validateSpendEligibility(playerStateService.getOrCreate(playerId), cost);
    }

    ZombiesServiceResult<Void> validateSpendEligibility(ZombiesPlayerRuntimeState state, double cost) {
        if (!state.lifeState().isAlive()) {
            return ZombiesServiceResult.failure(ZombiesErrorCode.PLAYER_DEAD);
        }
        if (state.connectionState() == ZombiesConnectionState.LEFT) {
            return ZombiesServiceResult.failure(ZombiesErrorCode.PLAYER_LEFT);
        }
        if (state.connectionState() == ZombiesConnectionState.OFFLINE) {
            return ZombiesServiceResult.failure(ZombiesErrorCode.PLAYER_OFFLINE);
        }
        if (state.points() + 0.000001D < cost) {
            return ZombiesServiceResult.failure(ZombiesErrorCode.ECONOMY_NOT_ENOUGH_POINTS, spendParams(state, cost), "");
        }
        return ZombiesServiceResult.ok();
    }

    private static Map<String, ModePlayerValue> spendParams(ZombiesPlayerRuntimeState state, double cost) {
        Map<String, ModePlayerValue> params = new LinkedHashMap<>();
        params.put("points", ModePlayerValue.ofInt(state.displayPoints()));
        params.put("cost", ModePlayerValue.ofInt((int) Math.floor(cost)));
        return params;
    }

    private static Map<String, ModePlayerValue> costParams(double cost) {
        Map<String, ModePlayerValue> params = new LinkedHashMap<>();
        params.put("cost", ModePlayerValue.ofDouble(cost));
        return params;
    }

    private static int rewardOrDefault(Integer value, int defaultValue) {
        return value == null || value < 0 ? defaultValue : value;
    }

    private double applyScoreMultiplier(UUID playerId, double points) {
        double multiplier = scoreMultiplier(playerId);
        double adjusted = points * multiplier;
        return Double.isFinite(adjusted) ? adjusted : points;
    }

    private double scoreMultiplier(UUID playerId) {
        if (playerId == null) {
            return 1.0D;
        }
        return playerStateService.get(playerId)
                .flatMap(state -> state.buff(ZombiesBuffType.SCORE_MULTIPLIER))
                .map(ZombiesBuffState::multiplier)
                .filter(multiplier -> Double.isFinite(multiplier) && multiplier > 0.0D)
                .orElse(1.0D);
    }

    @FunctionalInterface
    public interface SpendAction<T> {
        ZombiesServiceResult<T> apply(ZombiesPlayerRuntimeState state);
    }

    public record RewardSummary(
            UUID killerId,
            double killPoints,
            int assistCount,
            double assistPoints
    ) {
    }
}
