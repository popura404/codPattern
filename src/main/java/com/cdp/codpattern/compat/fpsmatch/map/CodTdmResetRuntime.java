package com.cdp.codpattern.compat.fpsmatch.map;

import com.phasetranscrystal.fpsmatch.core.map.BaseTeam;

import java.util.function.Supplier;

final class CodTdmResetRuntime {
    private final CodTdmMatchRuntimeState matchState;
    private final CodTdmPlayerRuntimeState playerState;
    private final CodTdmVoteRuntime voteRuntime;
    private final Supplier<Iterable<BaseTeam>> teamsSupplier;

    CodTdmResetRuntime(
            CodTdmMatchRuntimeState matchState,
            CodTdmPlayerRuntimeState playerState,
            CodTdmVoteRuntime voteRuntime,
            Supplier<Iterable<BaseTeam>> teamsSupplier
    ) {
        this.matchState = matchState;
        this.playerState = playerState;
        this.voteRuntime = voteRuntime;
        this.teamsSupplier = teamsSupplier;
    }

    void resetGameState() {
        matchState.resetCoreState();
        playerState.clearAll();
        voteRuntime.clearAll();

        for (BaseTeam team : teamsSupplier.get()) {
            matchState.putTeamScore(team.name, 0);
        }
    }
}
