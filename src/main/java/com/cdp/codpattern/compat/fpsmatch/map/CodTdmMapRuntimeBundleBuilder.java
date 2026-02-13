package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.service.PhaseStateMachine;

final class CodTdmMapRuntimeBundleBuilder {
    private CodTdmMapRuntimeBundleBuilder() {
    }

    record RuntimeBundle(
            CodTdmVoteRuntime voteRuntime,
            CodTdmTeamMembershipCoordinator teamMembershipCoordinator,
            CodTdmClientSyncCoordinator clientSyncCoordinator,
            CodTdmMatchProgressRuntime matchProgressRuntime,
            CodTdmCombatRuntime combatRuntime,
            CodTdmPhaseRuntime phaseRuntime,
            CodTdmTickRuntime tickRuntime,
            CodTdmResetRuntime resetRuntime,
            CodTdmMapMutationRuntime mapMutationRuntime
    ) {
    }

    static RuntimeBundle build(
            CodTdmMap map,
            CodTdmPlayerRuntimeState playerState,
            CodTdmMatchRuntimeState matchState,
            CodTdmRespawnRuntime respawnRuntime,
            CodTdmMapComposition.Components components
    ) {
        PhaseStateMachine.Hooks phaseStateHooks = components.phaseStateHooks();
        CodTdmVoteRuntime voteRuntime = components.voteRuntime();
        CodTdmTeamMembershipCoordinator teamMembershipCoordinator = components.teamMembershipCoordinator();
        CodTdmClientSyncCoordinator clientSyncCoordinator = components.clientSyncCoordinator();
        CodTdmMatchProgressRuntime matchProgressRuntime = new CodTdmMatchProgressRuntime(matchState);
        CodTdmResetRuntime resetRuntime = new CodTdmResetRuntime(
                matchState,
                playerState,
                voteRuntime,
                () -> map.getMapTeams().getTeams()
        );
        CodTdmCombatRuntime combatRuntime = new CodTdmCombatRuntime(
                matchState,
                playerState,
                components.scoreServiceHooks(),
                components.playerDeathHooks()
        );
        CodTdmPhaseRuntime phaseRuntime = new CodTdmPhaseRuntime(
                matchState,
                voteRuntime,
                phaseStateHooks,
                components.joinedPlayerBroadcaster()::clearDeathHudForAllPlayers,
                map::syncToClient
        );
        CodTdmMatchLoopRuntime matchLoopRuntime =
                new CodTdmMatchLoopRuntime(matchState, phaseStateHooks, phaseRuntime::transitionToPhase);
        CodTdmTickRuntime tickRuntime = new CodTdmTickRuntime(
                matchLoopRuntime::tickPhaseStateMachine,
                voteRuntime::tickVoteSession,
                respawnRuntime::tickDeathCam,
                respawnRuntime::tickRespawn,
                respawnRuntime::tickInvincibility
        );
        CodTdmMapMutationRuntime mapMutationRuntime = new CodTdmMapMutationRuntime(
                map::join,
                map::joinSpec,
                map::syncToClient,
                map::addTeam,
                teamName -> map.getMapTeams().getTeamByName(teamName)
        );
        return new RuntimeBundle(
                voteRuntime,
                teamMembershipCoordinator,
                clientSyncCoordinator,
                matchProgressRuntime,
                combatRuntime,
                phaseRuntime,
                tickRuntime,
                resetRuntime,
                mapMutationRuntime
        );
    }
}
