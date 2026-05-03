package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.port.VoteControlPort;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Start-vote state machine for zombies rooms. The active vote uses a fixed member snapshot.
 */
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

    private static final class VoteSession {
        private final long voteId;
        private final UUID initiator;
        private final Set<UUID> members;
        private final int totalMembers;
        private final int requiredVotes;
        private final Set<UUID> accepted = new LinkedHashSet<>();
        private final Set<UUID> rejected = new LinkedHashSet<>();
        private int timeoutTicksRemaining;

        private VoteSession(long voteId, UUID initiator, Set<UUID> members, int requiredVotes, int timeoutTicks) {
            this.voteId = voteId;
            this.initiator = initiator;
            this.members = Set.copyOf(new LinkedHashSet<>(members));
            this.totalMembers = this.members.size();
            this.requiredVotes = requiredVotes;
            this.timeoutTicksRemaining = Math.max(1, timeoutTicks);
        }
    }

    private final Hooks hooks;
    private VoteSession activeVoteSession;
    private long voteSessionSequence = 0L;
    private FailureReason lastFailureReason;

    public ZombiesStartVoteService(Hooks hooks) {
        this.hooks = Objects.requireNonNull(hooks, "hooks");
    }

    @Override
    public boolean initiateStartVote(UUID initiator) {
        lastFailureReason = null;
        if (initiator == null) {
            return failToStart(null, FailureReason.INITIATOR_NOT_MEMBER);
        }
        if (activeVoteSession != null) {
            return failToStart(snapshot(activeVoteSession), FailureReason.VOTE_IN_PROGRESS);
        }
        if (!hooks.isWaitingPhase()) {
            return failToStart(null, FailureReason.NOT_WAITING);
        }

        Set<UUID> members = snapshotMembers(hooks.currentMembers());
        if (members.isEmpty()) {
            return failToStart(null, FailureReason.EMPTY_SNAPSHOT);
        }
        if (!members.contains(initiator)) {
            return failToStart(null, FailureReason.INITIATOR_NOT_MEMBER);
        }
        if (members.size() < hooks.minPlayersToStart()) {
            return failToStart(null, FailureReason.MIN_PLAYERS);
        }
        for (UUID member : members) {
            if (!hooks.isPlayerReady(member)) {
                return failToStart(null, FailureReason.PLAYERS_NOT_READY);
            }
        }

        int requiredVotes = requiredVotes(members.size(), hooks.votePercentageToStart());
        VoteSession session = new VoteSession(
                ++voteSessionSequence,
                initiator,
                members,
                requiredVotes,
                hooks.voteTimeoutTicks()
        );
        activeVoteSession = session;
        hooks.onVoteStarted(snapshot(session));
        hooks.markRoomListDirty();
        return true;
    }

    @Override
    public boolean initiateEndVote(UUID initiator) {
        lastFailureReason = FailureReason.END_VOTE_UNSUPPORTED;
        return false;
    }

    @Override
    public boolean submitVoteResponse(UUID playerId, long voteId, boolean accepted) {
        lastFailureReason = null;
        VoteSession session = activeVoteSession;
        if (session == null || session.voteId != voteId) {
            lastFailureReason = FailureReason.STALE_VOTE;
            return false;
        }
        if (!session.members.contains(playerId)) {
            lastFailureReason = FailureReason.PLAYER_NOT_IN_SNAPSHOT;
            return false;
        }
        if (session.accepted.contains(playerId) || session.rejected.contains(playerId)) {
            lastFailureReason = FailureReason.ALREADY_VOTED;
            return false;
        }

        if (accepted) {
            session.accepted.add(playerId);
        } else {
            session.rejected.add(playerId);
        }

        VoteSnapshot progress = snapshot(session);
        hooks.onVoteProgress(progress);
        hooks.markRoomListDirty();
        return resolveVote(session);
    }

    public void tickVoteSession() {
        VoteSession session = activeVoteSession;
        if (session == null) {
            return;
        }
        session.timeoutTicksRemaining--;
        if (session.timeoutTicksRemaining <= 0) {
            failActiveVote(session, FailureReason.TIMEOUT);
        }
    }

    public void onSnapshotMemberLeft(UUID playerId) {
        VoteSession session = activeVoteSession;
        if (session != null && session.members.contains(playerId)) {
            failActiveVote(session, FailureReason.PLAYER_LEFT);
        }
    }

    public void onPlayerJoined(UUID playerId) {
        // Joins during or after a snapshot do not affect the active vote denominator.
    }

    public Optional<VoteSnapshot> activeVoteSnapshot() {
        return activeVoteSession == null ? Optional.empty() : Optional.of(snapshot(activeVoteSession));
    }

    public Optional<FailureReason> lastFailureReason() {
        return Optional.ofNullable(lastFailureReason);
    }

    public void clearActiveVoteSession() {
        activeVoteSession = null;
        hooks.markRoomListDirty();
    }

    public static int requiredVotes(int totalMembers, int votePercent) {
        if (totalMembers <= 0) {
            return 0;
        }
        int required = (int) Math.ceil(totalMembers * (votePercent / 100.0));
        return Math.max(1, Math.min(totalMembers, required));
    }

    private boolean resolveVote(VoteSession session) {
        if (activeVoteSession != session) {
            return false;
        }

        int acceptCount = session.accepted.size();
        int rejectCount = session.rejected.size();
        int unresolvedCount = Math.max(0, session.totalMembers - acceptCount - rejectCount);

        if (acceptCount >= session.requiredVotes) {
            VoteSnapshot passed = snapshot(session);
            activeVoteSession = null;
            hooks.onVotePassed(passed);
            hooks.markRoomListDirty();
            return true;
        }

        if (acceptCount + unresolvedCount < session.requiredVotes) {
            failActiveVote(session, FailureReason.IMPOSSIBLE_TO_PASS);
            return false;
        }

        if (acceptCount + rejectCount >= session.totalMembers) {
            failActiveVote(session, FailureReason.ALL_RESPONDED_WITHOUT_PASSING);
        }
        return false;
    }

    private boolean failToStart(VoteSnapshot snapshot, FailureReason reason) {
        lastFailureReason = reason;
        hooks.onVoteFailed(snapshot, reason);
        hooks.markRoomListDirty();
        return false;
    }

    private void failActiveVote(VoteSession session, FailureReason reason) {
        if (activeVoteSession != session) {
            return;
        }
        VoteSnapshot failed = snapshot(session);
        activeVoteSession = null;
        lastFailureReason = reason;
        hooks.onVoteFailed(failed, reason);
        hooks.markRoomListDirty();
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

    private static VoteSnapshot snapshot(VoteSession session) {
        return new VoteSnapshot(
                session.voteId,
                session.initiator,
                session.members,
                session.accepted,
                session.rejected,
                session.requiredVotes,
                session.timeoutTicksRemaining
        );
    }
}
