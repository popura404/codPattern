package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.model.TdmGamePhase;

import com.cdp.codpattern.app.tdm.service.VoteService;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class CodTdmVoteCoordinator {
    private final Map<UUID, Boolean> readyStates;
    private final VoteService voteService;
    private final Supplier<TdmGamePhase> phaseSupplier;
    private final Predicate<UUID> joinedPlayerChecker;
    private final Runnable syncAction;

    CodTdmVoteCoordinator(
            Map<UUID, Boolean> readyStates,
            VoteService voteService,
            Supplier<TdmGamePhase> phaseSupplier,
            Predicate<UUID> joinedPlayerChecker,
            Runnable syncAction
    ) {
        this.readyStates = readyStates;
        this.voteService = voteService;
        this.phaseSupplier = phaseSupplier;
        this.joinedPlayerChecker = joinedPlayerChecker;
        this.syncAction = syncAction;
    }

    void initializeReadyState(ServerPlayer player) {
        readyStates.put(player.getUUID(), false);
    }

    boolean setPlayerReady(ServerPlayer player, boolean ready) {
        if (phaseSupplier.get() != TdmGamePhase.WAITING) {
            return false;
        }
        UUID playerId = player.getUUID();
        if (!joinedPlayerChecker.test(playerId)) {
            return false;
        }
        readyStates.put(playerId, ready);
        syncAction.run();
        return true;
    }

    boolean isPlayerReady(UUID playerId) {
        return readyStates.getOrDefault(playerId, false);
    }

    boolean initiateStartVote(UUID initiator) {
        return voteService.initiateStartVote(initiator);
    }

    boolean initiateEndVote(UUID initiator) {
        return voteService.initiateEndVote(initiator);
    }

    boolean submitVoteResponse(UUID playerId, long voteId, boolean accepted) {
        return voteService.submitVoteResponse(playerId, voteId, accepted);
    }

    String getVoteStatus() {
        return voteService.getVoteStatus();
    }

    void removePlayer(UUID playerId) {
        readyStates.remove(playerId);
        voteService.removePlayerFromActiveVote(playerId);
    }

    void clearAll() {
        readyStates.clear();
        voteService.clearActiveVoteSession();
    }
}
