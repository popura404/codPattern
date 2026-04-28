package com.cdp.codpattern.client.gui.screen.tdm;

import com.cdp.codpattern.app.match.model.RoomSummaryMetric;
import com.cdp.codpattern.app.match.model.RoomId;

import java.util.List;
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
    public List<RoomSummaryMetric> metrics;

    public TdmRoomData(String gameType,
            String mapName,
            String state,
            int playerCount,
            int maxPlayers,
            Map<String, Integer> teamPlayerCounts,
            Map<String, Integer> teamScores,
            int remainingTimeTicks,
            boolean hasMatchEndTeleportPoint) {
        this(gameType,
                mapName,
                state,
                playerCount,
                maxPlayers,
                teamPlayerCounts,
                teamScores,
                remainingTimeTicks,
                hasMatchEndTeleportPoint,
                List.of());
    }

    public TdmRoomData(String gameType,
            String mapName,
            String state,
            int playerCount,
            int maxPlayers,
            Map<String, Integer> teamPlayerCounts,
            Map<String, Integer> teamScores,
            int remainingTimeTicks,
            boolean hasMatchEndTeleportPoint,
            List<RoomSummaryMetric> metrics) {
        this.gameType = gameType;
        this.mapName = mapName;
        this.state = state;
        this.playerCount = playerCount;
        this.maxPlayers = maxPlayers;
        this.teamPlayerCounts = teamPlayerCounts;
        this.teamScores = teamScores;
        this.remainingTimeTicks = remainingTimeTicks;
        this.hasMatchEndTeleportPoint = hasMatchEndTeleportPoint;
        this.metrics = metrics == null ? List.of() : List.copyOf(metrics);
    }

    public RoomId roomId() {
        return RoomId.of(gameType, mapName);
    }

    public String roomKey() {
        return roomId().encode();
    }
}
