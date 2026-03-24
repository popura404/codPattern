package com.cdp.codpattern.compat.fpsmatch;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.port.ModeRoomActionPort;
import com.cdp.codpattern.app.match.port.ModeRoomReadPort;
import com.cdp.codpattern.app.tdm.port.CodTdmActionPort;
import com.cdp.codpattern.app.tdm.port.CodTdmReadPort;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class NoopFpsMatchGateway implements FpsMatchGateway {
    @Override
    public boolean isInMatch(UUID playerId) {
        return false;
    }

    @Override
    public Optional<ModeRoomActionPort> findRoomActionPort(RoomId roomId) {
        return Optional.empty();
    }

    @Override
    public Optional<ModeRoomActionPort> findPlayerRoomActionPort(ServerPlayer player) {
        return Optional.empty();
    }

    @Override
    public Optional<ModeRoomReadPort> findRoomReadPort(RoomId roomId) {
        return Optional.empty();
    }

    @Override
    public Optional<ModeRoomReadPort> findPlayerRoomReadPort(ServerPlayer player) {
        return Optional.empty();
    }

    @Override
    public List<ModeRoomReadPort> listRoomReadPorts() {
        return List.of();
    }

    @Override
    public Optional<CodTdmActionPort> findMapActionPortByName(String mapName) {
        return Optional.empty();
    }

    @Override
    public Optional<CodTdmActionPort> findPlayerTdmActionPort(ServerPlayer player) {
        return Optional.empty();
    }

    @Override
    public Optional<CodTdmReadPort> findMapReadPortByName(String mapName) {
        return Optional.empty();
    }

    @Override
    public Optional<CodTdmReadPort> findPlayerTdmReadPort(ServerPlayer player) {
        return Optional.empty();
    }

    @Override
    public void leaveCurrentMapIfDifferent(ServerPlayer player, String targetMapName) {
    }

    @Override
    public Optional<String> leaveCurrentMapIncludingSpectator(ServerPlayer player) {
        return Optional.empty();
    }

    @Override
    public void createAndRegisterMap(ServerLevel level, String mapName, BlockPos from, BlockPos to) {
        throw new UnsupportedOperationException("FPSMatch gateway unavailable");
    }

    @Override
    public List<CodTdmReadPort> listTdmReadPorts() {
        return List.of();
    }
}
