package com.cdp.codpattern.compat.fpsmatch;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.ModeRoomBackedMap;
import com.cdp.codpattern.app.match.ModeRoomHandle;
import com.cdp.codpattern.app.match.model.ModeDescriptor;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.port.ModeRoomActionPort;
import com.cdp.codpattern.app.match.port.ModeCombatEventPort;
import com.cdp.codpattern.app.match.port.ModeRoomReadPort;
import com.cdp.codpattern.app.match.port.ModeRoomSummaryPort;
import com.cdp.codpattern.app.tdm.model.TdmGameTypes;
import com.cdp.codpattern.app.tdm.port.CodTdmActionPort;
import com.cdp.codpattern.app.tdm.port.CodTdmReadPort;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class FpsMatchCoreGateway implements FpsMatchGateway {
    @Override
    public boolean isInMatch(UUID playerId) {
        return findRoomHandleByPlayerId(playerId).isPresent();
    }

    @Override
    public Optional<ModeRoomActionPort> findRoomActionPort(RoomId roomId) {
        return findRoomHandle(roomId).map(ModeRoomHandle::actionPort);
    }

    @Override
    public Optional<ModeRoomActionPort> findPlayerRoomActionPort(ServerPlayer player) {
        return findPlayerRoomHandle(player).map(ModeRoomHandle::actionPort);
    }

    @Override
    public Optional<ModeRoomReadPort> findRoomReadPort(RoomId roomId) {
        return findRoomHandle(roomId).flatMap(FpsMatchCoreGateway::legacyReadPort);
    }

    @Override
    public Optional<ModeRoomReadPort> findPlayerRoomReadPort(ServerPlayer player) {
        return findPlayerRoomHandle(player).flatMap(FpsMatchCoreGateway::legacyReadPort);
    }

    @Override
    public Optional<ModeCombatEventPort> findPlayerCombatEventPort(ServerPlayer player) {
        return findPlayerRoomHandle(player).flatMap(ModeRoomHandle::combatEventPort);
    }

    @Override
    public List<ModeRoomSummaryPort> listRoomSummaryPorts() {
        List<ModeRoomSummaryPort> ports = new ArrayList<>();
        for (ModeRoomHandle handle : listRoomHandles()) {
            ports.add(handle.summaryPort());
        }
        return List.copyOf(ports);
    }

    @Override
    public List<ModeRoomReadPort> listRoomReadPorts() {
        List<ModeRoomReadPort> ports = new ArrayList<>();
        for (ModeRoomHandle handle : listRoomHandles()) {
            legacyReadPort(handle).ifPresent(ports::add);
        }
        return List.copyOf(ports);
    }

    @Override
    public Optional<CodTdmActionPort> findMapActionPortByName(String mapName) {
        return findRoomHandle(RoomId.of(TdmGameTypes.CDP_TDM, mapName))
                .flatMap(FpsMatchCoreGateway::legacyTdmActionPort);
    }

    @Override
    public Optional<CodTdmActionPort> findPlayerTdmActionPort(ServerPlayer player) {
        return findPlayerRoomHandle(player).flatMap(FpsMatchCoreGateway::legacyTdmActionPort);
    }

    @Override
    public Optional<CodTdmReadPort> findMapReadPortByName(String mapName) {
        return findRoomHandle(RoomId.of(TdmGameTypes.CDP_TDM, mapName))
                .flatMap(FpsMatchCoreGateway::legacyTdmReadPort);
    }

    @Override
    public Optional<CodTdmReadPort> findPlayerTdmReadPort(ServerPlayer player) {
        return findPlayerRoomHandle(player).flatMap(FpsMatchCoreGateway::legacyTdmReadPort);
    }

    @Override
    public void leaveCurrentMapIfDifferent(ServerPlayer player, String targetMapName) {
        findPlayerRoomHandle(player).ifPresent(handle -> {
            if (!handle.roomId().mapName().equals(targetMapName)) {
                handle.actionPort().leave(player);
            }
        });
    }

    @Override
    public Optional<String> leaveCurrentMapIncludingSpectator(ServerPlayer player) {
        Optional<ModeRoomHandle> handleOptional = findPlayerRoomHandle(player);
        handleOptional.ifPresent(handle -> handle.actionPort().leave(player));
        return handleOptional.map(handle -> handle.roomId().mapName());
    }

    @Override
    public void createAndRegisterMap(ServerLevel level, String mapName, BlockPos from, BlockPos to) {
        FPSMCore core = FPSMCore.getInstance();
        var factory = core.getPreBuildGame(TdmGameTypes.CDP_TDM);
        if (factory == null) {
            return;
        }
        core.registerMap(TdmGameTypes.CDP_TDM, factory.apply(level, mapName, new AreaData(from, to)));
    }

    @Override
    public List<CodTdmReadPort> listTdmReadPorts() {
        List<CodTdmReadPort> ports = new ArrayList<>();
        for (ModeRoomHandle handle : listRoomHandles()) {
            if (TdmGameTypes.CDP_TDM.equals(GameModeRegistry.canonicalize(handle.roomId().gameType()))) {
                legacyTdmReadPort(handle).ifPresent(ports::add);
            }
        }
        return List.copyOf(ports);
    }

    private static Optional<ModeRoomHandle> findRoomHandle(RoomId roomId) {
        if (roomId == null || !FPSMCore.initialized()) {
            return Optional.empty();
        }
        String canonicalGameType = GameModeRegistry.canonicalize(roomId.gameType());
        for (ModeRoomHandle handle : listRoomHandles()) {
            RoomId handleRoomId = handle.roomId();
            if (handleRoomId.mapName().equals(roomId.mapName())
                    && GameModeRegistry.canonicalize(handleRoomId.gameType()).equals(canonicalGameType)) {
                return Optional.of(handle);
            }
        }
        return Optional.empty();
    }

    private static Optional<ModeRoomHandle> findPlayerRoomHandle(ServerPlayer player) {
        if (player == null || !FPSMCore.initialized()) {
            return Optional.empty();
        }
        return FPSMCore.getInstance()
                .getMapByPlayerWithSpec(player)
                .flatMap(FpsMatchCoreGateway::roomHandle);
    }

    private static Optional<ModeRoomHandle> findRoomHandleByPlayerId(UUID playerId) {
        if (playerId == null || !FPSMCore.initialized()) {
            return Optional.empty();
        }
        return FPSMCore.getInstance()
                .getPlayerByUUID(playerId)
                .flatMap(FpsMatchCoreGateway::findPlayerRoomHandle);
    }

    private static List<ModeRoomHandle> listRoomHandles() {
        if (!FPSMCore.initialized()) {
            return List.of();
        }
        Map<String, List<BaseMap>> allMaps = FPSMCore.getInstance().getAllMaps();
        List<ModeRoomHandle> handles = new ArrayList<>();
        Set<String> handledGameTypes = new LinkedHashSet<>();
        for (ModeDescriptor descriptor : GameModeRegistry.orderedModes()) {
            String canonicalGameType = GameModeRegistry.canonicalize(descriptor.gameType());
            handledGameTypes.add(canonicalGameType);
            appendRoomHandles(allMaps.getOrDefault(canonicalGameType, List.of()), handles);
        }
        for (Map.Entry<String, List<BaseMap>> entry : allMaps.entrySet()) {
            String canonicalGameType = GameModeRegistry.canonicalize(entry.getKey());
            if (handledGameTypes.add(canonicalGameType)) {
                appendRoomHandles(entry.getValue(), handles);
            }
        }
        return List.copyOf(handles);
    }

    private static void appendRoomHandles(List<BaseMap> maps, List<ModeRoomHandle> handles) {
        for (BaseMap map : maps) {
            roomHandle(map).ifPresent(handles::add);
        }
    }

    private static Optional<ModeRoomHandle> roomHandle(BaseMap map) {
        if (map instanceof ModeRoomBackedMap backedMap) {
            return Optional.ofNullable(backedMap.roomHandle());
        }
        return Optional.empty();
    }

    private static Optional<ModeRoomReadPort> legacyReadPort(ModeRoomHandle handle) {
        if (handle != null && handle.summaryPort() instanceof ModeRoomReadPort readPort) {
            return Optional.of(readPort);
        }
        return Optional.empty();
    }

    private static Optional<CodTdmReadPort> legacyTdmReadPort(ModeRoomHandle handle) {
        if (handle != null && handle.summaryPort() instanceof CodTdmReadPort readPort) {
            return Optional.of(readPort);
        }
        return Optional.empty();
    }

    private static Optional<CodTdmActionPort> legacyTdmActionPort(ModeRoomHandle handle) {
        if (handle != null && handle.actionPort() instanceof CodTdmActionPort actionPort) {
            return Optional.of(actionPort);
        }
        return Optional.empty();
    }
}
