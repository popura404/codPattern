package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.model.TdmGameTypes;
import com.cdp.codpattern.app.tdm.port.CodTdmActionPort;
import com.cdp.codpattern.app.tdm.port.CodTdmReadPort;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.UUID;

public final class CodTdmMapAccess {
    private CodTdmMapAccess() {
    }

    public static CodTdmActionPort actionPort(CodTdmMap map) {
        return map.actionPort();
    }

    public static boolean isInMatch(UUID playerId) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return false;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        return player != null && findReadPortByPlayer(player).isPresent();
    }

    public static Optional<CodTdmActionPort> findActionPortByMapName(String mapName) {
        return findByName(mapName).map(CodTdmMapAccess::actionPort);
    }

    public static Optional<CodTdmActionPort> findActionPortByPlayer(ServerPlayer player) {
        return findByPlayer(player).map(CodTdmMapAccess::actionPort);
    }

    public static Optional<CodTdmReadPort> findReadPortByMapName(String mapName) {
        return findByName(mapName).map(CodTdmMapAccess::readPort);
    }

    public static Optional<CodTdmReadPort> findReadPortByPlayer(ServerPlayer player) {
        return findByPlayer(player).map(CodTdmMapAccess::readPort);
    }

    public static CodTdmReadPort readPort(CodTdmMap map) {
        return map.readPort();
    }

    public static List<CodTdmReadPort> listReadPorts() {
        return listMaps().stream()
                .map(CodTdmMapAccess::readPort)
                .collect(Collectors.toList());
    }

    public static void leaveMap(BaseMap map, ServerPlayer player) {
        if (map instanceof CodTacticalTdmMap tacticalMap) {
            CodTacticalTdmMapAccess.actionPort(tacticalMap).leaveRoom(player);
            return;
        }
        if (map instanceof CodTdmMap tdmMap) {
            actionPort(tdmMap).leaveRoom(player);
            return;
        }
        map.leave(player);
    }

    public static void leaveCurrentMapIfDifferent(ServerPlayer player, String targetMapName) {
        findAnyByPlayer(player).ifPresent(currentMap -> {
            if (targetMapName.equals(mapNameOf(currentMap))) {
                return;
            }
            leaveMap(currentMap, player);
        });
    }

    public static Optional<String> leaveCurrentMapIncludingSpectator(ServerPlayer player) {
        Optional<BaseMap> mapOptional = findAnyByPlayerIncludingSpectator(player);
        mapOptional.ifPresent(map -> leaveMap(map, player));
        return mapOptional.map(CodTdmMapAccess::mapNameOf);
    }

    public static void createAndRegisterMap(ServerLevel level, String mapName, AreaData areaData) {
        register(create(level, mapName, areaData));
    }

    public static CodTdmMap createMap(ServerLevel level, String mapName, AreaData areaData) {
        return create(level, mapName, areaData);
    }

    public static void registerMap(CodTdmMap map) {
        register(map);
    }

    private static Optional<CodTdmMap> findByName(String mapName) {
        return asTdmMap(core().getMapByTypeWithName(TdmGameTypes.CDP_TDM, mapName));
    }

    private static Optional<CodTdmMap> findByPlayer(ServerPlayer player) {
        return asTdmMap(core().getMapByPlayer(player));
    }

    private static Optional<BaseMap> findAnyByPlayer(ServerPlayer player) {
        return core().getMapByPlayer(player);
    }

    private static Optional<BaseMap> findAnyByPlayerIncludingSpectator(ServerPlayer player) {
        return core().getMapByPlayerWithSpec(player);
    }

    private static List<CodTdmMap> listMaps() {
        List<BaseMap> maps = core()
                .getAllMaps()
                .getOrDefault(TdmGameTypes.CDP_TDM, List.of());
        return maps.stream()
                .filter(map -> map instanceof CodTdmMap)
                .map(map -> (CodTdmMap) map)
                .collect(Collectors.toList());
    }

    private static void register(CodTdmMap map) {
        core().registerMap(TdmGameTypes.CDP_TDM, map);
    }

    private static CodTdmMap create(ServerLevel level, String mapName, AreaData areaData) {
        return new CodTdmMap(level, mapName, areaData);
    }

    private static Optional<CodTdmMap> asTdmMap(Optional<BaseMap> mapOptional) {
        return mapOptional
                .filter(map -> map instanceof CodTdmMap && !(map instanceof CodTacticalTdmMap))
                .map(map -> (CodTdmMap) map);
    }

    private static String mapNameOf(BaseMap map) {
        if (map instanceof CodTdmMap tdmMap) {
            return readPort(tdmMap).mapName();
        }
        return map.mapName;
    }

    private static FPSMCore core() {
        return FPSMCore.getInstance();
    }
}
