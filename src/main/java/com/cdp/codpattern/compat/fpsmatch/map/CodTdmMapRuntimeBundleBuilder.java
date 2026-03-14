package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.service.KillFeedService;
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
                packet -> components.joinedPlayerBroadcaster().broadcastPacketToJoinedPlayers(packet),
                components.playerDeathHooks(),
                () -> CodTdmMapTeamViews.joinedPlayers(map)
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
                respawnRuntime::tickInvincibility,
                combatRuntime::tickCombatRegen
        );
        CodTdmMapMutationRuntime mapMutationRuntime = new CodTdmMapMutationRuntime(
                (teamName, player) -> {
                    if (map.checkSpecHasPlayer(player)) {
                        // Spectator -> in-match join on the same map should not trigger leave-room side effects.
                        map.getMapTeams().leaveTeam(player);
                        map.getMapTeams().joinTeam(teamName, player);
                    } else {
                        map.join(teamName, player);
                    }
                },
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
