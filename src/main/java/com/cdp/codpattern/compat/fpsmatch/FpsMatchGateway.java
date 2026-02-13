package com.cdp.codpattern.compat.fpsmatch;

import com.cdp.codpattern.app.tdm.port.CodTdmActionPort;
import com.cdp.codpattern.app.tdm.port.CodTdmReadPort;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FpsMatchGateway {
    boolean isInMatch(UUID playerId);

    Optional<CodTdmActionPort> findMapActionPortByName(String mapName);

    Optional<CodTdmActionPort> findPlayerTdmActionPort(ServerPlayer player);

    Optional<CodTdmReadPort> findMapReadPortByName(String mapName);

    Optional<CodTdmReadPort> findPlayerTdmReadPort(ServerPlayer player);

    void leaveCurrentMapIfDifferent(ServerPlayer player, String targetMapName);

    Optional<String> leaveCurrentMapIncludingSpectator(ServerPlayer player);

    void createAndRegisterMap(ServerLevel level, String mapName, BlockPos from, BlockPos to);

    List<CodTdmReadPort> listTdmReadPorts();
}
