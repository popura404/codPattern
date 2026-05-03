package com.cdp.codpattern.app.zombies.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ZombiesReadyVoteServiceCompatTest {
    private static final UUID PLAYER_ONE = uuid(1);
    private static final UUID PLAYER_TWO = uuid(2);
    private static final UUID PLAYER_THREE = uuid(3);

    private ZombiesReadyVoteServiceCompatTest() {
    }

    public static void main(String[] args) {
        readyCanOnlyToggleWhileWaiting();
        allReadyReturnsFalseForEmptyCollection();
        startVoteRequiresEverySnapshotMemberReady();
        requiredVotesCeilsAndClamps();
        snapshotMemberLeavingFailsActiveVote();
        rejectVoteCanMakeVoteImpossibleToPass();
        allRespondedWithoutPassingFailsActiveVote();
        timeoutFailsActiveVote();
    }

    private static void readyCanOnlyToggleWhileWaiting() {
        ReadyHooks hooks = new ReadyHooks();
        ZombiesReadyService readyService = new ZombiesReadyService(hooks);

        hooks.waiting = false;
        require(!readyService.setPlayerReady(PLAYER_ONE, true), "ready change outside waiting should fail");
        require(!readyService.isPlayerReady(PLAYER_ONE), "player should not become ready outside waiting");
        require(readyService.knownPlayers().isEmpty(), "failed ready change should not add known player");
        require(hooks.dirtyCount == 0, "failed ready change should not mark room dirty");

        hooks.waiting = true;
        require(readyService.setPlayerReady(PLAYER_ONE, true), "ready change while waiting should succeed");
        require(readyService.isPlayerReady(PLAYER_ONE), "player should become ready while waiting");
        require(readyService.knownPlayers().contains(PLAYER_ONE), "ready change should add known player");
        require(hooks.dirtyCount == 1, "changed ready state should mark room dirty");

        require(readyService.setPlayerReady(PLAYER_ONE, false), "unready change while waiting should succeed");
        require(!readyService.isPlayerReady(PLAYER_ONE), "player should become unready while waiting");
        require(hooks.dirtyCount == 2, "changed unready state should mark room dirty");
    }

    private static void allReadyReturnsFalseForEmptyCollection() {
        ZombiesReadyService readyService = new ZombiesReadyService(new ReadyHooks());

        require(!readyService.areAllReady(List.of()), "empty ready collection should be false");
        require(!readyService.areAllReady(null), "null ready collection should be false");
    }

    private static void startVoteRequiresEverySnapshotMemberReady() {
        VoteHooks hooks = new VoteHooks(PLAYER_ONE, PLAYER_TWO);
        hooks.readyPlayers.add(PLAYER_ONE);
        ZombiesStartVoteService voteService = new ZombiesStartVoteService(hooks);

        require(!voteService.initiateStartVote(PLAYER_ONE), "vote should not start while a member is unready");
        requireLastFailure(voteService, ZombiesStartVoteService.FailureReason.PLAYERS_NOT_READY);
        require(hooks.failedReasons.equals(List.of(ZombiesStartVoteService.FailureReason.PLAYERS_NOT_READY)),
                "unready start failure should be reported to hooks");
        require(voteService.activeVoteSnapshot().isEmpty(), "failed start should not create active vote");
    }

    private static void requiredVotesCeilsAndClamps() {
        require(ZombiesStartVoteService.requiredVotes(0, 100) == 0, "zero members should require zero votes");
        require(ZombiesStartVoteService.requiredVotes(3, 67) == 3, "67 percent of 3 should ceil to 3");
        require(ZombiesStartVoteService.requiredVotes(3, 66) == 2, "66 percent of 3 should ceil to 2");
        require(ZombiesStartVoteService.requiredVotes(3, 1) == 1, "positive percent should clamp to at least one");
        require(ZombiesStartVoteService.requiredVotes(3, 0) == 1, "zero percent should clamp to at least one");
        require(ZombiesStartVoteService.requiredVotes(3, 200) == 3, "over 100 percent should clamp to total members");
    }

    private static void snapshotMemberLeavingFailsActiveVote() {
        VoteHooks hooks = readyVoteHooks(100, PLAYER_ONE, PLAYER_TWO);
        ZombiesStartVoteService voteService = new ZombiesStartVoteService(hooks);

        require(voteService.initiateStartVote(PLAYER_ONE), "ready members should be able to start vote");
        long voteId = activeVoteId(voteService);

        hooks.members.remove(PLAYER_TWO);
        voteService.onSnapshotMemberLeft(PLAYER_TWO);

        require(voteService.activeVoteSnapshot().isEmpty(), "snapshot member leaving should clear active vote");
        requireLastFailure(voteService, ZombiesStartVoteService.FailureReason.PLAYER_LEFT);
        require(hooks.failedReasons.equals(List.of(ZombiesStartVoteService.FailureReason.PLAYER_LEFT)),
                "snapshot member leaving should report PLAYER_LEFT");
        require(!voteService.submitVoteResponse(PLAYER_ONE, voteId, true), "cleared vote should reject stale responses");
        requireLastFailure(voteService, ZombiesStartVoteService.FailureReason.STALE_VOTE);
    }

    private static void rejectVoteCanMakeVoteImpossibleToPass() {
        VoteHooks hooks = readyVoteHooks(100, PLAYER_ONE, PLAYER_TWO, PLAYER_THREE);
        ZombiesStartVoteService voteService = new ZombiesStartVoteService(hooks);

        require(voteService.initiateStartVote(PLAYER_ONE), "ready members should be able to start vote");
        long voteId = activeVoteId(voteService);

        require(!voteService.submitVoteResponse(PLAYER_ONE, voteId, false),
                "single rejection should make unanimous vote impossible");
        require(voteService.activeVoteSnapshot().isEmpty(), "impossible vote should clear active vote");
        requireLastFailure(voteService, ZombiesStartVoteService.FailureReason.IMPOSSIBLE_TO_PASS);
        require(hooks.failedReasons.equals(List.of(ZombiesStartVoteService.FailureReason.IMPOSSIBLE_TO_PASS)),
                "impossible vote should report IMPOSSIBLE_TO_PASS");
    }

    private static void allRespondedWithoutPassingFailsActiveVote() {
        VoteHooks hooks = readyVoteHooks(1, PLAYER_ONE, PLAYER_TWO, PLAYER_THREE);
        ZombiesStartVoteService voteService = new ZombiesStartVoteService(hooks);

        require(voteService.initiateStartVote(PLAYER_ONE), "ready members should be able to start vote");
        ZombiesStartVoteService.VoteSnapshot started = voteService.activeVoteSnapshot()
                .orElseThrow(() -> new AssertionError("vote should be active"));
        require(started.requiredVotes() == 1, "1 percent of 3 should clamp to 1 vote");

        require(!voteService.submitVoteResponse(PLAYER_ONE, started.voteId(), false), "first rejection should not pass");
        require(voteService.activeVoteSnapshot().isPresent(), "vote should remain active while still passable");
        require(!voteService.submitVoteResponse(PLAYER_TWO, started.voteId(), false), "second rejection should not pass");
        require(voteService.activeVoteSnapshot().isPresent(), "vote should remain active until all members respond");
        require(!voteService.submitVoteResponse(PLAYER_THREE, started.voteId(), false), "third rejection should fail vote");
        require(voteService.activeVoteSnapshot().isEmpty(), "failed vote should clear active vote");

        ZombiesStartVoteService.VoteSnapshot failed = hooks.failedSnapshots.get(0);
        require(failed.rejected().containsAll(List.of(PLAYER_ONE, PLAYER_TWO, PLAYER_THREE)),
                "failed snapshot should retain rejected responses");
        requireLastFailure(voteService, ZombiesStartVoteService.FailureReason.IMPOSSIBLE_TO_PASS);
        require(hooks.failedReasons.equals(List.of(ZombiesStartVoteService.FailureReason.IMPOSSIBLE_TO_PASS)),
                "all currently resolvable rejected-response failures should report impossible to pass");
    }

    private static void timeoutFailsActiveVote() {
        VoteHooks hooks = readyVoteHooks(51, PLAYER_ONE, PLAYER_TWO);
        hooks.timeoutTicks = 2;
        ZombiesStartVoteService voteService = new ZombiesStartVoteService(hooks);

        require(voteService.initiateStartVote(PLAYER_ONE), "ready members should be able to start vote");
        voteService.tickVoteSession();
        require(voteService.activeVoteSnapshot().isPresent(), "vote should remain active before timeout reaches zero");

        voteService.tickVoteSession();
        require(voteService.activeVoteSnapshot().isEmpty(), "timeout should clear active vote");
        requireLastFailure(voteService, ZombiesStartVoteService.FailureReason.TIMEOUT);
        require(hooks.failedReasons.equals(List.of(ZombiesStartVoteService.FailureReason.TIMEOUT)),
                "timeout should report TIMEOUT");
    }

    private static VoteHooks readyVoteHooks(int votePercent, UUID... players) {
        VoteHooks hooks = new VoteHooks(players);
        hooks.votePercent = votePercent;
        hooks.readyPlayers.addAll(hooks.members);
        return hooks;
    }

    private static long activeVoteId(ZombiesStartVoteService voteService) {
        Optional<ZombiesStartVoteService.VoteSnapshot> snapshot = voteService.activeVoteSnapshot();
        require(snapshot.isPresent(), "vote should be active");
        return snapshot.get().voteId();
    }

    private static void requireLastFailure(
            ZombiesStartVoteService voteService,
            ZombiesStartVoteService.FailureReason expected
    ) {
        Optional<ZombiesStartVoteService.FailureReason> actual = voteService.lastFailureReason();
        require(actual.isPresent(), "expected last failure " + expected);
        require(actual.get() == expected, "expected last failure " + expected + " but was " + actual.get());
    }

    private static UUID uuid(int value) {
        return new UUID(0L, value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class ReadyHooks implements ZombiesReadyService.Hooks {
        private boolean waiting = true;
        private int dirtyCount;

        @Override
        public boolean isWaitingPhase() {
            return waiting;
        }

        @Override
        public void markRoomListDirty() {
            dirtyCount++;
        }
    }

    private static final class VoteHooks implements ZombiesStartVoteService.Hooks {
        private final Set<UUID> members = new LinkedHashSet<>();
        private final Set<UUID> readyPlayers = new LinkedHashSet<>();
        private final List<ZombiesStartVoteService.VoteSnapshot> startedSnapshots = new ArrayList<>();
        private final List<ZombiesStartVoteService.VoteSnapshot> progressSnapshots = new ArrayList<>();
        private final List<ZombiesStartVoteService.VoteSnapshot> passedSnapshots = new ArrayList<>();
        private final List<ZombiesStartVoteService.VoteSnapshot> failedSnapshots = new ArrayList<>();
        private final List<ZombiesStartVoteService.FailureReason> failedReasons = new ArrayList<>();
        private boolean waiting = true;
        private int minPlayers = 1;
        private int votePercent = 51;
        private int timeoutTicks = ZombiesStartVoteService.DEFAULT_TIMEOUT_TICKS;
        private int dirtyCount;

        private VoteHooks(UUID... members) {
            this.members.addAll(List.of(members));
        }

        @Override
        public Collection<UUID> currentMembers() {
            return List.copyOf(members);
        }

        @Override
        public boolean isWaitingPhase() {
            return waiting;
        }

        @Override
        public boolean isPlayerReady(UUID playerId) {
            return readyPlayers.contains(playerId);
        }

        @Override
        public int minPlayersToStart() {
            return minPlayers;
        }

        @Override
        public int votePercentageToStart() {
            return votePercent;
        }

        @Override
        public int voteTimeoutTicks() {
            return timeoutTicks;
        }

        @Override
        public void onVoteStarted(ZombiesStartVoteService.VoteSnapshot snapshot) {
            startedSnapshots.add(snapshot);
        }

        @Override
        public void onVoteProgress(ZombiesStartVoteService.VoteSnapshot snapshot) {
            progressSnapshots.add(snapshot);
        }

        @Override
        public void onVotePassed(ZombiesStartVoteService.VoteSnapshot snapshot) {
            passedSnapshots.add(snapshot);
        }

        @Override
        public void onVoteFailed(
                ZombiesStartVoteService.VoteSnapshot snapshot,
                ZombiesStartVoteService.FailureReason reason
        ) {
            if (snapshot != null) {
                failedSnapshots.add(snapshot);
            }
            failedReasons.add(reason);
        }

        @Override
        public void markRoomListDirty() {
            dirtyCount++;
        }
    }
}
