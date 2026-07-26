package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.model.TdmGamePhase;

import com.cdp.codpattern.app.match.runtime.ready.DefaultReadyStateService;
import com.cdp.codpattern.app.tdm.service.VoteService;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class CodTdmVoteCoordinator {
    private final DefaultReadyStateService readyStateService;
    private final VoteService voteService;

    CodTdmVoteCoordinator(
            Map<UUID, Boolean> readyStates,
            VoteService voteService,
            Supplier<TdmGamePhase> phaseSupplier,
            Predicate<UUID> joinedPlayerChecker,
            Runnable syncAction
    ) {
        this.voteService = voteService;
        this.readyStateService = new DefaultReadyStateService(
                readyStates,
                new DefaultReadyStateService.Policy() {
                    @Override
                    public boolean canMutate(UUID playerId) {
                        return phaseSupplier.get() == TdmGamePhase.WAITING
                                && joinedPlayerChecker.test(playerId);
                    }

                    @Override
                    public void onMutation(
                            UUID playerId,
                            boolean ready,
                            DefaultReadyStateService.OperationResult result
                    ) {
                        if (result.accepted()) {
                            syncAction.run();
                        }
                    }
                });
    }

    void initializeReadyState(ServerPlayer player) {
        readyStateService.initialize(player.getUUID());
    }

    boolean setPlayerReady(ServerPlayer player, boolean ready) {
        return readyStateService.setReady(player.getUUID(), ready).accepted();
    }

    boolean isPlayerReady(UUID playerId) {
        return readyStateService.isReady(playerId);
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
        readyStateService.remove(playerId);
        voteService.removePlayerFromActiveVote(playerId);
    }

    void clearAll() {
        readyStateService.clear();
        voteService.clearActiveVoteSession();
    }
}
