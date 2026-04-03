package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.service.PhaseStateMachine;
import com.cdp.codpattern.app.tdm.service.PlayerDeathService;
import com.cdp.codpattern.app.tdm.service.ScoreService;
import com.cdp.codpattern.app.tdm.service.VoteService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

final class CodTdmMapComposition {
    private CodTdmMapComposition() {
    }

    record Components(
            CodTdmJoinedPlayerBroadcaster joinedPlayerBroadcaster,
            PhaseStateMachine.Hooks phaseStateHooks,
            ScoreService.Hooks scoreServiceHooks,
            PlayerDeathService.Hooks playerDeathHooks,
            CodTdmVoteRuntime voteRuntime,
            CodTdmTeamMembershipCoordinator teamMembershipCoordinator,
            CodTdmClientSyncCoordinator clientSyncCoordinator
    ) {
    }

    static Components compose(
            CodTdmPlayerRuntimeState playerState,
            CodTdmMatchRuntimeState matchState,
            Supplier<String> mapNameSupplier,
            Consumer<ServerPlayer> clearPlayerInventoryAction,
            Consumer<ServerPlayer> leaveFromBaseMapAction,
            Consumer<ServerPlayer> scheduleRespawnAction,
            BooleanSupplier hasMatchEndTeleportPointSupplier,
            Function<ServerPlayer, Boolean> teleportToMatchEndPointAction,
            CodTdmHooksComposition.MapPort hooksMapPort,
            CodTdmCoordinatorComposition.MapPort coordinatorMapPort,
            Supplier<Iterable<ServerPlayer>> joinedPlayersSupplier,
            Runnable startGameAction,
            Runnable transitionToEndedAction,
            Runnable resetGameAction,
            Function<MinecraftServer, Path> matchRecordDirResolver
    ) {
        CodTdmHooksComposition.SupportHooksBundle supportHooks = CodTdmHooksComposition.createSupportHooks(
                playerState,
                hooksMapPort,
                clearPlayerInventoryAction,
                scheduleRespawnAction,
                matchState::phase,
                hasMatchEndTeleportPointSupplier,
                mapNameSupplier,
                startGameAction,
                transitionToEndedAction
        );
        CodTdmCoordinatorComposition.CoordinatorsBundle coordinators = CodTdmCoordinatorComposition.create(
                coordinatorMapPort,
                playerState,
                matchState,
                supportHooks.voteService(),
                mapNameSupplier,
                scheduleRespawnAction,
                clearPlayerInventoryAction,
                leaveFromBaseMapAction,
                teleportToMatchEndPointAction,
                resetGameAction
        );
        CodTdmMatchResultExporter matchResultExporter = new CodTdmMatchResultExporter(
                coordinatorMapPort,
                matchState,
                playerState,
                mapNameSupplier,
                matchRecordDirResolver
        );
        PhaseStateMachine.Hooks phaseStateHooks = CodTdmHooksComposition.createPhaseStateHooks(
                playerState,
                supportHooks.joinedPlayerBroadcaster(),
                new CodTdmPhaseStateMapPortAdapter(
                        coordinators.roundLifecycleCoordinator(),
                        hasMatchEndTeleportPointSupplier,
                        joinedPlayersSupplier,
                        player -> teleportToMatchEndPointAction.apply(player),
                        mapNameSupplier,
                        resetGameAction,
                        matchResultExporter::exportOnMatchEnded
                )
        );
        CodTdmVoteRuntime voteRuntime = new CodTdmVoteRuntime(supportHooks.voteService(), coordinators.voteCoordinator());

        return new Components(
                supportHooks.joinedPlayerBroadcaster(),
                phaseStateHooks,
                supportHooks.scoreServiceHooks(),
                supportHooks.playerDeathHooks(),
                voteRuntime,
                coordinators.teamMembershipCoordinator(),
                coordinators.clientSyncCoordinator()
        );
    }
}
