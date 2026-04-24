package com.cdp.codpattern.compat.fpsmatch.map;

import net.minecraft.server.level.ServerPlayer;

import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

final record CodTdmPhaseHooksMapPortAdapter(
        java.util.function.IntConsumer showCountdownActionBarAction,
        Runnable clearCountdownActionBarAction,
        Runnable teleportAllPlayersToSpawnAction,
        Runnable giveAllPlayersKitsAction,
        Runnable giveAllPlayersKitsSilentlyAction,
        Runnable lockWarmupMovementAction,
        Runnable unlockAllRoomPlayersMovementAction,
        Runnable clearAllPlayersInventoryAction,
        Runnable restoreAllRoomPlayersToAdventureAction,
        Runnable onMatchEndedAction,
        BooleanSupplier hasMatchEndTeleportPointSupplier,
        Supplier<Iterable<ServerPlayer>> joinedPlayersSupplier,
        Function<ServerPlayer, Boolean> teleportPlayerToMatchEndPointAction,
        Supplier<String> mapNameSupplier,
        Supplier<String> gameTypeSupplier,
        Runnable resetGameAction
) implements CodTdmPhaseHooksPort {

    @Override
    public void showCountdownActionBar(int secondsLeft) {
        showCountdownActionBarAction.accept(secondsLeft);
    }

    @Override
    public void clearCountdownActionBar() {
        clearCountdownActionBarAction.run();
    }

    @Override
    public void teleportAllPlayersToSpawn() {
        teleportAllPlayersToSpawnAction.run();
    }

    @Override
    public void giveAllPlayersKits() {
        giveAllPlayersKitsAction.run();
    }

    @Override
    public void giveAllPlayersKitsSilently() {
        giveAllPlayersKitsSilentlyAction.run();
    }

    @Override
    public void lockWarmupMovement() {
        lockWarmupMovementAction.run();
    }

    @Override
    public void unlockAllRoomPlayersMovement() {
        unlockAllRoomPlayersMovementAction.run();
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
    public boolean teleportPlayerToMatchEndPoint(ServerPlayer player) {
        return teleportPlayerToMatchEndPointAction.apply(player);
    }

    @Override
    public String mapName() {
        return mapNameSupplier.get();
    }

    @Override
    public String gameType() {
        return gameTypeSupplier.get();
    }

    @Override
    public void resetGame() {
        resetGameAction.run();
    }
}
