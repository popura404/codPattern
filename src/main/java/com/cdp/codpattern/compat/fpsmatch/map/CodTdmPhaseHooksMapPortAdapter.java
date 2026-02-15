package com.cdp.codpattern.compat.fpsmatch.map;

import net.minecraft.server.level.ServerPlayer;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

final record CodTdmPhaseHooksMapPortAdapter(
        Runnable teleportAllPlayersToSpawnAction,
        Runnable giveAllPlayersKitsAction,
        Runnable clearAllPlayersInventoryAction,
        Runnable restoreAllRoomPlayersToAdventureAction,
        Runnable onMatchEndedAction,
        BooleanSupplier hasMatchEndTeleportPointSupplier,
        Supplier<Iterable<ServerPlayer>> joinedPlayersSupplier,
        Consumer<ServerPlayer> teleportPlayerToMatchEndPointAction,
        Supplier<String> mapNameSupplier,
        Runnable resetGameAction
) implements CodTdmPhaseHooksPort {

    @Override
    public void teleportAllPlayersToSpawn() {
        teleportAllPlayersToSpawnAction.run();
    }

    @Override
    public void giveAllPlayersKits() {
        giveAllPlayersKitsAction.run();
    }

    @Override
    public void clearAllPlayersInventory() {
        clearAllPlayersInventoryAction.run();
    }

    @Override
    public void restoreAllRoomPlayersToAdventure() {
        restoreAllRoomPlayersToAdventureAction.run();
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
    public Iterable<ServerPlayer> getJoinedPlayers() {
        return joinedPlayersSupplier.get();
    }

    @Override
    public void teleportPlayerToMatchEndPoint(ServerPlayer player) {
        teleportPlayerToMatchEndPointAction.accept(player);
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
