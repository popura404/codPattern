package com.cdp.codpattern.compat.fpsmatch.map;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;

interface CodTdmRoundLifecyclePort {
    List<ServerPlayer> getJoinedPlayers();

    List<ServerPlayer> getSpectatorPlayers();

    List<String> randomizeAllTeamSpawnsAndCollectMissingTeams();

    void teleportPlayerToRoundStartPoint(ServerPlayer player);

    void givePlayerKits(ServerPlayer player);

    void clearPlayerInventory(ServerPlayer player);
}
