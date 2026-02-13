package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.service.VoteService;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

final class CodTdmVoteRuntime {
    private final VoteService voteService;
    private final CodTdmVoteCoordinator voteCoordinator;

    CodTdmVoteRuntime(VoteService voteService, CodTdmVoteCoordinator voteCoordinator) {
        this.voteService = voteService;
        this.voteCoordinator = voteCoordinator;
    }

    void tickVoteSession() {
        voteService.tickVoteSession();
    }

    void clearActiveVoteSession() {
        voteService.clearActiveVoteSession();
    }

    void clearAll() {
        voteCoordinator.clearAll();
    }

    void removePlayer(UUID playerId) {
        voteCoordinator.removePlayer(playerId);
    }

    void initializeReadyState(ServerPlayer player) {
        voteCoordinator.initializeReadyState(player);
    }

    boolean setPlayerReady(ServerPlayer player, boolean ready) {
        return voteCoordinator.setPlayerReady(player, ready);
    }

    boolean isPlayerReady(UUID playerId) {
        return voteCoordinator.isPlayerReady(playerId);
    }

    boolean initiateStartVote(UUID initiator) {
        return voteCoordinator.initiateStartVote(initiator);
    }

    boolean initiateEndVote(UUID initiator) {
        return voteCoordinator.initiateEndVote(initiator);
    }

    boolean submitVoteResponse(UUID playerId, long voteId, boolean accepted) {
        return voteCoordinator.submitVoteResponse(playerId, voteId, accepted);
    }

    String getVoteStatus() {
        return voteCoordinator.getVoteStatus();
    }
}
