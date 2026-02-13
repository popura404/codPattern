package com.cdp.codpattern.compat.fpsmatch.map;

import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;
import java.util.function.Supplier;

final class CodTdmMapComponentsAssembler {
    private CodTdmMapComponentsAssembler() {
    }

    static CodTdmMapComposition.Components compose(
            CodTdmMap map,
            CodTdmPlayerRuntimeState playerState,
            CodTdmMatchRuntimeState matchState,
            CodTdmRespawnRuntime respawnRuntime,
            CodTdmEndTeleportRuntime endTeleportRuntime,
            Supplier<String> mapNameSupplier,
            Consumer<ServerPlayer> leaveFromBaseMapAction
    ) {
        CodTdmHooksMapPortAdapter hooksMapPort = new CodTdmHooksMapPortAdapter(
                () -> CodTdmMapTeamViews.joinedPlayers(map),
                player -> CodTdmMapTeamViews.findTeamNameByPlayer(map, player),
                map::getServerLevel,
                playerId -> map.getServerLevel().getPlayerByUUID(playerId)
        );
        CodTdmCoordinatorMapPortAdapter coordinatorMapPort = new CodTdmCoordinatorMapPortAdapter(
                map::checkGameHasPlayer,
                map::syncToClient,
                () -> CodTdmMapTeamViews.teamBalanceSnapshots(map),
                teamName -> map.getMapTeams().checkTeam(teamName),
                teamName -> map.getMapTeams().testTeamIsFull(teamName),
                player -> CodTdmMapTeamViews.findTeamNameByPlayer(map, player),
                player -> map.getMapTeams().leaveTeam(player),
                map::join,
                map::getServerLevel,
                () -> CodTdmMapTeamViews.joinedPlayers(map),
                () -> CodTdmMapTeamViews.spectatorPlayers(map),
                () -> CodTdmMapTeamViews.randomizeAllTeamSpawnsAndCollectMissingTeams(map),
                map::teleportPlayerToReSpawnPoint,
                map::givePlayerKits,
                () -> CodTdmMapTeamViews.teamRosters(map)
        );
        return CodTdmMapComposition.compose(
                playerState,
                matchState,
                mapNameSupplier,
                map::clearPlayerInventory,
                leaveFromBaseMapAction,
                respawnRuntime::scheduleRespawn,
                endTeleportRuntime::hasMatchEndTeleportPoint,
                endTeleportRuntime::teleportPlayerToMatchEndPoint,
                hooksMapPort,
                coordinatorMapPort,
                coordinatorMapPort::joinedPlayers,
                map::startGame,
                map::victory,
                map::resetGame
        );
    }
}
