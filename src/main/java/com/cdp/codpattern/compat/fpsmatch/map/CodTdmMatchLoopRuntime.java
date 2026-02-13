package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.model.TdmGamePhase;
import com.cdp.codpattern.app.tdm.service.PhaseStateMachine;
import com.cdp.codpattern.config.tdm.CodTdmConfig;

import java.util.function.Consumer;

final class CodTdmMatchLoopRuntime {
    private final CodTdmMatchRuntimeState matchState;
    private final PhaseStateMachine.Hooks phaseStateHooks;
    private final Consumer<TdmGamePhase> transitionToPhaseAction;

    CodTdmMatchLoopRuntime(
            CodTdmMatchRuntimeState matchState,
            PhaseStateMachine.Hooks phaseStateHooks,
            Consumer<TdmGamePhase> transitionToPhaseAction
    ) {
        this.matchState = matchState;
        this.phaseStateHooks = phaseStateHooks;
        this.transitionToPhaseAction = transitionToPhaseAction;
    }

    void tickPhaseStateMachine() {
        PhaseStateMachine.TickResult tickResult = PhaseStateMachine.tick(
                matchState.phase(),
                matchState.phaseTimer(),
                matchState.gameTimeTicks(),
                CodTdmConfig.getConfig(),
                matchState.teamScores(),
                phaseStateHooks
        );

        if (!tickResult.resetTriggered()) {
            matchState.setPhaseTimer(tickResult.phaseTimer());
            matchState.setGameTimeTicks(tickResult.gameTimeTicks());
            tickResult.nextPhase().ifPresent(transitionToPhaseAction);
        }
    }
}
