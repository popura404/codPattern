package com.cdp.codpattern.compat.fpsmatch.map;

import net.minecraft.server.level.ServerPlayer;

interface CodTdmPhaseHooksPort {
    void showCountdownActionBar(int secondsLeft);

    void clearCountdownActionBar();

    void teleportAllPlayersToSpawn();

    void giveAllPlayersKits();

    void giveAllPlayersKitsSilently();

    void lockWarmupMovement();

    void unlockAllRoomPlayersMovement();

    void clearAllPlayersInventory();

    void restoreAllRoomPlayersToAdventure();

    void onMatchEnded();

    boolean hasMatchEndTeleportPoint();

    Iterable<ServerPlayer> getJoinedPlayers();

    boolean teleportPlayerToMatchEndPoint(ServerPlayer player);

    String mapName();

    String gameType();

    void resetGame();
}
