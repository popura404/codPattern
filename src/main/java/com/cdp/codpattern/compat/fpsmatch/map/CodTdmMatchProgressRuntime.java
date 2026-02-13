package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.service.PhaseStateMachine;
import com.cdp.codpattern.app.tdm.service.ScoreService;
import com.cdp.codpattern.config.tdm.CodTdmConfig;

final class CodTdmMatchProgressRuntime {
    private final CodTdmMatchRuntimeState matchState;

    CodTdmMatchProgressRuntime(CodTdmMatchRuntimeState matchState) {
        this.matchState = matchState;
    }

    boolean hasReachedVictoryGoal() {
        return ScoreService.hasReachedVictoryGoal(
                matchState.phase(),
                matchState.gameTimeTicks(),
                matchState.teamScores(),
                CodTdmConfig.getConfig()
        );
    }

    int getRemainingTimeTicks() {
        return PhaseStateMachine.getRemainingTimeTicks(
                matchState.phase(),
                matchState.phaseTimer(),
                matchState.gameTimeTicks(),
                CodTdmConfig.getConfig()
        );
    }
}
