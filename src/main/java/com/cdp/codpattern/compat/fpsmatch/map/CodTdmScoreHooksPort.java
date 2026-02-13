package com.cdp.codpattern.compat.fpsmatch.map;

import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

interface CodTdmScoreHooksPort {
    Optional<String> findTeamNameByPlayer(ServerPlayer player);
}
