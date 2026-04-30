package com.cdp.codpattern.network.match;

import com.cdp.codpattern.app.match.model.ModeCapability;
import com.cdp.codpattern.app.match.model.RoomSummaryMetric;
import com.cdp.codpattern.app.match.model.RoomSummarySnapshot;
import com.cdp.codpattern.app.match.model.TeamSummarySnapshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mode-neutral room list payload used after packet decoding.
 */
public class RoomSyncInfo {
    public String state;
    public int playerCount;
    public int maxPlayers;
    public Map<String, Integer> teamPlayerCounts;
    public Map<String, Integer> teamScores;
    public int remainingTimeTicks;
    public boolean hasMatchEndTeleportPoint;
    public List<RoomSummaryMetric> metrics;
    public Set<ModeCapability> capabilities;

    public RoomSyncInfo(String state, int playerCount, int maxPlayers, Map<String, Integer> teamPlayerCounts,
            Map<String, Integer> teamScores, int remainingTimeTicks, boolean hasMatchEndTeleportPoint) {
        this(state,
                playerCount,
                maxPlayers,
                teamPlayerCounts,
                teamScores,
                remainingTimeTicks,
                hasMatchEndTeleportPoint,
                List.of(),
                Set.of());
    }

    public RoomSyncInfo(String state, int playerCount, int maxPlayers, Map<String, Integer> teamPlayerCounts,
            Map<String, Integer> teamScores, int remainingTimeTicks, boolean hasMatchEndTeleportPoint,
            List<RoomSummaryMetric> metrics) {
        this(state,
                playerCount,
                maxPlayers,
                teamPlayerCounts,
                teamScores,
                remainingTimeTicks,
                hasMatchEndTeleportPoint,
                metrics,
                Set.of());
    }

    public RoomSyncInfo(String state, int playerCount, int maxPlayers, Map<String, Integer> teamPlayerCounts,
            Map<String, Integer> teamScores, int remainingTimeTicks, boolean hasMatchEndTeleportPoint,
            List<RoomSummaryMetric> metrics, Set<ModeCapability> capabilities) {
        this.state = state;
        this.playerCount = playerCount;
        this.maxPlayers = maxPlayers;
        this.teamPlayerCounts = teamPlayerCounts == null ? Map.of() : teamPlayerCounts;
        this.teamScores = teamScores == null ? Map.of() : teamScores;
        this.remainingTimeTicks = remainingTimeTicks;
        this.hasMatchEndTeleportPoint = hasMatchEndTeleportPoint;
        this.metrics = metrics == null ? List.of() : List.copyOf(metrics);
        this.capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }

    public static RoomSyncInfo fromSnapshot(RoomSummarySnapshot snapshot, boolean hasMatchEndTeleportPoint) {
        Map<String, Integer> teamPlayerCounts = new HashMap<>();
        Map<String, Integer> teamScores = new HashMap<>();
        for (TeamSummarySnapshot team : snapshot.teams()) {
            teamPlayerCounts.put(team.teamName(), team.playerCount());
            teamScores.put(team.teamName(), team.score());
        }
        return new RoomSyncInfo(
                snapshot.lifecycleStateKey(),
                snapshot.playerCount(),
                snapshot.maxPlayers(),
                teamPlayerCounts,
                teamScores,
                snapshot.remainingTimeTicks(),
                hasMatchEndTeleportPoint,
                snapshot.metrics(),
                snapshot.capabilities());
    }
}
