package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.service.PhaseStateMachine;
import com.cdp.codpattern.app.tdm.service.PlayerDeathService;
import com.cdp.codpattern.app.tdm.service.ScoreService;
import com.cdp.codpattern.app.tdm.service.VoteService;

import java.util.Map;
import java.util.UUID;

final class CodTdmMapHooks {
    private CodTdmMapHooks() {
    }

    static PhaseStateMachine.Hooks createPhaseHooks(
            CodTdmPhaseHooksPort port,
            CodTdmPlayerRuntimeState playerState,
            CodTdmJoinedPlayerBroadcaster joinedPlayerBroadcaster
    ) {
        return new CodTdmPhaseStateHooks(port, playerState, joinedPlayerBroadcaster);
    }

    static ScoreService.Hooks createScoreHooks(
            CodTdmScoreHooksPort port,
            CodTdmJoinedPlayerBroadcaster joinedPlayerBroadcaster
    ) {
        return new CodTdmScoreHooks(port, joinedPlayerBroadcaster);
    }

    static PlayerDeathService.Hooks createPlayerDeathHooks(
            CodTdmPlayerDeathHooksPort port,
            CodTdmPlayerRuntimeState playerState,
            CodTdmJoinedPlayerBroadcaster joinedPlayerBroadcaster
    ) {
        return new CodTdmPlayerDeathHooks(port, playerState, joinedPlayerBroadcaster);
    }

    static VoteService.Hooks createVoteHooks(
            CodTdmVoteHooksPort port,
            Map<UUID, Boolean> readyStates,
            CodTdmJoinedPlayerBroadcaster joinedPlayerBroadcaster
    ) {
        return new CodTdmVoteHooks(port, readyStates, joinedPlayerBroadcaster);
    }
}
