package com.cdp.codpattern.client.gui.screen.tdm;

import com.cdp.codpattern.app.match.model.ModeCapability;
import com.cdp.codpattern.app.match.model.RoomSummaryMetric;
import com.cdp.codpattern.client.gui.screen.match.ModeRoomData;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Legacy DTO name retained for older callers. New code should use {@link ModeRoomData}.
 */
@Deprecated(forRemoval = false)
public class TdmRoomData extends ModeRoomData {
    public TdmRoomData(String gameType,
            String mapName,
            String state,
            int playerCount,
            int maxPlayers,
            Map<String, Integer> teamPlayerCounts,
            Map<String, Integer> teamScores,
            int remainingTimeTicks,
            boolean hasMatchEndTeleportPoint) {
        super(gameType,
                mapName,
                state,
                playerCount,
                maxPlayers,
                teamPlayerCounts,
                teamScores,
                remainingTimeTicks,
                hasMatchEndTeleportPoint);
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
        super(gameType,
                mapName,
                state,
                playerCount,
                maxPlayers,
                teamPlayerCounts,
                teamScores,
                remainingTimeTicks,
                hasMatchEndTeleportPoint,
                metrics);
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
            List<RoomSummaryMetric> metrics,
            Set<ModeCapability> capabilities) {
        super(gameType,
                mapName,
                state,
                playerCount,
                maxPlayers,
                teamPlayerCounts,
                teamScores,
                remainingTimeTicks,
                hasMatchEndTeleportPoint,
                metrics,
                capabilities);
    }
}
