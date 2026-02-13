package com.cdp.codpattern.compat.fpsmatch.map;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

final record CodTdmRoundLifecycleMapPortAdapter(
        Supplier<List<ServerPlayer>> joinedPlayersSupplier,
        Supplier<List<ServerPlayer>> spectatorPlayersSupplier,
        Supplier<List<String>> missingSpawnTeamsSupplier,
        Consumer<ServerPlayer> teleportToRespawnAction,
        Consumer<ServerPlayer> givePlayerKitsAction,
        Consumer<ServerPlayer> clearPlayerInventoryAction
) implements CodTdmRoundLifecyclePort {

    @Override
    public List<ServerPlayer> getJoinedPlayers() {
        return joinedPlayersSupplier.get();
    }

    @Override
    public List<ServerPlayer> getSpectatorPlayers() {
        return spectatorPlayersSupplier.get();
    }

    @Override
    public List<String> randomizeAllTeamSpawnsAndCollectMissingTeams() {
        return missingSpawnTeamsSupplier.get();
    }

    @Override
    public void teleportPlayerToReSpawnPoint(ServerPlayer player) {
        teleportToRespawnAction.accept(player);
    }

    @Override
    public void givePlayerKits(ServerPlayer player) {
        givePlayerKitsAction.accept(player);
    }

    @Override
    public void clearPlayerInventory(ServerPlayer player) {
        clearPlayerInventoryAction.accept(player);
    }
}
