package com.cdp.codpattern.client.gui.screen.tdm;

import com.cdp.codpattern.app.match.model.RoomId;

import java.util.Map;

public class TdmRoomData {
    public String gameType;
    public String mapName;
    public String state;
    public int playerCount;
    public int maxPlayers;
    public Map<String, Integer> teamPlayerCounts;
    public Map<String, Integer> teamScores;
    public int remainingTimeTicks;
    public boolean hasMatchEndTeleportPoint;

    public TdmRoomData(String gameType,
            String mapName,
            String state,
            int playerCount,
            int maxPlayers,
            Map<String, Integer> teamPlayerCounts,
            Map<String, Integer> teamScores,
            int remainingTimeTicks,
            boolean hasMatchEndTeleportPoint) {
        this.gameType = gameType;
        this.mapName = mapName;
        this.state = state;
        this.playerCount = playerCount;
        this.maxPlayers = maxPlayers;
        this.teamPlayerCounts = teamPlayerCounts;
        this.teamScores = teamScores;
        this.remainingTimeTicks = remainingTimeTicks;
        this.hasMatchEndTeleportPoint = hasMatchEndTeleportPoint;
    }

    public RoomId roomId() {
        return RoomId.of(gameType, mapName);
    }

    public String roomKey() {
        return roomId().encode();
    }
}
