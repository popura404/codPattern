package com.cdp.codpattern.compat.fpsmatch.map;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

final record CodTdmTeamMembershipMapPortAdapter(
        Consumer<UUID> clearTransientPlayerStateAction,
        Consumer<UUID> clearPlayerCombatStatsAction,
        Consumer<UUID> removePlayerFromVoteAction,
        Function<ServerPlayer, Boolean> teleportPlayerToMatchEndPointAction,
        Consumer<ServerPlayer> clearPlayerInventoryAction,
        Runnable syncToClientAction,
        BooleanSupplier waitingPhaseChecker,
        Predicate<String> teamExistsChecker,
        Predicate<String> teamFullChecker,
        Function<ServerPlayer, Optional<String>> teamNameByPlayerResolver,
        CodTdmTeamSwitchBalanceChecker teamSwitchBalanceChecker,
        Consumer<ServerPlayer> leaveTeamAction,
        BiConsumer<String, ServerPlayer> joinTeamAction,
        Supplier<ServerLevel> serverLevelSupplier,
        Supplier<String> mapNameSupplier
) implements CodTdmTeamMembershipPort {

    @Override
    public void clearTransientPlayerState(UUID playerId) {
        clearTransientPlayerStateAction.accept(playerId);
    }

    @Override
    public void clearPlayerCombatStats(UUID playerId) {
        clearPlayerCombatStatsAction.accept(playerId);
    }

    @Override
    public void removePlayerFromVote(UUID playerId) {
        removePlayerFromVoteAction.accept(playerId);
    }

    @Override
    public boolean teleportPlayerToMatchEndPoint(ServerPlayer player) {
        return teleportPlayerToMatchEndPointAction.apply(player);
    }

    @Override
    public void clearPlayerInventory(ServerPlayer player) {
        clearPlayerInventoryAction.accept(player);
    }

    @Override
    public void syncToClient() {
        syncToClientAction.run();
    }

    @Override
    public boolean isWaitingPhase() {
        return waitingPhaseChecker.getAsBoolean();
    }

    @Override
    public boolean hasTeam(String teamName) {
        return teamExistsChecker.test(teamName);
    }

    @Override
    public boolean isTeamFull(String teamName) {
        return teamFullChecker.test(teamName);
    }

    @Override
    public Optional<String> findTeamNameByPlayer(ServerPlayer player) {
        return teamNameByPlayerResolver.apply(player);
    }

    @Override
    public boolean canSwitchWithBalance(String currentTeam, String targetTeam, int maxTeamDiff) {
        return teamSwitchBalanceChecker.canSwitch(currentTeam, targetTeam, maxTeamDiff);
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
    public ServerLevel serverLevel() {
        return serverLevelSupplier.get();
    }

    @Override
    public String mapName() {
        return mapNameSupplier.get();
    }
}
