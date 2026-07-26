package com.cdp.codpattern.app.match.runtime.vote;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class RoomVoteEngineCompatTest {
    private static final UUID ONE = new UUID(0L, 1L);
    private static final UUID TWO = new UUID(0L, 2L);
    private static final UUID THREE = new UUID(0L, 3L);

    private RoomVoteEngineCompatTest() {
    }

    public static void main(String[] args) {
        failOnDeparturePolicyPreservesSnapshotVote();
        recalculatePolicyPreservesPvpVote();
        timeoutAndResponseFailuresAreExplicit();
        System.out.println("PASS room vote engine compat");
    }

    private static void failOnDeparturePolicyPreservesSnapshotVote() {
        Harness harness = new Harness(Set.of(ONE, TWO));
        harness.departurePolicy = RoomVoteEngine.MemberDeparturePolicy.FAIL_ACTIVE_VOTE;
        harness.freezeThreshold = true;
        harness.percent = 100;
        RoomVoteEngine<String> engine = harness.engine();

        require(engine.initiate("START", ONE), "snapshot vote should start");
        engine.memberDeparted(TWO);
        require(engine.activeSnapshot().isEmpty(), "snapshot member departure should fail the vote");
        require(harness.failures.equals(List.of(RoomVoteEngine.FailureReason.MEMBER_LEFT)),
                "snapshot member departure should report MEMBER_LEFT");
    }

    private static void recalculatePolicyPreservesPvpVote() {
        Harness harness = new Harness(Set.of(ONE, TWO, THREE));
        harness.departurePolicy = RoomVoteEngine.MemberDeparturePolicy.REMOVE_AND_RECALCULATE;
        harness.percent = 60;
        RoomVoteEngine<String> engine = harness.engine();

        require(engine.initiate("START", ONE), "PVP vote should start");
        long voteId = engine.activeSnapshot().orElseThrow().voteId();
        require(!engine.submit(ONE, voteId, true), "one of three should remain below 60 percent");
        engine.memberDeparted(TWO);
        require(engine.activeSnapshot().isPresent(), "one of two should remain below 60 percent");
        engine.memberDeparted(THREE);
        require(engine.activeSnapshot().isEmpty(), "one of one should pass after recalculation");
        require(harness.passed == 1, "recalculated vote should invoke pass once");
    }

    private static void timeoutAndResponseFailuresAreExplicit() {
        Harness harness = new Harness(Set.of(ONE, TWO));
        harness.timeoutTicks = 2;
        RoomVoteEngine<String> engine = harness.engine();
        require(engine.initiate("START", ONE), "vote should start");
        long voteId = engine.activeSnapshot().orElseThrow().voteId();

        require(!engine.submit(THREE, voteId, true), "non-snapshot voter should be rejected");
        require(engine.lastFailureReason().orElseThrow() == RoomVoteEngine.FailureReason.PLAYER_NOT_IN_SNAPSHOT,
                "non-snapshot voter failure should be explicit");
        engine.tick();
        require(engine.activeSnapshot().isPresent(), "vote should remain active before timeout");
        engine.tick();
        require(engine.activeSnapshot().isEmpty(), "vote should clear at timeout");
        require(harness.failures.contains(RoomVoteEngine.FailureReason.TIMEOUT),
                "timeout should be surfaced to the mode listener");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Harness implements RoomVoteEngine.Policy<String>, RoomVoteEngine.Listener<String> {
        private final Set<UUID> members;
        private final List<RoomVoteEngine.FailureReason> failures = new ArrayList<>();
        private RoomVoteEngine.MemberDeparturePolicy departurePolicy =
                RoomVoteEngine.MemberDeparturePolicy.REMOVE_AND_RECALCULATE;
        private int percent = 60;
        private int timeoutTicks = 300;
        private boolean freezeThreshold;
        private int passed;

        private Harness(Collection<UUID> members) {
            this.members = new LinkedHashSet<>(members);
        }

        private RoomVoteEngine<String> engine() {
            return new RoomVoteEngine<>(this, this);
        }

        @Override
        public RoomVoteEngine.StartDecision prepareStart(String kind, UUID initiator, boolean voteActive) {
            if (voteActive) {
                return RoomVoteEngine.StartDecision.rejected(RoomVoteEngine.FailureReason.VOTE_IN_PROGRESS);
            }
            return RoomVoteEngine.StartDecision.accepted(members);
        }

        @Override
        public int requiredVotes(String kind, int memberCount) {
            return RoomVoteEngine.ceilClampedThreshold(memberCount, percent);
        }

        @Override
        public int timeoutTicks(String kind) {
            return timeoutTicks;
        }

        @Override
        public RoomVoteEngine.MemberDeparturePolicy memberDeparturePolicy(String kind) {
            return departurePolicy;
        }

        @Override
        public boolean freezeThresholdAtStart(String kind) {
            return freezeThreshold;
        }

        @Override
        public Optional<RoomVoteEngine.FailureReason> activeFailure(String kind, Set<UUID> currentMembers) {
            return Optional.empty();
        }

        @Override
        public void onPassed(RoomVoteEngine.Snapshot<String> snapshot) {
            passed++;
        }

        @Override
        public void onFailed(RoomVoteEngine.Snapshot<String> snapshot, RoomVoteEngine.FailureReason reason) {
            failures.add(reason);
        }
    }
}
