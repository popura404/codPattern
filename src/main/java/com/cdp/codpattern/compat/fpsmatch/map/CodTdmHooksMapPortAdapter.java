package com.cdp.codpattern.compat.fpsmatch.map;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

final record CodTdmHooksMapPortAdapter(
        Supplier<List<ServerPlayer>> joinedPlayersSupplier,
        Function<ServerPlayer, Optional<String>> findTeamNameByPlayerFunction,
        Supplier<ServerLevel> serverLevelSupplier,
        Function<UUID, Player> findPlayerByIdFunction
) implements CodTdmHooksComposition.MapPort {

    @Override
    public List<ServerPlayer> joinedPlayers() {
        return joinedPlayersSupplier.get();
    }

    @Override
    public Optional<String> findTeamNameByPlayer(ServerPlayer player) {
        return findTeamNameByPlayerFunction.apply(player);
    }

    @Override
    public ServerLevel serverLevel() {
        return serverLevelSupplier.get();
    }

    @Override
    public Player findPlayerById(UUID playerId) {
        return findPlayerByIdFunction.apply(playerId);
    }
}
