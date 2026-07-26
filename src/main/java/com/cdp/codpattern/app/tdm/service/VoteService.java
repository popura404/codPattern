package com.cdp.codpattern.app.tdm.service;

import com.cdp.codpattern.app.match.runtime.vote.RoomVoteEngine;
import com.cdp.codpattern.network.match.VoteDialogPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** PVP presentation and prerequisite facade over the neutral room vote engine. */
public final class VoteService {
    private static final int VOTE_TIMEOUT_TICKS = 15 * 20;

    public interface Hooks {
        Player getPlayer(UUID playerId);

        List<ServerPlayer> getJoinedPlayers();

        boolean isWaitingPhase();

        boolean isPlayingOrWarmupPhase();

        boolean isPlayerReady(UUID playerId);

        boolean hasMatchEndTeleportPoint();

        int getMinPlayersToStart();

        int getVotePercentageToStart();

        int getVotePercentageToEnd();

        String getMapName();

        void broadcastToJoinedPlayers(Component message);

        void sendVoteDialog(VoteDialogPacket packet, ServerPlayer player);

        void notifyPlayer(Player player, Component message);

        void onStartVotePassed();

        void onEndVotePassed();

        void markRoomListDirty();
    }

    private enum VoteType {
        START,
        END
    }

    private enum ResolutionOutcome {
        PASSED,
        MIN_PLAYERS,
        IMPOSSIBLE,
        ALL_RESPONDED
    }

    private final Hooks hooks;
    private final RoomVoteEngine<VoteType> delegate;

    public VoteService(Hooks hooks) {
        this.hooks = java.util.Objects.requireNonNull(hooks, "hooks");
        this.delegate = new RoomVoteEngine<>(new PvpVotePolicy(), new PvpVoteListener());
    }

    public boolean initiateStartVote(UUID initiator) {
        return initiateVote(VoteType.START, initiator);
    }

    public boolean initiateEndVote(UUID initiator) {
        return initiateVote(VoteType.END, initiator);
    }

    public boolean submitVoteResponse(UUID playerId, long voteId, boolean accepted) {
        boolean passed = delegate.submit(playerId, voteId, accepted);
        Optional<RoomVoteEngine.FailureReason> failure = delegate.lastFailureReason();
        if (failure.orElse(null) == RoomVoteEngine.FailureReason.STALE_VOTE) {
            Player player = hooks.getPlayer(playerId);
            if (player != null) {
                hooks.notifyPlayer(player, Component.translatable("message.codpattern.game.vote_expired"));
            }
        } else if (failure.orElse(null) == RoomVoteEngine.FailureReason.ALREADY_VOTED) {
            Player player = hooks.getPlayer(playerId);
            if (player != null) {
                hooks.notifyPlayer(player, Component.translatable("message.codpattern.game.already_voted"));
            }
        }
        return passed;
    }

    public void tickVoteSession() {
        delegate.tick();
    }

    private void broadcastTimeout(VoteType type) {
        Component timeoutMessage = type == VoteType.START
                ? Component.translatable("message.codpattern.game.vote_timeout_start")
                : Component.translatable("message.codpattern.game.vote_timeout_end");
        hooks.broadcastToJoinedPlayers(timeoutMessage);
    }

    public void clearActiveVoteSession() {
        delegate.clear();
    }

    public void removePlayerFromActiveVote(UUID playerId) {
        delegate.memberDeparted(playerId);
    }

    public String getVoteStatus() {
        Optional<RoomVoteEngine.Snapshot<VoteType>> activeVote = delegate.activeSnapshot();
        if (activeVote.isEmpty()) {
            return "";
        }
        RoomVoteEngine.Snapshot<VoteType> snapshot = activeVote.get();
        int requiredVotes = snapshot.requiredVotes();
        int acceptedVotes = snapshot.accepted().size();
        if (snapshot.kind() == VoteType.START) {
            return Component.translatable("message.codpattern.game.status_vote_start", acceptedVotes, requiredVotes)
                    .getString();
        }
        return Component.translatable("message.codpattern.game.status_vote_end", acceptedVotes, requiredVotes)
                .getString();
    }

    private boolean initiateVote(VoteType type, UUID initiator) {
        return delegate.initiate(type, initiator);
    }

    private RoomVoteEngine.StartDecision prepareStart(VoteType type, UUID initiator, boolean voteActive) {
        Player initiatorPlayer = hooks.getPlayer(initiator);
        if (initiatorPlayer == null) {
            return RoomVoteEngine.StartDecision.rejected(RoomVoteEngine.FailureReason.INITIATOR_MISSING);
        }

        if (voteActive) {
            hooks.notifyPlayer(initiatorPlayer, Component.translatable("message.codpattern.game.vote_in_progress"));
            return RoomVoteEngine.StartDecision.rejected(RoomVoteEngine.FailureReason.VOTE_IN_PROGRESS);
        }

        if (type == VoteType.START) {
            if (!hooks.isWaitingPhase()) {
                hooks.notifyPlayer(initiatorPlayer, Component.translatable("message.codpattern.game.already_started"));
                return RoomVoteEngine.StartDecision.rejected(RoomVoteEngine.FailureReason.NOT_WAITING);
            }
        } else if (!hooks.isPlayingOrWarmupPhase()) {
            hooks.notifyPlayer(initiatorPlayer, Component.translatable("message.codpattern.game.not_started"));
            return RoomVoteEngine.StartDecision.rejected(RoomVoteEngine.FailureReason.NOT_PLAYING);
        }

        List<ServerPlayer> joinedPlayers = hooks.getJoinedPlayers();
        if (joinedPlayers.isEmpty()) {
            return RoomVoteEngine.StartDecision.rejected(RoomVoteEngine.FailureReason.EMPTY_SNAPSHOT);
        }

        int totalPlayers = joinedPlayers.size();
        if (type == VoteType.START && totalPlayers < hooks.getMinPlayersToStart()) {
            hooks.notifyPlayer(initiatorPlayer, Component.translatable("message.codpattern.game.min_players_warning",
                    hooks.getMinPlayersToStart(), totalPlayers));
            return RoomVoteEngine.StartDecision.rejected(RoomVoteEngine.FailureReason.MIN_PLAYERS);
        }

        if (type == VoteType.START) {
            long unreadyCount = joinedPlayers.stream()
                    .filter(joinedPlayer -> !hooks.isPlayerReady(joinedPlayer.getUUID()))
                    .count();
            if (unreadyCount > 0) {
                hooks.notifyPlayer(initiatorPlayer, Component.translatable("message.codpattern.vote.players_not_ready"));
                return RoomVoteEngine.StartDecision.rejected(RoomVoteEngine.FailureReason.PLAYERS_NOT_READY);
            }
            if (!hooks.hasMatchEndTeleportPoint()) {
                hooks.notifyPlayer(initiatorPlayer, Component.translatable(
                        "message.codpattern.vote.missing_end_teleport",
                        hooks.getMapName()));
                return RoomVoteEngine.StartDecision.rejected(RoomVoteEngine.FailureReason.MISSING_END_TELEPORT);
            }
        }

        Set<UUID> voters = new LinkedHashSet<>();
        for (ServerPlayer joinedPlayer : joinedPlayers) {
            voters.add(joinedPlayer.getUUID());
        }
        return RoomVoteEngine.StartDecision.accepted(voters);
    }

    private void onVoteStarted(RoomVoteEngine.Snapshot<VoteType> snapshot) {
        Player initiatorPlayer = hooks.getPlayer(snapshot.initiator());
        if (initiatorPlayer == null) {
            return;
        }
        String initiatorName = initiatorPlayer.getName().getString();
        Component startMessage = snapshot.kind() == VoteType.START
                ? Component.translatable("message.codpattern.game.vote_initiated_start", initiatorName)
                : Component.translatable("message.codpattern.game.vote_initiated_end", initiatorName);
        hooks.broadcastToJoinedPlayers(startMessage);

        VoteDialogPacket dialogPacket = new VoteDialogPacket(
                hooks.getMapName(),
                snapshot.voteId(),
                snapshot.kind().name(),
                initiatorName,
                snapshot.requiredVotes(),
                snapshot.totalMembers());
        Set<UUID> members = snapshot.members();
        for (ServerPlayer joinedPlayer : hooks.getJoinedPlayers()) {
            if (members.contains(joinedPlayer.getUUID())) {
                hooks.sendVoteDialog(dialogPacket, joinedPlayer);
            }
        }
        hooks.markRoomListDirty();
    }

    private void presentResolution(
            RoomVoteEngine.Snapshot<VoteType> session,
            ResolutionOutcome outcome
    ) {
        int totalPlayers = session.totalMembers();
        if (outcome == ResolutionOutcome.MIN_PLAYERS) {
            hooks.broadcastToJoinedPlayers(Component.translatable("message.codpattern.game.min_players_warning",
                    hooks.getMinPlayersToStart(), totalPlayers));
            hooks.broadcastToJoinedPlayers(Component.translatable("message.codpattern.game.vote_failed"));
            return;
        }
        if (outcome == ResolutionOutcome.PASSED) {
            if (session.kind() == VoteType.START) {
                hooks.broadcastToJoinedPlayers(Component.translatable("message.codpattern.game.vote_passed"));
                hooks.onStartVotePassed();
            } else {
                hooks.broadcastToJoinedPlayers(Component.translatable("message.codpattern.game.vote_passed_end"));
                hooks.onEndVotePassed();
            }
            return;
        }
        if (outcome == ResolutionOutcome.IMPOSSIBLE) {
            Component failMessage = session.kind() == VoteType.START
                    ? Component.translatable("message.codpattern.game.vote_failed")
                    : Component.translatable("message.codpattern.game.vote_failed_end");
            hooks.broadcastToJoinedPlayers(failMessage);
            return;
        }
        Component failMessage = session.kind() == VoteType.START
                ? Component.translatable("message.codpattern.game.vote_failed")
                : Component.translatable("message.codpattern.game.vote_failed_end");
        hooks.broadcastToJoinedPlayers(failMessage);
    }

    private int getRequiredVotes(VoteType type, int totalPlayers) {
        int votePercent = type == VoteType.START ? hooks.getVotePercentageToStart() : hooks.getVotePercentageToEnd();
        return RoomVoteEngine.ceilClampedThreshold(totalPlayers, votePercent);
    }

    private void broadcastVoteProgress(RoomVoteEngine.Snapshot<VoteType> session) {
        int totalPlayers = session.totalMembers();
        int requiredVotes = session.requiredVotes();
        int acceptCount = session.accepted().size();
        int rejectCount = session.rejected().size();

        Component progressMessage = session.kind() == VoteType.START
                ? Component.translatable("message.codpattern.game.vote_progress_start", acceptCount, rejectCount,
                        totalPlayers, requiredVotes)
                : Component.translatable("message.codpattern.game.vote_progress_end", acceptCount, rejectCount,
                        totalPlayers, requiredVotes);
        hooks.broadcastToJoinedPlayers(progressMessage);
    }

    private final class PvpVotePolicy implements RoomVoteEngine.Policy<VoteType> {
        @Override
        public RoomVoteEngine.StartDecision prepareStart(
                VoteType kind,
                UUID initiator,
                boolean voteActive
        ) {
            return VoteService.this.prepareStart(kind, initiator, voteActive);
        }

        @Override
        public int requiredVotes(VoteType kind, int memberCount) {
            return getRequiredVotes(kind, memberCount);
        }

        @Override
        public int timeoutTicks(VoteType kind) {
            return VOTE_TIMEOUT_TICKS;
        }

        @Override
        public RoomVoteEngine.MemberDeparturePolicy memberDeparturePolicy(VoteType kind) {
            return RoomVoteEngine.MemberDeparturePolicy.REMOVE_AND_RECALCULATE;
        }

        @Override
        public Optional<RoomVoteEngine.FailureReason> activeFailure(VoteType kind, Set<UUID> currentMembers) {
            if (kind == VoteType.START && currentMembers.size() < hooks.getMinPlayersToStart()) {
                return Optional.of(RoomVoteEngine.FailureReason.MIN_PLAYERS);
            }
            if (kind == VoteType.END && !hooks.isPlayingOrWarmupPhase()) {
                return Optional.of(RoomVoteEngine.FailureReason.NOT_PLAYING);
            }
            return Optional.empty();
        }
    }

    private final class PvpVoteListener implements RoomVoteEngine.Listener<VoteType> {
        @Override
        public void onStarted(RoomVoteEngine.Snapshot<VoteType> snapshot) {
            onVoteStarted(snapshot);
        }

        @Override
        public void onProgress(RoomVoteEngine.Snapshot<VoteType> snapshot) {
            broadcastVoteProgress(snapshot);
            hooks.markRoomListDirty();
        }

        @Override
        public void onPassed(RoomVoteEngine.Snapshot<VoteType> snapshot) {
            presentResolution(snapshot, ResolutionOutcome.PASSED);
        }

        @Override
        public void onFailed(
                RoomVoteEngine.Snapshot<VoteType> snapshot,
                RoomVoteEngine.FailureReason reason
        ) {
            switch (reason) {
                case TIMEOUT -> {
                    broadcastTimeout(snapshot.kind());
                    hooks.markRoomListDirty();
                }
                case MIN_PLAYERS -> presentResolution(snapshot, ResolutionOutcome.MIN_PLAYERS);
                case IMPOSSIBLE_TO_PASS -> presentResolution(snapshot, ResolutionOutcome.IMPOSSIBLE);
                case ALL_RESPONDED_WITHOUT_PASSING -> presentResolution(snapshot, ResolutionOutcome.ALL_RESPONDED);
                default -> {
                    // The current PVP facade intentionally presents no message for other invalidations.
                }
            }
        }

        @Override
        public void onMemberDeparted(UUID playerId, boolean voteResolved) {
            hooks.markRoomListDirty();
        }
    }
}
