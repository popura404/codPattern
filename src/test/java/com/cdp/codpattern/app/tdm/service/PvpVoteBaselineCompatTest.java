package com.cdp.codpattern.app.tdm.service;

import com.cdp.codpattern.app.match.runtime.vote.RoomVoteEngine;
import com.cdp.codpattern.network.match.VoteDialogPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PvpVoteBaselineCompatTest {
    private PvpVoteBaselineCompatTest() {
    }

    public static void main(String[] args) throws Exception {
        verifyThresholds();
        verifyTimeout();
        verifyLeaveRecalculatesCurrentPvpMembership();
        verifyImpossibleVoteFailsEarly();
        System.out.println("PASS PVP vote baseline compat");
    }

    private static void verifyThresholds() throws Exception {
        RecordingHooks hooks = new RecordingHooks();
        hooks.startPercent = 60;
        hooks.endPercent = 75;
        VoteService service = new VoteService(hooks);

        requireEquals(2, requiredVotes(service, "START", 3), "60 percent of three requires two votes");
        requireEquals(3, requiredVotes(service, "END", 3), "75 percent of three requires three votes");
        requireEquals(1, requiredVotes(service, "START", 1), "single-player vote requires one vote");
        requireEquals(3, requiredVotes(service, "END", 4), "75 percent of four requires three votes");
    }

    private static void verifyTimeout() {
        VoteHarness harness = new VoteHarness(Set.of(UUID.randomUUID()));
        RoomVoteEngine<String> engine = harness.engine();
        requireTrue(engine.initiate("START", harness.members.iterator().next()), "test vote should start");

        for (int tick = 0; tick < (15 * 20) - 1; tick++) {
            engine.tick();
        }
        requireTrue(engine.activeSnapshot().isPresent(), "vote remains active before the 15-second timeout");
        engine.tick();
        requireTrue(engine.activeSnapshot().isEmpty(), "vote expires at the 15-second timeout");
        requireEquals(List.of(RoomVoteEngine.FailureReason.TIMEOUT), harness.failures,
                "vote timeout should report exactly once");
    }

    private static void verifyLeaveRecalculatesCurrentPvpMembership() {
        UUID accepted = UUID.randomUUID();
        UUID leavingOne = UUID.randomUUID();
        UUID leavingTwo = UUID.randomUUID();
        VoteHarness harness = new VoteHarness(Set.of(accepted, leavingOne, leavingTwo));
        harness.percent = 60;
        RoomVoteEngine<String> engine = harness.engine();

        requireTrue(engine.initiate("START", accepted), "test vote should start");
        long voteId = engine.activeSnapshot().orElseThrow().voteId();
        requireFalse(engine.submit(accepted, voteId, true),
                "one of three votes does not initially pass a 60-percent vote");
        engine.memberDeparted(leavingOne);
        requireTrue(engine.activeSnapshot().isPresent(),
                "one accepted vote out of two remains below the recalculated threshold");
        engine.memberDeparted(leavingTwo);
        requireTrue(engine.activeSnapshot().isEmpty(),
                "departures shrink the PVP voter set and allow the remaining accepted vote to pass");
        requireEquals(1, harness.passed, "recalculated start vote invokes the pass hook once");
    }

    private static void verifyImpossibleVoteFailsEarly() {
        UUID firstReject = UUID.randomUUID();
        UUID secondReject = UUID.randomUUID();
        UUID unresolved = UUID.randomUUID();
        VoteHarness harness = new VoteHarness(Set.of(firstReject, secondReject, unresolved));
        harness.percent = 60;
        RoomVoteEngine<String> engine = harness.engine();

        requireTrue(engine.initiate("START", firstReject), "test vote should start");
        long voteId = engine.activeSnapshot().orElseThrow().voteId();
        engine.submit(firstReject, voteId, false);
        requireTrue(engine.activeSnapshot().isPresent(), "one rejection still leaves a possible 60-percent majority");
        engine.submit(secondReject, voteId, false);
        requireTrue(engine.activeSnapshot().isEmpty(),
                "vote fails as soon as accepted plus unresolved voters cannot reach the threshold");
        requireEquals(List.of(RoomVoteEngine.FailureReason.IMPOSSIBLE_TO_PASS), harness.failures,
                "impossible vote should report its neutral failure reason");
        requireEquals(0, harness.passed, "failed vote does not invoke the start hook");
    }

    private static int requiredVotes(VoteService service, String voteTypeName, int totalPlayers) throws Exception {
        Class<?> voteType = Class.forName(VoteService.class.getName() + "$VoteType");
        Method method = VoteService.class.getDeclaredMethod("getRequiredVotes", voteType, int.class);
        method.setAccessible(true);
        return (int) method.invoke(service, enumValue(voteType, voteTypeName), totalPlayers);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(Class<?> enumType, String name) {
        return Enum.valueOf((Class<? extends Enum>) enumType.asSubclass(Enum.class), name);
    }

    private static void requireTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void requireFalse(boolean value, String message) {
        requireTrue(!value, message);
    }

    private static void requireEquals(Object expected, Object actual, String message) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static final class VoteHarness implements RoomVoteEngine.Policy<String>, RoomVoteEngine.Listener<String> {
        private final Set<UUID> members;
        private final List<RoomVoteEngine.FailureReason> failures = new java.util.ArrayList<>();
        private int percent = 60;
        private int passed;

        private VoteHarness(Collection<UUID> members) {
            this.members = new LinkedHashSet<>(members);
        }

        private RoomVoteEngine<String> engine() {
            return new RoomVoteEngine<>(this, this);
        }

        @Override
        public RoomVoteEngine.StartDecision prepareStart(String kind, UUID initiator, boolean voteActive) {
            return RoomVoteEngine.StartDecision.accepted(members);
        }

        @Override
        public int requiredVotes(String kind, int memberCount) {
            return RoomVoteEngine.ceilClampedThreshold(memberCount, percent);
        }

        @Override
        public int timeoutTicks(String kind) {
            return 15 * 20;
        }

        @Override
        public RoomVoteEngine.MemberDeparturePolicy memberDeparturePolicy(String kind) {
            return RoomVoteEngine.MemberDeparturePolicy.REMOVE_AND_RECALCULATE;
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

    private static final class RecordingHooks implements VoteService.Hooks {
        private int startPercent = 60;
        private int endPercent = 75;

        @Override
        public Player getPlayer(UUID playerId) {
            return null;
        }

        @Override
        public List<ServerPlayer> getJoinedPlayers() {
            return List.of();
        }

        @Override
        public boolean isWaitingPhase() {
            return true;
        }

        @Override
        public boolean isPlayingOrWarmupPhase() {
            return true;
        }

        @Override
        public boolean isPlayerReady(UUID playerId) {
            return true;
        }

        @Override
        public boolean hasMatchEndTeleportPoint() {
            return true;
        }

        @Override
        public int getMinPlayersToStart() {
            return 1;
        }

        @Override
        public int getVotePercentageToStart() {
            return startPercent;
        }

        @Override
        public int getVotePercentageToEnd() {
            return endPercent;
        }

        @Override
        public String getMapName() {
            return "baseline";
        }

        @Override
        public void broadcastToJoinedPlayers(Component message) {
        }

        @Override
        public void sendVoteDialog(VoteDialogPacket packet, ServerPlayer player) {
        }

        @Override
        public void notifyPlayer(Player player, Component message) {
        }

        @Override
        public void onStartVotePassed() {
        }

        @Override
        public void onEndVotePassed() {
        }

        @Override
        public void markRoomListDirty() {
        }
    }
}
