package com.cdp.codpattern.app.zombies.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ZombiesStarterKitDistributorCompatTest {
    private static final UUID PLAYER_ONE = playerId(1);
    private static final UUID PLAYER_TWO = playerId(2);
    private static final UUID PLAYER_THREE = playerId(3);

    private ZombiesStarterKitDistributorCompatTest() {
    }

    public static void main(String[] args) {
        validationFailureLeavesAllTargetsUntouched();
        runtimeFailureRestoresAlreadyMutatedTargets();
    }

    private static void validationFailureLeavesAllTargetsUntouched() {
        FakeStarterKitTarget first = new FakeStarterKitTarget(PLAYER_ONE, "starter-one", List.of("old-one"));
        FakeStarterKitTarget missingWeapon = new FakeStarterKitTarget(PLAYER_TWO, "", List.of("old-two"));

        ZombiesServiceResult<Void> result = ZombiesStarterKitDistributor.applyPreparedStarterWeapons(
                List.of(first, missingWeapon));

        requireFailure(result, "missing starter weapon should fail during preflight");
        require(first.inventory().equals(List.of("old-one")),
                "preflight failure should not mutate earlier targets");
        require(missingWeapon.inventory().equals(List.of("old-two")),
                "preflight failure should not mutate failing target");
        require(first.clearCount == 0 && first.applyCount == 0 && first.restoreCount == 0,
                "preflight failure should not perform mutation or compensation");
    }

    private static void runtimeFailureRestoresAlreadyMutatedTargets() {
        FakeStarterKitTarget first = new FakeStarterKitTarget(PLAYER_ONE, "starter-one", List.of("old-one"));
        FakeStarterKitTarget second = new FakeStarterKitTarget(PLAYER_TWO, "starter-two", List.of("old-two"));
        FakeStarterKitTarget third = new FakeStarterKitTarget(PLAYER_THREE, "starter-three", List.of("old-three"));
        second.failOnClear = true;

        ZombiesServiceResult<Void> result = ZombiesStarterKitDistributor.applyPreparedStarterWeapons(
                List.of(first, second, third));

        requireFailure(result, "runtime exception during starter apply should fail");
        require(first.inventory().equals(List.of("old-one")),
                "compensation should restore target mutated before the exception");
        require(second.inventory().equals(List.of("old-two")),
                "compensation should restore target that threw after clearing");
        require(third.inventory().equals(List.of("old-three")),
                "targets after the exception should stay untouched");
        require(first.restoreCount == 1, "first target snapshot should be restored");
        require(second.restoreCount == 1, "failing target snapshot should be restored");
        require(third.restoreCount == 0, "untouched target should not be restored");
        require(first.applyCount == 1, "first target should have reached starter weapon apply before failure");
        require(third.applyCount == 0, "third target should not be reached after failure");
    }

    private static void requireFailure(ZombiesServiceResult<?> result, String message) {
        require(!result.success(), message + ": expected failure");
        require(ZombiesErrorCode.STARTUP_STARTER_WEAPON_MISSING.equals(result.code()),
                message + ": expected starter kit failure but was " + result.code());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static UUID playerId(int suffix) {
        return new UUID(0L, suffix);
    }

    private static final class FakeStarterKitTarget implements ZombiesStarterKitDistributor.StarterKitTarget {
        private final UUID playerId;
        private final String starterWeapon;
        private List<String> inventory;
        private boolean runtimeCleared;
        private boolean failOnClear;
        private int clearCount;
        private int applyCount;
        private int syncCount;
        private int restoreCount;

        private FakeStarterKitTarget(UUID playerId, String starterWeapon, List<String> inventory) {
            this.playerId = playerId;
            this.starterWeapon = starterWeapon;
            this.inventory = new ArrayList<>(inventory);
        }

        @Override
        public UUID playerId() {
            return playerId;
        }

        @Override
        public boolean canApplyStarterWeapon() {
            return starterWeapon != null && !starterWeapon.isBlank();
        }

        @Override
        public ZombiesStarterKitDistributor.StarterKitSnapshot captureSnapshot() {
            return new FakeStarterKitSnapshot(inventory, runtimeCleared);
        }

        @Override
        public void clearInventoryAndRuntime() {
            clearCount++;
            inventory.clear();
            runtimeCleared = true;
            if (failOnClear) {
                throw new IllegalStateException("simulated clear failure");
            }
        }

        @Override
        public void applyStarterWeapon() {
            applyCount++;
            inventory.clear();
            inventory.add(starterWeapon);
        }

        @Override
        public void syncInventory() {
            syncCount++;
        }

        @Override
        public void restoreSnapshot(ZombiesStarterKitDistributor.StarterKitSnapshot snapshot) {
            FakeStarterKitSnapshot fakeSnapshot = (FakeStarterKitSnapshot) snapshot;
            inventory = new ArrayList<>(fakeSnapshot.inventory());
            runtimeCleared = fakeSnapshot.runtimeCleared();
            restoreCount++;
        }

        private List<String> inventory() {
            return List.copyOf(inventory);
        }
    }

    private record FakeStarterKitSnapshot(
            List<String> inventory,
            boolean runtimeCleared
    ) implements ZombiesStarterKitDistributor.StarterKitSnapshot {
        private FakeStarterKitSnapshot {
            inventory = List.copyOf(inventory);
        }
    }
}
