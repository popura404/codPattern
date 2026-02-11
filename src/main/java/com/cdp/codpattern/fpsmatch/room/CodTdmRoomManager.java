package com.cdp.codpattern.fpsmatch.room;

import com.cdp.codpattern.fpsmatch.map.CodTdmMap;
import com.cdp.codpattern.network.handler.PacketHandler;
import com.cdp.codpattern.network.tdm.RoomListSyncPacket;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.map.BaseTeam;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.stream.Collectors;

/**
 * TDM 房间管理器（单例）
 * 从 FPSMCore 获取 TDM 类型的地图
 */
public class CodTdmRoomManager {
    private static CodTdmRoomManager INSTANCE = null;

    private CodTdmRoomManager() {
    }

    /**
     * 获取单例实例
     */
    public static CodTdmRoomManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new CodTdmRoomManager();
        }
        return INSTANCE;
    }

    /**
     * 获取所有 TDM 地图
     */
    public List<CodTdmMap> getAllMaps() {
        List<BaseMap> maps = FPSMCore.getInstance().getAllMaps().getOrDefault(CodTdmMap.GAME_TYPE, new ArrayList<>());
        return maps.stream()
                .filter(map -> map instanceof CodTdmMap)
                .map(map -> (CodTdmMap) map)
                .collect(Collectors.toList());
    }

    /**
     * 向客户端同步房间列表
     */
    public void syncRoomListToClient(ServerPlayer player) {
        Map<String, RoomListSyncPacket.RoomInfo> roomInfos = new HashMap<>();

        for (CodTdmMap map : getAllMaps()) {
            // 计算玩家数和最大玩家数
            int playerCount = map.getMapTeams().getJoinedPlayers().size();
            int maxPlayers = 0;
            Map<String, Integer> teamPlayerCounts = new HashMap<>();
            Map<String, Integer> teamScores = map.getTeamScoresSnapshot();
            int remainingTimeTicks = map.getRemainingTimeTicks();

            for (BaseTeam team : map.getMapTeams().getTeams()) {
                maxPlayers += team.getPlayerLimit();
                teamPlayerCounts.put(team.name, team.getPlayerList().size());
            }

            RoomListSyncPacket.RoomInfo info = new RoomListSyncPacket.RoomInfo(
                    map.getPhase().name(),
                    playerCount,
                    maxPlayers,
                    teamPlayerCounts,
                    teamScores,
                    remainingTimeTicks,
                    map.hasMatchEndTeleportPoint());
            roomInfos.put(map.mapName, info);
        }

        PacketHandler.sendToPlayer(new RoomListSyncPacket(roomInfos), player);
    }

    /**
     * 根据地图名获取地图
     */
    public Optional<CodTdmMap> getMap(String mapName) {
        return FPSMCore.getInstance().getMapByName(mapName)
                .filter(map -> map instanceof CodTdmMap)
                .map(map -> (CodTdmMap) map);
    }

    /**
     * 获取玩家所在的 TDM 地图
     */
    public Optional<CodTdmMap> getPlayerMap(UUID playerId) {
        return getAllMaps().stream()
                .filter(map -> map.checkGameHasPlayer(playerId))
                .findFirst();
    }

    /**
     * 同步房间列表到客户端（重载方法）
     */
    public void syncToClient(ServerPlayer player) {
        syncRoomListToClient(player);
    }
}
