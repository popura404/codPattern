package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.model.TdmGamePhase;
import com.cdp.codpattern.app.tdm.service.PhaseStateMachine;
import com.cdp.codpattern.config.tdm.CodTdmConfig;

final class CodTdmPhaseRuntime {
    private final CodTdmMatchRuntimeState matchState;
    private final CodTdmVoteRuntime voteRuntime;
    private final PhaseStateMachine.Hooks phaseStateHooks;
    private final Runnable clearDeathHudForAllPlayersAction;
    private final Runnable syncToClientAction;

    CodTdmPhaseRuntime(
            CodTdmMatchRuntimeState matchState,
            CodTdmVoteRuntime voteRuntime,
            PhaseStateMachine.Hooks phaseStateHooks,
            Runnable clearDeathHudForAllPlayersAction,
            Runnable syncToClientAction
    ) {
        this.matchState = matchState;
        this.voteRuntime = voteRuntime;
        this.phaseStateHooks = phaseStateHooks;
        this.clearDeathHudForAllPlayersAction = clearDeathHudForAllPlayersAction;
        this.syncToClientAction = syncToClientAction;
    }

    void transitionToPhase(TdmGamePhase newPhase) {
        if (newPhase != matchState.phase()) {
            voteRuntime.clearActiveVoteSession();
        }

        PhaseStateMachine.EnterPhaseResult enterPhaseResult = PhaseStateMachine.enterPhase(
                newPhase,
                matchState.gameTimeTicks(),
                CodTdmConfig.getConfig(),
                phaseStateHooks
        );
        matchState.setPhase(newPhase);
        matchState.setPhaseTimer(enterPhaseResult.phaseTimer());
        matchState.setGameTimeTicks(enterPhaseResult.gameTimeTicks());
        if (newPhase == TdmGamePhase.PLAYING) {
            matchState.markPlayingStarted(System.currentTimeMillis());
        }

        if (newPhase != TdmGamePhase.PLAYING) {
            clearDeathHudForAllPlayersAction.run();
        }

        syncToClientAction.run();
    }
}
