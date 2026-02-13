package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.model.TdmGamePhase;

import com.cdp.codpattern.app.tdm.service.PhaseStateMachine;
import com.cdp.codpattern.app.tdm.service.PlayerDeathService;
import com.cdp.codpattern.app.tdm.service.ScoreService;
import com.cdp.codpattern.app.tdm.service.VoteService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class CodTdmHooksComposition {
    private CodTdmHooksComposition() {
    }

    record SupportHooksBundle(
            CodTdmJoinedPlayerBroadcaster joinedPlayerBroadcaster,
            ScoreService.Hooks scoreServiceHooks,
            PlayerDeathService.Hooks playerDeathHooks,
            VoteService voteService
    ) {
    }

    interface MapPort {
        List<ServerPlayer> joinedPlayers();

        Optional<String> findTeamNameByPlayer(ServerPlayer player);

        ServerLevel serverLevel();

        Player findPlayerById(UUID playerId);
    }

    interface PhasePort {
        void teleportAllPlayersToSpawn();

        void giveAllPlayersKits();

        void clearAllPlayersInventory();

        void restoreAllRoomPlayersToAdventure();

        boolean hasMatchEndTeleportPoint();

        Iterable<ServerPlayer> joinedPlayers();

        void teleportPlayerToMatchEndPoint(ServerPlayer player);

        String mapName();

        void resetGame();
    }

    static SupportHooksBundle createSupportHooks(
            CodTdmPlayerRuntimeState playerState,
            MapPort mapPort,
            Consumer<ServerPlayer> clearPlayerInventoryAction,
            Consumer<ServerPlayer> scheduleRespawnAction,
            Supplier<TdmGamePhase> phaseSupplier,
            Supplier<String> mapNameSupplier,
            Runnable startGameAction,
            Runnable transitionToEndedAction
    ) {
        Supplier<List<ServerPlayer>> joinedPlayersSupplier = mapPort::joinedPlayers;
        CodTdmJoinedPlayerBroadcaster joinedPlayerBroadcaster = new CodTdmJoinedPlayerBroadcaster(joinedPlayersSupplier);

        ScoreService.Hooks scoreServiceHooks = CodTdmMapHooks.createScoreHooks(
                new CodTdmScoreHooksMapPortAdapter(mapPort::findTeamNameByPlayer),
                joinedPlayerBroadcaster
        );

        PlayerDeathService.Hooks playerDeathHooks = CodTdmMapHooks.createPlayerDeathHooks(
                new CodTdmPlayerDeathHooksMapPortAdapter(
                        clearPlayerInventoryAction,
                        mapPort::serverLevel,
                        scheduleRespawnAction
                ),
                playerState,
                joinedPlayerBroadcaster
        );

        VoteService voteService = new VoteService(CodTdmMapHooks.createVoteHooks(
                new CodTdmVoteHooksMapPortAdapter(
                        mapPort::findPlayerById,
                        joinedPlayersSupplier,
                        phaseSupplier,
                        mapNameSupplier,
                        startGameAction,
                        transitionToEndedAction
                ),
                playerState.readyStates(),
                joinedPlayerBroadcaster
        ));

        return new SupportHooksBundle(
                joinedPlayerBroadcaster,
                scoreServiceHooks,
                playerDeathHooks,
                voteService
        );
    }

    static PhaseStateMachine.Hooks createPhaseStateHooks(
            CodTdmPlayerRuntimeState playerState,
            CodTdmJoinedPlayerBroadcaster joinedPlayerBroadcaster,
            PhasePort phasePort
    ) {
        return CodTdmMapHooks.createPhaseHooks(
                new CodTdmPhaseHooksMapPortAdapter(
                        phasePort::teleportAllPlayersToSpawn,
                        phasePort::giveAllPlayersKits,
                        phasePort::clearAllPlayersInventory,
                        phasePort::restoreAllRoomPlayersToAdventure,
                        phasePort::hasMatchEndTeleportPoint,
                        phasePort::joinedPlayers,
                        phasePort::teleportPlayerToMatchEndPoint,
                        phasePort::mapName,
                        phasePort::resetGame
                ),
                playerState,
                joinedPlayerBroadcaster
        );
    }
}
