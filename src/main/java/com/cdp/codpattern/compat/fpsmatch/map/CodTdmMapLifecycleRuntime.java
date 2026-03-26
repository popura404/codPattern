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
        CodTdmRespawnRuntime respawnRuntime,
        CodTdmEndTeleportRuntime endTeleportRuntime,
        CodTdmMatchProgressRuntime matchProgressRuntime,
        java.util.function.Supplier<TdmGamePhase> phaseSupplier,
        Runnable markStartedAction,
        Runnable markStoppedAction
) {

    void tick() {
        tickRuntime.tick();
        clientSyncCoordinator.tick();
    }

    void syncToClient() {
        clientSyncCoordinator.syncToClient();
    }

    void requestRosterResync(ServerPlayer player) {
        clientSyncCoordinator.requestRosterResync(player);
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

    void handleUnexpectedDisconnect(ServerPlayer player) {
        if (phaseSupplier.get() == TdmGamePhase.WAITING) {
            teamMembershipCoordinator.leaveRoom(player);
            return;
        }
        teamMembershipCoordinator.handleUnexpectedDisconnect(player);
    }

    void handleReconnect(ServerPlayer player) {
        teamMembershipCoordinator.handleReconnect(player);
        TdmGamePhase phase = phaseSupplier.get();
        if (phase == TdmGamePhase.WARMUP || phase == TdmGamePhase.PLAYING) {
            respawnRuntime.respawnPlayerNow(player);
        }
        clientSyncCoordinator.syncToClient();
    }

    void setMatchEndTeleportPoint(SpawnPointData data) {
        endTeleportRuntime.setMatchEndTeleportPoint(data);
    }
}
