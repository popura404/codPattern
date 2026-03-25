package com.cdp.codpattern.compat.fpsmatch;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.port.ModeRoomActionPort;
import com.cdp.codpattern.app.match.port.ModeRoomReadPort;
import com.cdp.codpattern.compat.fpsmatch.map.CodTacticalTdmMapAccess;
import com.cdp.codpattern.compat.fpsmatch.map.CodTdmMapAccess;
import com.cdp.codpattern.app.tdm.port.CodTdmActionPort;
import com.cdp.codpattern.app.tdm.port.CodTdmReadPort;
import com.cdp.codpattern.app.tdm.model.TdmGameTypes;
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
        return CodTdmMapAccess.isInMatch(playerId) || CodTacticalTdmMapAccess.isInMatch(playerId);
    }

    @Override
    public Optional<ModeRoomActionPort> findRoomActionPort(RoomId roomId) {
        if (roomId == null) {
            return Optional.empty();
        }
        if (TdmGameTypes.isFrontline(roomId.gameType())) {
            return CodTdmMapAccess.findActionPortByMapName(roomId.mapName()).map(port -> (ModeRoomActionPort) port);
        }
        if (TdmGameTypes.isTeamDeathMatch(roomId.gameType())) {
            return CodTacticalTdmMapAccess.findActionPortByMapName(roomId.mapName()).map(port -> (ModeRoomActionPort) port);
        }
        return Optional.empty();
    }

    @Override
    public Optional<ModeRoomActionPort> findPlayerRoomActionPort(ServerPlayer player) {
        Optional<ModeRoomActionPort> tdmPort = CodTdmMapAccess.findActionPortByPlayer(player)
                .map(port -> (ModeRoomActionPort) port);
        if (tdmPort.isPresent()) {
            return tdmPort;
        }
        return CodTacticalTdmMapAccess.findActionPortByPlayer(player).map(port -> (ModeRoomActionPort) port);
    }

    @Override
    public Optional<ModeRoomReadPort> findRoomReadPort(RoomId roomId) {
        if (roomId == null) {
            return Optional.empty();
        }
        if (TdmGameTypes.isFrontline(roomId.gameType())) {
            return CodTdmMapAccess.findReadPortByMapName(roomId.mapName()).map(port -> (ModeRoomReadPort) port);
        }
        if (TdmGameTypes.isTeamDeathMatch(roomId.gameType())) {
            return CodTacticalTdmMapAccess.findReadPortByMapName(roomId.mapName()).map(port -> (ModeRoomReadPort) port);
        }
        return Optional.empty();
    }

    @Override
    public Optional<ModeRoomReadPort> findPlayerRoomReadPort(ServerPlayer player) {
        Optional<ModeRoomReadPort> tdmPort = CodTdmMapAccess.findReadPortByPlayer(player)
                .map(port -> (ModeRoomReadPort) port);
        if (tdmPort.isPresent()) {
            return tdmPort;
        }
        return CodTacticalTdmMapAccess.findReadPortByPlayer(player).map(port -> (ModeRoomReadPort) port);
    }

    @Override
    public List<ModeRoomReadPort> listRoomReadPorts() {
        List<ModeRoomReadPort> ports = new java.util.ArrayList<>();
        CodTdmMapAccess.listReadPorts().forEach(port -> ports.add(port));
        CodTacticalTdmMapAccess.listReadPorts().forEach(port -> ports.add(port));
        return List.copyOf(ports);
    }

    @Override
    public Optional<CodTdmActionPort> findMapActionPortByName(String mapName) {
        return CodTdmMapAccess.findActionPortByMapName(mapName);
    }

    @Override
    public Optional<CodTdmActionPort> findPlayerTdmActionPort(ServerPlayer player) {
        Optional<CodTdmActionPort> tdmPort = CodTdmMapAccess.findActionPortByPlayer(player).map(port -> (CodTdmActionPort) port);
        if (tdmPort.isPresent()) {
            return tdmPort;
        }
        return CodTacticalTdmMapAccess.findActionPortByPlayer(player).map(port -> (CodTdmActionPort) port);
    }

    @Override
    public Optional<CodTdmReadPort> findMapReadPortByName(String mapName) {
        return CodTdmMapAccess.findReadPortByMapName(mapName);
    }

    @Override
    public Optional<CodTdmReadPort> findPlayerTdmReadPort(ServerPlayer player) {
        Optional<CodTdmReadPort> tdmPort = CodTdmMapAccess.findReadPortByPlayer(player).map(port -> (CodTdmReadPort) port);
        if (tdmPort.isPresent()) {
            return tdmPort;
        }
        return CodTacticalTdmMapAccess.findReadPortByPlayer(player).map(port -> (CodTdmReadPort) port);
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
