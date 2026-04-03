package com.cdp.codpattern.app.tdm.service;

import com.cdp.codpattern.network.tdm.VoteDialogPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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

    private static final class VoteSession {
        private final long voteId;
        private final VoteType type;
        private final Set<UUID> voters;
        private final Set<UUID> accepted = new HashSet<>();
        private final Set<UUID> rejected = new HashSet<>();
        private int timeoutTicksRemaining = VOTE_TIMEOUT_TICKS;

        private VoteSession(long voteId, VoteType type, Set<UUID> voters) {
            this.voteId = voteId;
            this.type = type;
            this.voters = voters;
        }
    }

    private final Hooks hooks;
    private VoteSession activeVoteSession;
    private long voteSessionSequence = 0L;

    public VoteService(Hooks hooks) {
        this.hooks = hooks;
    }

    public boolean initiateStartVote(UUID initiator) {
        return initiateVote(VoteType.START, initiator);
    }

    public boolean initiateEndVote(UUID initiator) {
        return initiateVote(VoteType.END, initiator);
    }

    public boolean submitVoteResponse(UUID playerId, long voteId, boolean accepted) {
        if (activeVoteSession == null || activeVoteSession.voteId != voteId) {
            Player player = hooks.getPlayer(playerId);
            if (player != null) {
                hooks.notifyPlayer(player, Component.translatable("message.codpattern.game.vote_expired"));
            }
            return false;
        }

        VoteSession session = activeVoteSession;
        if (!session.voters.contains(playerId)) {
            return false;
        }
        if (session.accepted.contains(playerId) || session.rejected.contains(playerId)) {
            Player player = hooks.getPlayer(playerId);
            if (player != null) {
                hooks.notifyPlayer(player, Component.translatable("message.codpattern.game.already_voted"));
            }
            return false;
        }

        if (accepted) {
            session.accepted.add(playerId);
        } else {
            session.rejected.add(playerId);
        }

        broadcastVoteProgress(session);
        hooks.markRoomListDirty();
        return resolveVoteIfReady(session);
    }

    public void tickVoteSession() {
        if (activeVoteSession == null) {
            return;
        }
        VoteSession session = activeVoteSession;
        session.timeoutTicksRemaining--;
        if (session.timeoutTicksRemaining > 0) {
            return;
        }

        Component timeoutMessage = session.type == VoteType.START
                ? Component.translatable("message.codpattern.game.vote_timeout_start")
                : Component.translatable("message.codpattern.game.vote_timeout_end");
        hooks.broadcastToJoinedPlayers(timeoutMessage);
        clearActiveVoteSession();
        hooks.markRoomListDirty();
    }

    public void clearActiveVoteSession() {
        activeVoteSession = null;
    }

    public void removePlayerFromActiveVote(UUID playerId) {
        if (activeVoteSession == null) {
            return;
        }

        VoteSession session = activeVoteSession;
        if (!session.voters.remove(playerId)) {
            return;
        }
        session.accepted.remove(playerId);
        session.rejected.remove(playerId);
        resolveVoteIfReady(session);
        hooks.markRoomListDirty();
    }

    public String getVoteStatus() {
        if (activeVoteSession == null) {
            return "";
        }
        int requiredVotes = getRequiredVotes(activeVoteSession.type, activeVoteSession.voters.size());
        int acceptedVotes = activeVoteSession.accepted.size();
        if (activeVoteSession.type == VoteType.START) {
            return Component.translatable("message.codpattern.game.status_vote_start", acceptedVotes, requiredVotes)
                    .getString();
        }
        return Component.translatable("message.codpattern.game.status_vote_end", acceptedVotes, requiredVotes)
                .getString();
    }

    private boolean initiateVote(VoteType type, UUID initiator) {
        Player initiatorPlayer = hooks.getPlayer(initiator);
        if (initiatorPlayer == null) {
            return false;
        }

        if (activeVoteSession != null) {
            hooks.notifyPlayer(initiatorPlayer, Component.translatable("message.codpattern.game.vote_in_progress"));
            return false;
        }

        if (type == VoteType.START) {
            if (!hooks.isWaitingPhase()) {
                hooks.notifyPlayer(initiatorPlayer, Component.translatable("message.codpattern.game.already_started"));
                return false;
            }
        } else if (!hooks.isPlayingOrWarmupPhase()) {
            hooks.notifyPlayer(initiatorPlayer, Component.translatable("message.codpattern.game.not_started"));
            return false;
        }

        List<ServerPlayer> joinedPlayers = hooks.getJoinedPlayers();
        if (joinedPlayers.isEmpty()) {
            return false;
        }

        int totalPlayers = joinedPlayers.size();
        if (type == VoteType.START && totalPlayers < hooks.getMinPlayersToStart()) {
            hooks.notifyPlayer(initiatorPlayer, Component.translatable("message.codpattern.game.min_players_warning",
                    hooks.getMinPlayersToStart(), totalPlayers));
            return false;
        }

        if (type == VoteType.START) {
            long unreadyCount = joinedPlayers.stream()
                    .filter(joinedPlayer -> !hooks.isPlayerReady(joinedPlayer.getUUID()))
                    .count();
            if (unreadyCount > 0) {
                hooks.notifyPlayer(initiatorPlayer, Component.translatable("message.codpattern.vote.players_not_ready"));
                return false;
            }
            if (!hooks.hasMatchEndTeleportPoint()) {
                hooks.notifyPlayer(initiatorPlayer, Component.translatable(
                        "message.codpattern.vote.missing_end_teleport",
                        hooks.getMapName()));
                return false;
            }
        }

        Set<UUID> voters = new HashSet<>();
        for (ServerPlayer joinedPlayer : joinedPlayers) {
            voters.add(joinedPlayer.getUUID());
        }

        VoteSession session = new VoteSession(++voteSessionSequence, type, voters);
        activeVoteSession = session;

        String initiatorName = initiatorPlayer.getName().getString();
        Component startMessage = type == VoteType.START
                ? Component.translatable("message.codpattern.game.vote_initiated_start", initiatorName)
                : Component.translatable("message.codpattern.game.vote_initiated_end", initiatorName);
        hooks.broadcastToJoinedPlayers(startMessage);

        int requiredVotes = getRequiredVotes(type, totalPlayers);
        VoteDialogPacket dialogPacket = new VoteDialogPacket(
                hooks.getMapName(), session.voteId, type.name(), initiatorName, requiredVotes, totalPlayers);
        for (ServerPlayer joinedPlayer : joinedPlayers) {
            hooks.sendVoteDialog(dialogPacket, joinedPlayer);
        }

        hooks.markRoomListDirty();
        return true;
    }

    private boolean resolveVoteIfReady(VoteSession session) {
        if (activeVoteSession == null || activeVoteSession != session) {
            return false;
        }

        if (session.voters.isEmpty()) {
            clearActiveVoteSession();
            return false;
        }

        if (session.type == VoteType.START) {
            int totalPlayers = session.voters.size();
            if (totalPlayers < hooks.getMinPlayersToStart()) {
                hooks.broadcastToJoinedPlayers(Component.translatable("message.codpattern.game.min_players_warning",
                        hooks.getMinPlayersToStart(), totalPlayers));
                hooks.broadcastToJoinedPlayers(Component.translatable("message.codpattern.game.vote_failed"));
                clearActiveVoteSession();
                return false;
            }
        } else if (!hooks.isPlayingOrWarmupPhase()) {
            clearActiveVoteSession();
            return false;
        }

        int totalPlayers = session.voters.size();
        int requiredVotes = getRequiredVotes(session.type, totalPlayers);
        int acceptCount = session.accepted.size();
        int rejectCount = session.rejected.size();
        int unresolvedCount = Math.max(0, totalPlayers - acceptCount - rejectCount);

        if (acceptCount >= requiredVotes) {
            if (session.type == VoteType.START) {
                hooks.broadcastToJoinedPlayers(Component.translatable("message.codpattern.game.vote_passed"));
                clearActiveVoteSession();
                hooks.onStartVotePassed();
            } else {
                hooks.broadcastToJoinedPlayers(Component.translatable("message.codpattern.game.vote_passed_end"));
                clearActiveVoteSession();
                hooks.onEndVotePassed();
            }
            return true;
        }

        if (acceptCount + unresolvedCount < requiredVotes) {
            Component failMessage = session.type == VoteType.START
                    ? Component.translatable("message.codpattern.game.vote_failed")
                    : Component.translatable("message.codpattern.game.vote_failed_end");
            hooks.broadcastToJoinedPlayers(failMessage);
            clearActiveVoteSession();
            return false;
        }

        if (acceptCount + rejectCount >= totalPlayers) {
            Component failMessage = session.type == VoteType.START
                    ? Component.translatable("message.codpattern.game.vote_failed")
                    : Component.translatable("message.codpattern.game.vote_failed_end");
            hooks.broadcastToJoinedPlayers(failMessage);
            clearActiveVoteSession();
            return false;
        }

        return false;
    }

    private int getRequiredVotes(VoteType type, int totalPlayers) {
        int votePercent = type == VoteType.START ? hooks.getVotePercentageToStart() : hooks.getVotePercentageToEnd();
        int requiredVotes = (int) Math.ceil(totalPlayers * (votePercent / 100.0));
        return Math.max(1, Math.min(Math.max(1, totalPlayers), requiredVotes));
    }

    private void broadcastVoteProgress(VoteSession session) {
        int totalPlayers = session.voters.size();
        int requiredVotes = getRequiredVotes(session.type, totalPlayers);
        int acceptCount = session.accepted.size();
        int rejectCount = session.rejected.size();

        Component progressMessage = session.type == VoteType.START
                ? Component.translatable("message.codpattern.game.vote_progress_start", acceptCount, rejectCount,
                        totalPlayers, requiredVotes)
                : Component.translatable("message.codpattern.game.vote_progress_end", acceptCount, rejectCount,
                        totalPlayers, requiredVotes);
        hooks.broadcastToJoinedPlayers(progressMessage);
    }
}
