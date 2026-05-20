package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.config.zombies.ZombiesRulesConfig;
import com.cdp.codpattern.config.zombies.ZombiesRulesValidator;

public final class ZombiesSpawnPointDistanceWeightingCompatTest {
    private ZombiesSpawnPointDistanceWeightingCompatTest() {
    }

    public static void main(String[] args) {
        defaultCurveAppliesSmallDistanceBias();
        disabledConfigKeepsOriginalWeight();
        validatorRejectsReversedDistanceBands();
        System.out.println("PASS zombies spawn point distance weighting compat");
    }

    private static void defaultCurveAppliesSmallDistanceBias() {
        ZombiesRulesConfig.SpawnPointWeighting weighting = new ZombiesRulesConfig.SpawnPointWeighting();

        requireClose(
                0.65D,
                ZombiesMobSpawnService.distanceMultiplier(4.0D, 4.0D, weighting),
                "too-close spawn points should use the minimum multiplier");
        requireClose(
                1.15D,
                ZombiesMobSpawnService.distanceMultiplier(30.0D, 30.0D, weighting),
                "ideal-distance spawn points should receive a small boost");
        requireClose(
                0.85D,
                ZombiesMobSpawnService.distanceMultiplier(150.0D, 150.0D, weighting),
                "far spawn points should be mildly reduced, not disabled");
        requireClose(
                0.65D,
                ZombiesMobSpawnService.distanceMultiplier(30.0D, 4.0D, weighting),
                "any survivor too close to the spawn should clamp the multiplier down");
        requireClose(
                2.3D,
                ZombiesMobSpawnService.effectiveSpawnWeight(2.0D, 30.0D, 30.0D, weighting),
                "effective weight should be original map weight times distance multiplier");
    }

    private static void disabledConfigKeepsOriginalWeight() {
        ZombiesRulesConfig.SpawnPointWeighting weighting = new ZombiesRulesConfig.SpawnPointWeighting();
        weighting.setEnabled(false);

        requireClose(
                1.0D,
                ZombiesMobSpawnService.distanceMultiplier(4.0D, 4.0D, weighting),
                "disabled distance weighting should not alter the multiplier");
        requireClose(
                2.0D,
                ZombiesMobSpawnService.effectiveSpawnWeight(2.0D, 4.0D, 4.0D, weighting),
                "disabled distance weighting should keep the original map weight");
    }

    private static void validatorRejectsReversedDistanceBands() {
        ZombiesRulesConfig config = new ZombiesRulesConfig();
        config.getSpawnPointWeighting().setTooCloseDistance(32.0D);
        config.getSpawnPointWeighting().setIdealMinDistance(16.0D);

        boolean hasIssue = new ZombiesRulesValidator().validate(config).stream()
                .anyMatch(issue -> ZombiesRulesValidator.RULES_INVALID_SPAWN_POINT_WEIGHTING.equals(issue.code()));
        require(hasIssue, "reversed distance bands should produce a spawn point weighting validation issue");
    }

    private static void requireClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.0001D) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
