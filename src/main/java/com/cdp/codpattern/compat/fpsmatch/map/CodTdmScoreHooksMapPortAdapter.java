package com.cdp.codpattern.compat.fpsmatch.map;

import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.function.Function;

final record CodTdmScoreHooksMapPortAdapter(
        Function<ServerPlayer, Optional<String>> teamNameResolver
) implements CodTdmScoreHooksPort {

    @Override
    public Optional<String> findTeamNameByPlayer(ServerPlayer player) {
        return teamNameResolver.apply(player);
    }
}
