package com.cdp.codpattern.app.match.runtime.ready;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class DefaultReadyStateServiceCompatTest {
    private static final UUID PLAYER = new UUID(0L, 1L);
    private static final UUID OUTSIDER = new UUID(0L, 2L);

    private DefaultReadyStateServiceCompatTest() {
    }

    public static void main(String[] args) {
        acceptedAndChangedRemainIndependent();
        initializationRemovalAndClearPoliciesRemainIndependent();
        System.out.println("PASS default ready-state service compat");
    }

    private static void acceptedAndChangedRemainIndependent() {
        Map<UUID, Boolean> states = new LinkedHashMap<>();
        RecordingPolicy policy = new RecordingPolicy();
        DefaultReadyStateService service = new DefaultReadyStateService(states, policy);

        require(!service.setReady(OUTSIDER, true).accepted(), "non-member mutation should be rejected");
        require(!service.knownPlayers().contains(OUTSIDER), "rejected mutation must not register identity");

        DefaultReadyStateService.OperationResult firstFalse = service.setReady(PLAYER, false);
        require(firstFalse.accepted() && !firstFalse.changed(),
                "accepted false write for a new identity should not report a value mutation");
        require(service.knownPlayers().contains(PLAYER), "accepted write should register the identity");
        require(policy.acceptedMutations == 1 && policy.changedMutations == 0,
                "policy must see accepted and changed outcomes separately");

        DefaultReadyStateService.OperationResult firstTrue = service.setReady(PLAYER, true);
        DefaultReadyStateService.OperationResult repeatedTrue = service.setReady(PLAYER, true);
        require(firstTrue.accepted() && firstTrue.changed(), "first true write should change state");
        require(repeatedTrue.accepted() && !repeatedTrue.changed(),
                "idempotent true write should remain accepted without a mutation");
        require(policy.acceptedMutations == 3 && policy.changedMutations == 1,
                "accepted-write sync can run three times while dirty notification runs once");
    }

    private static void initializationRemovalAndClearPoliciesRemainIndependent() {
        RecordingPolicy policy = new RecordingPolicy();
        DefaultReadyStateService service = new DefaultReadyStateService(new LinkedHashMap<>(), policy);

        service.setReady(PLAYER, true);
        require(service.initialize(PLAYER).changed(), "initialization should force true state back to false");
        require(!service.isReady(PLAYER), "initialized player should be unready");
        require(policy.initializations == 1, "initialization policy should run independently");

        require(service.remove(PLAYER).changed(), "removing a known identity should report changed");
        require(!service.remove(PLAYER).changed(), "repeated removal should be idempotent");
        service.clear();
        require(policy.removals == 2, "removal policy should observe both attempts");
        require(policy.clears == 1, "clear policy should run even for an already empty store");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class RecordingPolicy implements DefaultReadyStateService.Policy {
        private int acceptedMutations;
        private int changedMutations;
        private int initializations;
        private int removals;
        private int clears;

        @Override
        public boolean canMutate(UUID playerId) {
            return PLAYER.equals(playerId);
        }

        @Override
        public void onInitialized(UUID playerId, DefaultReadyStateService.OperationResult result) {
            initializations++;
        }

        @Override
        public void onMutation(
                UUID playerId,
                boolean ready,
                DefaultReadyStateService.OperationResult result
        ) {
            if (result.accepted()) {
                acceptedMutations++;
            }
            if (result.changed()) {
                changedMutations++;
            }
        }

        @Override
        public void onRemoved(UUID playerId, DefaultReadyStateService.OperationResult result) {
            removals++;
        }

        @Override
        public void onCleared(DefaultReadyStateService.OperationResult result) {
            clears++;
        }
    }
}
