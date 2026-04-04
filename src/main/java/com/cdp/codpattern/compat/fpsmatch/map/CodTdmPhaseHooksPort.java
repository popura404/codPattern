package com.cdp.codpattern.compat.fpsmatch.map;

import net.minecraft.server.level.ServerPlayer;

interface CodTdmPhaseHooksPort {
    void teleportAllPlayersToSpawn();

    void giveAllPlayersKits();

    void clearAllPlayersInventory();

    void restoreAllRoomPlayersToAdventure();

    void onMatchEnded();

    boolean hasMatchEndTeleportPoint();

    Iterable<ServerPlayer> getJoinedPlayers();

    boolean teleportPlayerToMatchEndPoint(ServerPlayer player);

    String mapName();

    void resetGame();
}
