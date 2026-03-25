package com.cdp.codpattern.compat.fpsmatch.map;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

final record CodTdmRoundLifecycleMapPortAdapter(
        Supplier<List<ServerPlayer>> joinedPlayersSupplier,
        Supplier<List<ServerPlayer>> spectatorPlayersSupplier,
        Supplier<List<String>> missingSpawnTeamsSupplier,
        Predicate<ServerPlayer> teleportToRoundStartAction,
        java.util.function.Consumer<ServerPlayer> givePlayerKitsAction,
        java.util.function.Consumer<ServerPlayer> clearPlayerInventoryAction,
        java.util.function.Consumer<ServerPlayer> scheduleRespawnAction
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
    public boolean teleportPlayerToRoundStartPoint(ServerPlayer player) {
        return teleportToRoundStartAction.test(player);
    }

    @Override
    public void givePlayerKits(ServerPlayer player) {
        givePlayerKitsAction.accept(player);
    }

    @Override
    public void clearPlayerInventory(ServerPlayer player) {
        clearPlayerInventoryAction.accept(player);
    }

    @Override
    public void scheduleRespawn(ServerPlayer player) {
        scheduleRespawnAction.accept(player);
    }
}
