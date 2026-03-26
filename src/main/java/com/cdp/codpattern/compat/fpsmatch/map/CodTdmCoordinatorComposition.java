package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.tdm.model.TdmGamePhase;
import com.cdp.codpattern.app.tdm.service.TeamBalanceService;
import com.cdp.codpattern.app.tdm.service.TeamPlayerSnapshotService;
import com.cdp.codpattern.app.tdm.service.VoteService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

final class CodTdmCoordinatorComposition {
    private CodTdmCoordinatorComposition() {
    }

    record CoordinatorsBundle(
            CodTdmVoteCoordinator voteCoordinator,
            CodTdmTeamMembershipCoordinator teamMembershipCoordinator,
            CodTdmRoundLifecycleCoordinator roundLifecycleCoordinator,
            CodTdmClientSyncCoordinator clientSyncCoordinator
    ) {
    }

    interface MapPort {
        boolean containsJoinedPlayer(UUID playerId);

        void syncToClient();

        List<TeamBalanceService.TeamSnapshot> teamBalanceSnapshots();

        boolean hasTeam(String teamName);

        boolean isTeamFull(String teamName);

        Optional<String> findTeamNameByPlayer(ServerPlayer player);

        void leaveTeam(ServerPlayer player);

        void joinTeam(String teamName, ServerPlayer player);

        void removePlayerById(UUID playerId);

        String gameType();

        ServerLevel serverLevel();

        List<ServerPlayer> joinedPlayers();

        List<ServerPlayer> spectatorPlayers();

        List<String> randomizeAllTeamSpawnsAndCollectMissingTeams();

        boolean teleportPlayerToRoundStartPoint(ServerPlayer player);

        void givePlayerKits(ServerPlayer player);

        List<TeamPlayerSnapshotService.TeamRoster> teamRosters();
    }

    static CoordinatorsBundle create(
            MapPort mapPort,
            CodTdmPlayerRuntimeState playerState,
            CodTdmMatchRuntimeState matchState,
            VoteService voteService,
            Supplier<String> mapNameSupplier,
            Consumer<ServerPlayer> scheduleRespawnAction,
            Consumer<ServerPlayer> clearPlayerInventoryAction,
            Consumer<ServerPlayer> leaveFromBaseMapAction,
            Function<ServerPlayer, Boolean> teleportToMatchEndPointAction,
            Runnable resetGameAction
    ) {
        CodTdmVoteCoordinator voteCoordinator = new CodTdmVoteCoordinator(
                playerState.readyStates(),
                voteService,
                matchState::phase,
                mapPort::containsJoinedPlayer,
                mapPort::syncToClient
        );
        CodTdmTeamSwitchBalanceChecker teamSwitchBalanceChecker =
                (currentTeam, targetTeam, maxTeamDiff) -> TeamBalanceService.canSwitchWithBalance(
                        mapPort.teamBalanceSnapshots(),
                        currentTeam,
                        targetTeam,
                        maxTeamDiff
                );

        CodTdmTeamMembershipCoordinator teamMembershipCoordinator = new CodTdmTeamMembershipCoordinator(
                new CodTdmTeamMembershipMapPortAdapter(
                        playerState::clearTransientPlayerState,
                        playerState::removePlayerCombatStats,
                        voteCoordinator::removePlayer,
                        teleportToMatchEndPointAction,
                        clearPlayerInventoryAction,
                        mapPort::syncToClient,
                        playerState::setSpectatorPreferredJoinTeam,
                        playerState::getSpectatorPreferredJoinTeam,
                        playerState::clearSpectatorPreferredJoinTeam,
                        () -> matchState.phase() == TdmGamePhase.WAITING,
                        () -> mapPort.teamBalanceSnapshots().stream().anyMatch(snapshot -> snapshot.playerCount() > 0),
                        playerState::markDisconnected,
                        playerState::clearDisconnected,
                        playerState::disconnectedGraceTimers,
                        mapPort::removePlayerById,
                        mapPort::hasTeam,
                        mapPort::isTeamFull,
                        mapPort::findTeamNameByPlayer,
                        teamSwitchBalanceChecker,
                        mapPort::leaveTeam,
                        mapPort::joinTeam,
                        mapPort::serverLevel,
                        mapNameSupplier
                ),
                leaveFromBaseMapAction,
                resetGameAction
        );

        CodTdmRoundLifecycleCoordinator roundLifecycleCoordinator = new CodTdmRoundLifecycleCoordinator(
                new CodTdmRoundLifecycleMapPortAdapter(
                        mapPort::joinedPlayers,
                        mapPort::spectatorPlayers,
                        mapPort::randomizeAllTeamSpawnsAndCollectMissingTeams,
                        mapPort::teleportPlayerToRoundStartPoint,
                        mapPort::givePlayerKits,
                        clearPlayerInventoryAction,
                        scheduleRespawnAction
                )
        );

        CodTdmClientSyncCoordinator clientSyncCoordinator = new CodTdmClientSyncCoordinator(
                new CodTdmClientSyncMapPortAdapter(
                        () -> RoomId.of(mapPort.gameType(), mapNameSupplier.get()).encode(),
                        () -> TeamPlayerSnapshotService.buildTeamPlayers(
                                mapPort.teamRosters(),
                                uuid -> mapPort.serverLevel().getPlayerByUUID(uuid),
                                playerState.readyStates(),
                                playerState.playerKills(),
                                playerState.playerDeaths(),
                                playerState.maxKillStreaks(),
                                playerState.respawnTimers().keySet(),
                                playerState.invinciblePlayers()
                        ),
                        mapPort::joinedPlayers,
                        mapPort::spectatorPlayers
                ),
                matchState::phase,
                matchState::phaseTimer,
                matchState::gameTimeTicks,
                matchState::teamScores
        );

        return new CoordinatorsBundle(
                voteCoordinator,
                teamMembershipCoordinator,
                roundLifecycleCoordinator,
                clientSyncCoordinator
        );
    }
}
