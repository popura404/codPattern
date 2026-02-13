package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;

interface CodTdmClientSyncPort {
    String mapName();

    Map<String, List<PlayerInfo>> getTeamPlayers();

    List<ServerPlayer> getJoinedPlayers();
}
