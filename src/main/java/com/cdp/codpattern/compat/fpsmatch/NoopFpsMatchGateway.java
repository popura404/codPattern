package com.cdp.codpattern.compat.fpsmatch;

import com.cdp.codpattern.fpsmatch.map.CodTdmMap;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

public final class NoopFpsMatchGateway implements FpsMatchGateway {
    @Override
    public boolean isInMatch(UUID playerId) {
        return false;
    }

    @Override
    public Optional<CodTdmMap> findMapByName(String mapName) {
        return Optional.empty();
    }

    @Override
    public Optional<CodTdmMap> findPlayerTdmMap(ServerPlayer player) {
        return Optional.empty();
    }

    @Override
    public void leaveCurrentMapIfDifferent(ServerPlayer player, CodTdmMap targetMap) {
    }

    @Override
    public Optional<String> leaveCurrentMapIncludingSpectator(ServerPlayer player) {
        return Optional.empty();
    }
}
