package com.cdp.codpattern.client.gui.screen.match;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.model.ModeCapability;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.model.RoomSummaryMetric;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ModeRoomData {
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
    public Set<ModeCapability> capabilities;

    public ModeRoomData(String gameType,
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
                List.of(),
                Set.of());
    }

    public ModeRoomData(String gameType,
            String mapName,
            String state,
            int playerCount,
            int maxPlayers,
            Map<String, Integer> teamPlayerCounts,
            Map<String, Integer> teamScores,
            int remainingTimeTicks,
            boolean hasMatchEndTeleportPoint,
            List<RoomSummaryMetric> metrics) {
        this(gameType,
                mapName,
                state,
                playerCount,
                maxPlayers,
                teamPlayerCounts,
                teamScores,
                remainingTimeTicks,
                hasMatchEndTeleportPoint,
                metrics,
                Set.of());
    }

    public ModeRoomData(String gameType,
            String mapName,
            String state,
            int playerCount,
            int maxPlayers,
            Map<String, Integer> teamPlayerCounts,
            Map<String, Integer> teamScores,
            int remainingTimeTicks,
            boolean hasMatchEndTeleportPoint,
            List<RoomSummaryMetric> metrics,
            Set<ModeCapability> capabilities) {
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
        this.capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }

    public RoomId roomId() {
        return RoomId.of(gameType, mapName);
    }

    public String roomKey() {
        return roomId().encode();
    }

    public boolean hasCapability(ModeCapability capability) {
        if (capability == null) {
            return false;
        }
        if (!capabilities.isEmpty()) {
            return capabilities.contains(capability);
        }
        return GameModeRegistry.hasCapability(gameType, capability);
    }
}
