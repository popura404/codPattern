package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.model.TdmGamePhase;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

final record CodTdmVoteHooksMapPortAdapter(
        Function<UUID, Player> playerResolver,
        Supplier<List<ServerPlayer>> joinedPlayersSupplier,
        Supplier<TdmGamePhase> phaseSupplier,
        Supplier<String> mapNameSupplier,
        Runnable startGameAction,
        Runnable endVoteTransitionAction
) implements CodTdmVoteHooksPort {

    @Override
    public Player getPlayer(UUID playerId) {
        return playerResolver.apply(playerId);
    }

    @Override
    public List<ServerPlayer> getJoinedPlayers() {
        return joinedPlayersSupplier.get();
    }

    @Override
    public TdmGamePhase getPhase() {
        return phaseSupplier.get();
    }

    @Override
    public String mapName() {
        return mapNameSupplier.get();
    }

    @Override
    public void startGame() {
        startGameAction.run();
    }

    @Override
    public void transitionToEndedFromVote() {
        endVoteTransitionAction.run();
    }
}
