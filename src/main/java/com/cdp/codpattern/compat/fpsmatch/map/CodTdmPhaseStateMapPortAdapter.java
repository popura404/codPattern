package com.cdp.codpattern.compat.fpsmatch.map;

import net.minecraft.server.level.ServerPlayer;

import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

final record CodTdmPhaseStateMapPortAdapter(
        CodTdmRoundLifecycleCoordinator roundLifecycleCoordinator,
        BooleanSupplier hasMatchEndTeleportPointSupplier,
        Supplier<Iterable<ServerPlayer>> joinedPlayersSupplier,
        Function<ServerPlayer, Boolean> teleportToMatchEndPointAction,
        Supplier<String> mapNameSupplier,
        Runnable resetGameAction,
        Runnable onMatchEndedAction
) implements CodTdmHooksComposition.PhasePort {

    @Override
    public void teleportAllPlayersToSpawn() {
        roundLifecycleCoordinator.teleportAllPlayersToSpawn();
    }

    @Override
    public void giveAllPlayersKits() {
        roundLifecycleCoordinator.giveAllPlayersKits();
    }

    @Override
    public void clearAllPlayersInventory() {
        roundLifecycleCoordinator.clearAllPlayersInventory();
    }

    @Override
    public void restoreAllRoomPlayersToAdventure() {
        roundLifecycleCoordinator.restoreAllRoomPlayersToAdventure();
    }

    @Override
    public void onMatchEnded() {
        onMatchEndedAction.run();
    }

    @Override
    public boolean hasMatchEndTeleportPoint() {
        return hasMatchEndTeleportPointSupplier.getAsBoolean();
    }

    @Override
    public Iterable<ServerPlayer> joinedPlayers() {
        return joinedPlayersSupplier.get();
    }

    @Override
    public boolean teleportPlayerToMatchEndPoint(ServerPlayer player) {
        return teleportToMatchEndPointAction.apply(player);
    }

    @Override
    public String mapName() {
        return mapNameSupplier.get();
    }

    @Override
    public void resetGame() {
        resetGameAction.run();
    }
}
