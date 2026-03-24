package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.service.TeamBalanceService;
import com.cdp.codpattern.app.tdm.service.TeamPlayerSnapshotService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

final record CodTdmCoordinatorMapPortAdapter(
        Predicate<UUID> containsJoinedPlayerPredicate,
        Runnable syncToClientAction,
        Supplier<List<TeamBalanceService.TeamSnapshot>> teamBalanceSnapshotsSupplier,
        Predicate<String> hasTeamPredicate,
        Predicate<String> isTeamFullPredicate,
        Function<ServerPlayer, Optional<String>> findTeamNameByPlayerFunction,
        Consumer<ServerPlayer> leaveTeamAction,
        BiConsumer<String, ServerPlayer> joinTeamAction,
        Supplier<String> gameTypeSupplier,
        Supplier<ServerLevel> serverLevelSupplier,
        Supplier<List<ServerPlayer>> joinedPlayersSupplier,
        Supplier<List<ServerPlayer>> spectatorPlayersSupplier,
        Supplier<List<String>> missingSpawnTeamsSupplier,
        Predicate<ServerPlayer> teleportPlayerToRoundStartAction,
        Consumer<ServerPlayer> givePlayerKitsAction,
        Supplier<List<TeamPlayerSnapshotService.TeamRoster>> teamRostersSupplier
) implements CodTdmCoordinatorComposition.MapPort {

    @Override
    public boolean containsJoinedPlayer(UUID playerId) {
        return containsJoinedPlayerPredicate.test(playerId);
    }

    @Override
    public void syncToClient() {
        syncToClientAction.run();
    }

    @Override
    public List<TeamBalanceService.TeamSnapshot> teamBalanceSnapshots() {
        return teamBalanceSnapshotsSupplier.get();
    }

    @Override
    public boolean hasTeam(String teamName) {
        return hasTeamPredicate.test(teamName);
    }

    @Override
    public boolean isTeamFull(String teamName) {
        return isTeamFullPredicate.test(teamName);
    }

    @Override
    public Optional<String> findTeamNameByPlayer(ServerPlayer player) {
        return findTeamNameByPlayerFunction.apply(player);
    }

    @Override
    public void leaveTeam(ServerPlayer player) {
        leaveTeamAction.accept(player);
    }

    @Override
    public void joinTeam(String teamName, ServerPlayer player) {
        joinTeamAction.accept(teamName, player);
    }

    @Override
    public String gameType() {
        return gameTypeSupplier.get();
    }

    @Override
    public ServerLevel serverLevel() {
        return serverLevelSupplier.get();
    }

    @Override
    public List<ServerPlayer> joinedPlayers() {
        return joinedPlayersSupplier.get();
    }

    @Override
    public List<ServerPlayer> spectatorPlayers() {
        return spectatorPlayersSupplier.get();
    }

    @Override
    public List<String> randomizeAllTeamSpawnsAndCollectMissingTeams() {
        return missingSpawnTeamsSupplier.get();
    }

    @Override
    public boolean teleportPlayerToRoundStartPoint(ServerPlayer player) {
        return teleportPlayerToRoundStartAction.test(player);
    }

    @Override
    public void givePlayerKits(ServerPlayer player) {
        givePlayerKitsAction.accept(player);
    }

    @Override
    public List<TeamPlayerSnapshotService.TeamRoster> teamRosters() {
        return teamRostersSupplier.get();
    }
}
