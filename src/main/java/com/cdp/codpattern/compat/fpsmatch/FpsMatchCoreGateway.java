package com.cdp.codpattern.compat.fpsmatch;

import com.cdp.codpattern.compat.fpsmatch.map.CodTdmMapAccess;
import com.cdp.codpattern.app.tdm.port.CodTdmActionPort;
import com.cdp.codpattern.app.tdm.port.CodTdmReadPort;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class FpsMatchCoreGateway implements FpsMatchGateway {
    @Override
    public boolean isInMatch(UUID playerId) {
        return CodTdmMapAccess.isInMatch(playerId);
    }

    @Override
    public Optional<CodTdmActionPort> findMapActionPortByName(String mapName) {
        return CodTdmMapAccess.findActionPortByMapName(mapName);
    }

    @Override
    public Optional<CodTdmActionPort> findPlayerTdmActionPort(ServerPlayer player) {
        return CodTdmMapAccess.findActionPortByPlayer(player);
    }

    @Override
    public Optional<CodTdmReadPort> findMapReadPortByName(String mapName) {
        return CodTdmMapAccess.findReadPortByMapName(mapName);
    }

    @Override
    public Optional<CodTdmReadPort> findPlayerTdmReadPort(ServerPlayer player) {
        return CodTdmMapAccess.findReadPortByPlayer(player);
    }

    @Override
    public void leaveCurrentMapIfDifferent(ServerPlayer player, String targetMapName) {
        CodTdmMapAccess.leaveCurrentMapIfDifferent(player, targetMapName);
    }

    @Override
    public Optional<String> leaveCurrentMapIncludingSpectator(ServerPlayer player) {
        return CodTdmMapAccess.leaveCurrentMapIncludingSpectator(player);
    }

    @Override
    public void createAndRegisterMap(ServerLevel level, String mapName, BlockPos from, BlockPos to) {
        CodTdmMapAccess.createAndRegisterMap(level, mapName, new AreaData(from, to));
    }

    @Override
    public List<CodTdmReadPort> listTdmReadPorts() {
        return CodTdmMapAccess.listReadPorts();
    }

}
