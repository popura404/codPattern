package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.port.VoteControlPort;
import com.cdp.codpattern.app.match.runtime.vote.RoomVoteEngine;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Zombies start-vote compatibility facade over the neutral room vote engine. */
public final class ZombiesStartVoteService implements VoteControlPort {
    public static final int DEFAULT_TIMEOUT_TICKS = 15 * 20;

    public interface Hooks {
        Collection<UUID> currentMembers();

        boolean isWaitingPhase();

        boolean isPlayerReady(UUID playerId);

        int minPlayersToStart();

        int votePercentageToStart();

        default int voteTimeoutTicks() {
            return DEFAULT_TIMEOUT_TICKS;
        }

        default void onVoteStarted(VoteSnapshot snapshot) {
        }

        default void onVoteProgress(VoteSnapshot snapshot) {
        }

        default void onVotePassed(VoteSnapshot snapshot) {
        }

        default void onVoteStartRejected(UUID initiator, FailureReason reason) {
        }

        default void onVoteFailed(VoteSnapshot snapshot, FailureReason reason) {
        }

        default void markRoomListDirty() {
        }
    }

    public enum FailureReason {
        NOT_WAITING,
        VOTE_IN_PROGRESS,
        EMPTY_SNAPSHOT,
        MIN_PLAYERS,
        PLAYERS_NOT_READY,
        INITIATOR_NOT_MEMBER,
        PLAYER_LEFT,
        IMPOSSIBLE_TO_PASS,
        ALL_RESPONDED_WITHOUT_PASSING,
        TIMEOUT,
        STALE_VOTE,
        PLAYER_NOT_IN_SNAPSHOT,
        ALREADY_VOTED,
        END_VOTE_UNSUPPORTED
    }

    public record VoteSnapshot(
            long voteId,
            UUID initiator,
            Set<UUID> members,
            Set<UUID> accepted,
            Set<UUID> rejected,
            int requiredVotes,
            int timeoutTicksRemaining
    ) {
        public VoteSnapshot {
            members = members == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(members));
            accepted = accepted == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(accepted));
            rejected = rejected == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(rejected));
        }

        public int totalMembers() {
            return members.size();
        }
    }

    private enum VoteKind {
        START
    }

    private final Hooks hooks;
    private final RoomVoteEngine<VoteKind> delegate;
    private FailureReason lastFailureReason;

    public ZombiesStartVoteService(Hooks hooks) {
        this.hooks = Objects.requireNonNull(hooks, "hooks");
        this.delegate = new RoomVoteEngine<>(new VotePolicy(), new VoteListener());
    }

    @Override
    public boolean initiateStartVote(UUID initiator) {
        lastFailureReason = null;
        return delegate.initiate(VoteKind.START, initiator);
    }

    @Override
    public boolean initiateEndVote(UUID initiator) {
        lastFailureReason = FailureReason.END_VOTE_UNSUPPORTED;
        return false;
    }

    @Override
    public boolean submitVoteResponse(UUID playerId, long voteId, boolean accepted) {
        lastFailureReason = null;
        return delegate.submit(playerId, voteId, accepted);
    }

    public void tickVoteSession() {
        delegate.tick();
    }

    public void onSnapshotMemberLeft(UUID playerId) {
        delegate.memberDeparted(playerId);
    }

    public void onPlayerJoined(UUID playerId) {
        // Joins during or after a snapshot do not affect the active vote denominator.
    }

    public Optional<VoteSnapshot> activeVoteSnapshot() {
        return delegate.activeSnapshot().map(ZombiesStartVoteService::toFacadeSnapshot);
    }

    public Optional<FailureReason> lastFailureReason() {
        return Optional.ofNullable(lastFailureReason);
    }

    public void clearActiveVoteSession() {
        delegate.clear();
    }

    public static int requiredVotes(int totalMembers, int votePercent) {
        return RoomVoteEngine.ceilClampedThreshold(totalMembers, votePercent);
    }

    private final class VotePolicy implements RoomVoteEngine.Policy<VoteKind> {
        @Override
        public RoomVoteEngine.StartDecision prepareStart(
                VoteKind kind,
                UUID initiator,
                boolean voteActive
        ) {
            if (initiator == null) {
                return RoomVoteEngine.StartDecision.rejected(
                        RoomVoteEngine.FailureReason.INITIATOR_NOT_MEMBER);
            }
            if (voteActive) {
                return RoomVoteEngine.StartDecision.rejected(RoomVoteEngine.FailureReason.VOTE_IN_PROGRESS);
            }
            if (!hooks.isWaitingPhase()) {
                return RoomVoteEngine.StartDecision.rejected(RoomVoteEngine.FailureReason.NOT_WAITING);
            }

            Set<UUID> members = snapshotMembers(hooks.currentMembers());
            if (members.isEmpty()) {
                return RoomVoteEngine.StartDecision.rejected(RoomVoteEngine.FailureReason.EMPTY_SNAPSHOT);
            }
            if (!members.contains(initiator)) {
                return RoomVoteEngine.StartDecision.rejected(
                        RoomVoteEngine.FailureReason.INITIATOR_NOT_MEMBER);
            }
            if (members.size() < hooks.minPlayersToStart()) {
                return RoomVoteEngine.StartDecision.rejected(RoomVoteEngine.FailureReason.MIN_PLAYERS);
            }
            for (UUID member : members) {
                if (!hooks.isPlayerReady(member)) {
                    return RoomVoteEngine.StartDecision.rejected(
                            RoomVoteEngine.FailureReason.PLAYERS_NOT_READY);
                }
            }
            return RoomVoteEngine.StartDecision.accepted(members);
        }

        @Override
        public int requiredVotes(VoteKind kind, int memberCount) {
            return ZombiesStartVoteService.requiredVotes(memberCount, hooks.votePercentageToStart());
        }

        @Override
        public int timeoutTicks(VoteKind kind) {
            return hooks.voteTimeoutTicks();
        }

        @Override
        public RoomVoteEngine.MemberDeparturePolicy memberDeparturePolicy(VoteKind kind) {
            return RoomVoteEngine.MemberDeparturePolicy.FAIL_ACTIVE_VOTE;
        }

        @Override
        public boolean freezeThresholdAtStart(VoteKind kind) {
            return true;
        }
    }

    private final class VoteListener implements RoomVoteEngine.Listener<VoteKind> {
        @Override
        public void onStartRejected(
                VoteKind kind,
                UUID initiator,
                RoomVoteEngine.FailureReason reason
        ) {
            FailureReason facadeReason = toFacadeReason(reason);
            lastFailureReason = facadeReason;
            hooks.onVoteStartRejected(initiator, facadeReason);
            hooks.markRoomListDirty();
        }

        @Override
        public void onStarted(RoomVoteEngine.Snapshot<VoteKind> snapshot) {
            hooks.onVoteStarted(toFacadeSnapshot(snapshot));
            hooks.markRoomListDirty();
        }

        @Override
        public void onProgress(RoomVoteEngine.Snapshot<VoteKind> snapshot) {
            hooks.onVoteProgress(toFacadeSnapshot(snapshot));
            hooks.markRoomListDirty();
        }

        @Override
        public void onPassed(RoomVoteEngine.Snapshot<VoteKind> snapshot) {
            hooks.onVotePassed(toFacadeSnapshot(snapshot));
            hooks.markRoomListDirty();
        }

        @Override
        public void onFailed(
                RoomVoteEngine.Snapshot<VoteKind> snapshot,
                RoomVoteEngine.FailureReason reason
        ) {
            FailureReason facadeReason = toFacadeReason(reason);
            lastFailureReason = facadeReason;
            hooks.onVoteFailed(toFacadeSnapshot(snapshot), facadeReason);
            hooks.markRoomListDirty();
        }

        @Override
        public void onResponseRejected(UUID playerId, long voteId, RoomVoteEngine.FailureReason reason) {
            lastFailureReason = toFacadeReason(reason);
        }

        @Override
        public void onCleared(boolean hadActiveVote) {
            hooks.markRoomListDirty();
        }
    }

    private static VoteSnapshot toFacadeSnapshot(RoomVoteEngine.Snapshot<VoteKind> snapshot) {
        return new VoteSnapshot(
                snapshot.voteId(),
                snapshot.initiator(),
                snapshot.members(),
                snapshot.accepted(),
                snapshot.rejected(),
                snapshot.requiredVotes(),
                snapshot.timeoutTicksRemaining());
    }

    private static FailureReason toFacadeReason(RoomVoteEngine.FailureReason reason) {
        return switch (reason) {
            case NOT_WAITING -> FailureReason.NOT_WAITING;
            case VOTE_IN_PROGRESS -> FailureReason.VOTE_IN_PROGRESS;
            case EMPTY_SNAPSHOT -> FailureReason.EMPTY_SNAPSHOT;
            case MIN_PLAYERS -> FailureReason.MIN_PLAYERS;
            case PLAYERS_NOT_READY -> FailureReason.PLAYERS_NOT_READY;
            case INITIATOR_MISSING, INITIATOR_NOT_MEMBER -> FailureReason.INITIATOR_NOT_MEMBER;
            case MEMBER_LEFT -> FailureReason.PLAYER_LEFT;
            case IMPOSSIBLE_TO_PASS -> FailureReason.IMPOSSIBLE_TO_PASS;
            case ALL_RESPONDED_WITHOUT_PASSING -> FailureReason.ALL_RESPONDED_WITHOUT_PASSING;
            case TIMEOUT -> FailureReason.TIMEOUT;
            case STALE_VOTE -> FailureReason.STALE_VOTE;
            case PLAYER_NOT_IN_SNAPSHOT -> FailureReason.PLAYER_NOT_IN_SNAPSHOT;
            case ALREADY_VOTED -> FailureReason.ALREADY_VOTED;
            case UNSUPPORTED_KIND, NOT_PLAYING, MISSING_END_TELEPORT -> FailureReason.NOT_WAITING;
        };
    }

    private static Set<UUID> snapshotMembers(Collection<UUID> members) {
        if (members == null) {
            return Set.of();
        }
        Set<UUID> snapshot = new LinkedHashSet<>();
        for (UUID member : members) {
            if (member != null) {
                snapshot.add(member);
            }
        }
        return Set.copyOf(snapshot);
    }
}
