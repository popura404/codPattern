package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tactical.port.CodTacticalTdmActionPort;
import com.cdp.codpattern.app.tactical.port.CodTacticalTdmReadPort;
import com.cdp.codpattern.app.tdm.model.TdmGameTypes;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public final class CodTacticalTdmMapAccess {
    private CodTacticalTdmMapAccess() {
    }

    public static CodTacticalTdmActionPort actionPort(CodTacticalTdmMap map) {
        return map.tacticalActionPort();
    }

    public static CodTacticalTdmReadPort readPort(CodTacticalTdmMap map) {
        return map.tacticalReadPort();
    }

    public static boolean isInMatch(UUID playerId) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return false;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        return player != null && findReadPortByPlayer(player).isPresent();
    }

    public static Optional<CodTacticalTdmActionPort> findActionPortByMapName(String mapName) {
        return findByName(mapName).map(CodTacticalTdmMapAccess::actionPort);
    }

    public static Optional<CodTacticalTdmActionPort> findActionPortByPlayer(ServerPlayer player) {
        return findByPlayer(player).map(CodTacticalTdmMapAccess::actionPort);
    }

    public static Optional<CodTacticalTdmReadPort> findReadPortByMapName(String mapName) {
        return findByName(mapName).map(CodTacticalTdmMapAccess::readPort);
    }

    public static Optional<CodTacticalTdmReadPort> findReadPortByPlayer(ServerPlayer player) {
        return findByPlayer(player).map(CodTacticalTdmMapAccess::readPort);
    }

    public static List<CodTacticalTdmReadPort> listReadPorts() {
        return listMaps().stream()
                .map(CodTacticalTdmMapAccess::readPort)
                .collect(Collectors.toList());
    }

    public static CodTacticalTdmMap createMap(ServerLevel level, String mapName, AreaData areaData) {
        return new CodTacticalTdmMap(level, mapName, areaData);
    }

    public static void createAndRegisterMap(ServerLevel level, String mapName, AreaData areaData) {
        registerMap(createMap(level, mapName, areaData));
    }

    public static void registerMap(CodTacticalTdmMap map) {
        FPSMCore.getInstance().registerMap(TdmGameTypes.CDP_TACTICAL_TDM, map);
    }

    private static Optional<CodTacticalTdmMap> findByName(String mapName) {
        return FPSMCore.getInstance()
                .getMapByTypeWithName(TdmGameTypes.CDP_TACTICAL_TDM, mapName)
                .filter(map -> map instanceof CodTacticalTdmMap)
                .map(map -> (CodTacticalTdmMap) map);
    }

    private static Optional<CodTacticalTdmMap> findByPlayer(ServerPlayer player) {
        return FPSMCore.getInstance()
                .getMapByPlayerWithSpec(player)
                .filter(map -> map instanceof CodTacticalTdmMap)
                .map(map -> (CodTacticalTdmMap) map);
    }

    private static List<CodTacticalTdmMap> listMaps() {
        List<BaseMap> maps = FPSMCore.getInstance()
                .getAllMaps()
                .getOrDefault(TdmGameTypes.CDP_TACTICAL_TDM, List.of());
        return maps.stream()
                .filter(map -> map instanceof CodTacticalTdmMap)
                .map(map -> (CodTacticalTdmMap) map)
                .collect(Collectors.toList());
    }
}
