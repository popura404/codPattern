package com.cdp.codpattern.client.gui.screen.match;

import com.cdp.codpattern.client.ClientMatchState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JoinedRoomLiveState {
    private static final long STALE_THRESHOLD_MS = 3000L;

    private String roomKey;
    private String phase = "WAITING";
    private int remainingTimeTicks = 0;
    private int rosterVersion = 0;
    private long lastPhaseSyncAtMs = 0L;
    private long lastScoreSyncAtMs = 0L;
    private long lastRosterSyncAtMs = 0L;
    private Map<String, Integer> teamScores = new HashMap<>();
    private Map<String, List<com.cdp.codpattern.fpsmatch.room.PlayerInfo>> teamPlayers = new HashMap<>();

    public void setRoomKey(String roomKey) {
        this.roomKey = roomKey;
    }

    public void clear() {
        roomKey = null;
        phase = "WAITING";
        remainingTimeTicks = 0;
        rosterVersion = 0;
        lastPhaseSyncAtMs = 0L;
        lastScoreSyncAtMs = 0L;
        lastRosterSyncAtMs = 0L;
        teamScores = new HashMap<>();
        teamPlayers = new HashMap<>();
    }

    public void refreshFromClientState() {
        String clientRoomContext = ClientMatchState.roomContextName();
        if ((roomKey == null || roomKey.isBlank()) && clientRoomContext != null && !clientRoomContext.isBlank()) {
            roomKey = clientRoomContext;
        }
        phase = ClientMatchState.currentPhase();
        remainingTimeTicks = ClientMatchState.remainingTimeTicks();
        long latestPhaseSyncAtMs = ClientMatchState.lastPhaseSyncAtMs();
        long latestScoreSyncAtMs = ClientMatchState.lastScoreSyncAtMs();
        long latestRosterSyncAtMs = ClientMatchState.lastRosterSyncAtMs();
        if (latestScoreSyncAtMs != lastScoreSyncAtMs || teamScores.isEmpty()) {
            teamScores = ClientMatchState.teamScoresSnapshot();
        }
        if (latestRosterSyncAtMs != lastRosterSyncAtMs || ClientMatchState.rosterVersion() != rosterVersion) {
            teamPlayers = ClientMatchState.teamPlayersSnapshot();
        }
        lastPhaseSyncAtMs = latestPhaseSyncAtMs;
        lastScoreSyncAtMs = latestScoreSyncAtMs;
        lastRosterSyncAtMs = latestRosterSyncAtMs;
        rosterVersion = ClientMatchState.rosterVersion();
    }

    public String roomKey() {
        return roomKey;
    }

    public String phase() {
        return phase;
    }

    public int remainingTimeTicks() {
        return remainingTimeTicks;
    }

    public int rosterVersion() {
        return rosterVersion;
    }

    public long lastPhaseSyncAtMs() {
        return lastPhaseSyncAtMs;
    }

    public long lastScoreSyncAtMs() {
        return lastScoreSyncAtMs;
    }

    public long lastRosterSyncAtMs() {
        return lastRosterSyncAtMs;
    }

    public Map<String, Integer> teamScores() {
        return teamScores;
    }

    public Map<String, List<com.cdp.codpattern.fpsmatch.room.PlayerInfo>> teamPlayers() {
        return teamPlayers;
    }

    public boolean hasLiveRoom() {
        return roomKey != null && !roomKey.isBlank();
    }

    public boolean isStale(long nowMs) {
        long latestSyncAtMs = Math.max(lastPhaseSyncAtMs, Math.max(lastScoreSyncAtMs, lastRosterSyncAtMs));
        return latestSyncAtMs > 0L && nowMs - latestSyncAtMs >= STALE_THRESHOLD_MS;
    }

    public int secondsSinceLatestSync(long nowMs) {
        long latestSyncAtMs = Math.max(lastPhaseSyncAtMs, Math.max(lastScoreSyncAtMs, lastRosterSyncAtMs));
        if (latestSyncAtMs <= 0L || nowMs <= latestSyncAtMs) {
            return 0;
        }
        return (int) ((nowMs - latestSyncAtMs) / 1000L);
    }
}
