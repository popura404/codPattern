package com.cdp.codpattern.app.match.runtime.vote;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Policy-driven room vote state machine with explicit member-departure semantics. */
public final class RoomVoteEngine<K> {
    public enum FailureReason {
        UNSUPPORTED_KIND,
        INITIATOR_MISSING,
        VOTE_IN_PROGRESS,
        NOT_WAITING,
        NOT_PLAYING,
        EMPTY_SNAPSHOT,
        INITIATOR_NOT_MEMBER,
        MIN_PLAYERS,
        PLAYERS_NOT_READY,
        MISSING_END_TELEPORT,
        STALE_VOTE,
        PLAYER_NOT_IN_SNAPSHOT,
        ALREADY_VOTED,
        MEMBER_LEFT,
        IMPOSSIBLE_TO_PASS,
        ALL_RESPONDED_WITHOUT_PASSING,
        TIMEOUT
    }

    public enum MemberDeparturePolicy {
        FAIL_ACTIVE_VOTE,
        REMOVE_AND_RECALCULATE,
        IGNORE
    }

    public interface Policy<K> {
        StartDecision prepareStart(K kind, UUID initiator, boolean voteActive);

        int requiredVotes(K kind, int memberCount);

        int timeoutTicks(K kind);

        MemberDeparturePolicy memberDeparturePolicy(K kind);

        default Optional<FailureReason> activeFailure(K kind, Set<UUID> currentMembers) {
            return Optional.empty();
        }

        default boolean freezeThresholdAtStart(K kind) {
            return false;
        }
    }

    public interface Listener<K> {
        default void onStartRejected(K kind, UUID initiator, FailureReason reason) {
        }

        default void onStarted(Snapshot<K> snapshot) {
        }

        default void onProgress(Snapshot<K> snapshot) {
        }

        default void onPassed(Snapshot<K> snapshot) {
        }

        default void onFailed(Snapshot<K> snapshot, FailureReason reason) {
        }

        default void onResponseRejected(UUID playerId, long voteId, FailureReason reason) {
        }

        default void onMemberDeparted(UUID playerId, boolean voteResolved) {
        }

        default void onCleared(boolean hadActiveVote) {
        }
    }

    public record StartDecision(boolean accepted, Set<UUID> members, FailureReason failureReason) {
        public StartDecision {
            members = copyMembers(members);
            if (accepted && failureReason != null) {
                throw new IllegalArgumentException("accepted start decision cannot carry a failure reason");
            }
            if (!accepted && failureReason == null) {
                throw new IllegalArgumentException("rejected start decision requires a failure reason");
            }
        }

        public static StartDecision accepted(Collection<UUID> members) {
            return new StartDecision(true, copyMembers(members), null);
        }

        public static StartDecision rejected(FailureReason reason) {
            return new StartDecision(false, Set.of(), Objects.requireNonNull(reason, "reason"));
        }
    }

    public record Snapshot<K>(
            long voteId,
            K kind,
            UUID initiator,
            Set<UUID> members,
            Set<UUID> accepted,
            Set<UUID> rejected,
            int requiredVotes,
            int timeoutTicksRemaining
    ) {
        public Snapshot {
            members = copyMembers(members);
            accepted = copyMembers(accepted);
            rejected = copyMembers(rejected);
        }

        public int totalMembers() {
            return members.size();
        }
    }

    private final class VoteSession {
        private final long voteId;
        private final K kind;
        private final UUID initiator;
        private final Set<UUID> members;
        private final Set<UUID> accepted = new LinkedHashSet<>();
        private final Set<UUID> rejected = new LinkedHashSet<>();
        private final int startThreshold;
        private int timeoutTicksRemaining;

        private VoteSession(long voteId, K kind, UUID initiator, Set<UUID> members) {
            this.voteId = voteId;
            this.kind = kind;
            this.initiator = initiator;
            this.members = new LinkedHashSet<>(members);
            this.startThreshold = threshold(kind, members.size());
            this.timeoutTicksRemaining = Math.max(1, policy.timeoutTicks(kind));
        }
    }

    private final Policy<K> policy;
    private final Listener<K> listener;
    private VoteSession activeVote;
    private long sequence;
    private FailureReason lastFailureReason;

    public RoomVoteEngine(Policy<K> policy, Listener<K> listener) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    public boolean initiate(K kind, UUID initiator) {
        lastFailureReason = null;
        StartDecision decision = Objects.requireNonNull(
                policy.prepareStart(kind, initiator, activeVote != null),
                "policy.prepareStart result");
        if (!decision.accepted()) {
            return rejectStart(kind, initiator, decision.failureReason());
        }
        if (activeVote != null) {
            return rejectStart(kind, initiator, FailureReason.VOTE_IN_PROGRESS);
        }
        if (decision.members().isEmpty()) {
            return rejectStart(kind, initiator, FailureReason.EMPTY_SNAPSHOT);
        }

        VoteSession session = new VoteSession(++sequence, kind, initiator, decision.members());
        activeVote = session;
        listener.onStarted(snapshot(session));
        return true;
    }

    public boolean submit(UUID playerId, long voteId, boolean accepted) {
        lastFailureReason = null;
        VoteSession session = activeVote;
        if (session == null || session.voteId != voteId) {
            return rejectResponse(playerId, voteId, FailureReason.STALE_VOTE);
        }
        if (!session.members.contains(playerId)) {
            return rejectResponse(playerId, voteId, FailureReason.PLAYER_NOT_IN_SNAPSHOT);
        }
        if (session.accepted.contains(playerId) || session.rejected.contains(playerId)) {
            return rejectResponse(playerId, voteId, FailureReason.ALREADY_VOTED);
        }

        if (accepted) {
            session.accepted.add(playerId);
        } else {
            session.rejected.add(playerId);
        }
        listener.onProgress(snapshot(session));
        return resolve(session);
    }

    public void tick() {
        VoteSession session = activeVote;
        if (session == null) {
            return;
        }
        session.timeoutTicksRemaining--;
        if (session.timeoutTicksRemaining <= 0) {
            fail(session, FailureReason.TIMEOUT);
        }
    }

    public void memberDeparted(UUID playerId) {
        VoteSession session = activeVote;
        if (session == null || !session.members.contains(playerId)) {
            return;
        }
        MemberDeparturePolicy departurePolicy = policy.memberDeparturePolicy(session.kind);
        if (departurePolicy == MemberDeparturePolicy.FAIL_ACTIVE_VOTE) {
            fail(session, FailureReason.MEMBER_LEFT);
            listener.onMemberDeparted(playerId, true);
            return;
        }
        if (departurePolicy == MemberDeparturePolicy.IGNORE) {
            listener.onMemberDeparted(playerId, false);
            return;
        }

        session.members.remove(playerId);
        session.accepted.remove(playerId);
        session.rejected.remove(playerId);
        boolean resolved = resolve(session) || activeVote == null;
        listener.onMemberDeparted(playerId, resolved);
    }

    public void clear() {
        boolean hadActiveVote = activeVote != null;
        activeVote = null;
        listener.onCleared(hadActiveVote);
    }

    public Optional<Snapshot<K>> activeSnapshot() {
        return activeVote == null ? Optional.empty() : Optional.of(snapshot(activeVote));
    }

    public Optional<FailureReason> lastFailureReason() {
        return Optional.ofNullable(lastFailureReason);
    }

    public static int ceilClampedThreshold(int memberCount, int votePercent) {
        if (memberCount <= 0) {
            return 0;
        }
        int required = (int) Math.ceil(memberCount * (votePercent / 100.0));
        return Math.max(1, Math.min(memberCount, required));
    }

    private boolean resolve(VoteSession session) {
        if (activeVote != session) {
            return false;
        }
        if (session.members.isEmpty()) {
            activeVote = null;
            return false;
        }

        Optional<FailureReason> activeFailure = policy.activeFailure(
                session.kind,
                Set.copyOf(session.members));
        if (activeFailure.isPresent()) {
            fail(session, activeFailure.get());
            return false;
        }

        int requiredVotes = effectiveThreshold(session);
        int acceptCount = session.accepted.size();
        int rejectCount = session.rejected.size();
        int unresolvedCount = Math.max(0, session.members.size() - acceptCount - rejectCount);
        if (acceptCount >= requiredVotes) {
            Snapshot<K> passed = snapshot(session);
            activeVote = null;
            listener.onPassed(passed);
            return true;
        }
        if (acceptCount + unresolvedCount < requiredVotes) {
            fail(session, FailureReason.IMPOSSIBLE_TO_PASS);
            return false;
        }
        if (acceptCount + rejectCount >= session.members.size()) {
            fail(session, FailureReason.ALL_RESPONDED_WITHOUT_PASSING);
        }
        return false;
    }

    private boolean rejectStart(K kind, UUID initiator, FailureReason reason) {
        lastFailureReason = Objects.requireNonNull(reason, "reason");
        listener.onStartRejected(kind, initiator, reason);
        return false;
    }

    private boolean rejectResponse(UUID playerId, long voteId, FailureReason reason) {
        lastFailureReason = reason;
        listener.onResponseRejected(playerId, voteId, reason);
        return false;
    }

    private void fail(VoteSession session, FailureReason reason) {
        if (activeVote != session) {
            return;
        }
        Snapshot<K> failed = snapshot(session);
        activeVote = null;
        lastFailureReason = reason;
        listener.onFailed(failed, reason);
    }

    private Snapshot<K> snapshot(VoteSession session) {
        return new Snapshot<>(
                session.voteId,
                session.kind,
                session.initiator,
                session.members,
                session.accepted,
                session.rejected,
                effectiveThreshold(session),
                session.timeoutTicksRemaining);
    }

    private int effectiveThreshold(VoteSession session) {
        return policy.freezeThresholdAtStart(session.kind)
                ? session.startThreshold
                : threshold(session.kind, session.members.size());
    }

    private int threshold(K kind, int memberCount) {
        if (memberCount <= 0) {
            return 0;
        }
        return Math.max(1, Math.min(memberCount, policy.requiredVotes(kind, memberCount)));
    }

    private static Set<UUID> copyMembers(Collection<UUID> members) {
        if (members == null) {
            return Set.of();
        }
        Set<UUID> copied = new LinkedHashSet<>();
        for (UUID member : members) {
            if (member != null) {
                copied.add(member);
            }
        }
        return Set.copyOf(copied);
    }
}
