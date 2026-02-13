package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.model.TdmGamePhase;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import net.minecraft.server.level.ServerPlayer;

final record CodTdmMapLifecycleRuntime(
        CodTdmClientSyncCoordinator clientSyncCoordinator,
        CodTdmPhaseRuntime phaseRuntime,
        CodTdmTickRuntime tickRuntime,
        CodTdmResetRuntime resetRuntime,
        CodTdmTeamMembershipCoordinator teamMembershipCoordinator,
        CodTdmEndTeleportRuntime endTeleportRuntime,
        CodTdmMatchProgressRuntime matchProgressRuntime,
        Runnable markStartedAction,
        Runnable markStoppedAction
) {

    void tick() {
        tickRuntime.tick();
    }

    void syncToClient() {
        clientSyncCoordinator.syncToClient();
    }

    void transitionToCountdown() {
        phaseRuntime.transitionToPhase(TdmGamePhase.COUNTDOWN);
    }

    void startGame() {
        markStartedAction.run();
        transitionToCountdown();
    }

    void transitionToEnded() {
        phaseRuntime.transitionToPhase(TdmGamePhase.ENDED);
    }

    boolean hasReachedVictoryGoal() {
        return matchProgressRuntime.hasReachedVictoryGoal();
    }

    void resetGameState() {
        resetRuntime.resetGameState();
    }

    void resetGame() {
        markStoppedAction.run();
        resetGameState();
    }

    void leaveRoom(ServerPlayer player) {
        teamMembershipCoordinator.leaveRoom(player);
    }

    void setMatchEndTeleportPoint(SpawnPointData data) {
        endTeleportRuntime.setMatchEndTeleportPoint(data);
    }
}
