package com.cdp.codpattern.compat.fpsmatch;

import com.cdp.codpattern.fpsmatch.map.CodTdmMap;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

public interface FpsMatchGateway {
    boolean isInMatch(UUID playerId);

    Optional<CodTdmMap> findMapByName(String mapName);

    Optional<CodTdmMap> findPlayerTdmMap(ServerPlayer player);

    void leaveCurrentMapIfDifferent(ServerPlayer player, CodTdmMap targetMap);

    Optional<String> leaveCurrentMapIncludingSpectator(ServerPlayer player);
}
