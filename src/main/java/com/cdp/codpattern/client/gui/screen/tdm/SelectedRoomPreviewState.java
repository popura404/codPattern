package com.cdp.codpattern.client.gui.screen.tdm;

import com.cdp.codpattern.fpsmatch.room.PlayerInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SelectedRoomPreviewState {
    private String roomKey;
    private TdmRoomData summarySnapshot;
    private long lastPreviewUpdatedAtMs = 0L;
    private Map<String, List<PlayerInfo>> teamPlayers = new HashMap<>();
    private int rosterVersion = 0;
    private long lastRosterUpdatedAtMs = 0L;
    private long lastRosterRequestedAtMs = 0L;
    private boolean rosterLoading = false;

    public void updateSelection(String selectedRoom, LobbySummaryState lobbySummaryState, long nowMs) {
        String normalizedRoom = (selectedRoom == null || selectedRoom.isBlank()) ? null : selectedRoom;
        if (!Objects.equals(roomKey, normalizedRoom)) {
            clearRoster();
        }
        roomKey = normalizedRoom;
        syncFromLobby(lobbySummaryState, nowMs);
    }

    public void syncFromLobby(LobbySummaryState lobbySummaryState, long nowMs) {
        if (roomKey == null || roomKey.isBlank()) {
            summarySnapshot = null;
            lastPreviewUpdatedAtMs = 0L;
            clearRoster();
            return;
        }
        if (lobbySummaryState == null) {
            summarySnapshot = null;
            return;
        }
        summarySnapshot = lobbySummaryState.rooms().get(roomKey);
        if (summarySnapshot != null) {
            lastPreviewUpdatedAtMs = nowMs;
        } else {
            clearRoster();
        }
    }

    public void clear() {
        roomKey = null;
        summarySnapshot = null;
        lastPreviewUpdatedAtMs = 0L;
        clearRoster();
    }

    public void beginRosterLoading(long nowMs) {
        if (roomKey == null || roomKey.isBlank()) {
            return;
        }
        rosterLoading = true;
        lastRosterRequestedAtMs = nowMs;
    }

    public boolean shouldRequestRoster(long nowMs, int expectedPlayerCount, long refreshIntervalMs, long retryIntervalMs) {
        if (roomKey == null || roomKey.isBlank() || summarySnapshot == null) {
            return false;
        }
        if (rosterLoading && lastRosterRequestedAtMs > 0L && nowMs - lastRosterRequestedAtMs < retryIntervalMs) {
            return false;
        }
        if (lastRosterUpdatedAtMs <= 0L) {
            return true;
        }
        if (expectedPlayerCount >= 0 && totalPlayerCount() != expectedPlayerCount) {
            return true;
        }
        return nowMs - lastRosterUpdatedAtMs >= refreshIntervalMs;
    }

    public void applyRoster(String roomKey, int rosterVersion, Map<String, List<PlayerInfo>> teamPlayers, long nowMs) {
        if (this.roomKey == null || !this.roomKey.equals(roomKey)) {
            return;
        }
        this.teamPlayers = teamPlayers == null ? new HashMap<>() : new HashMap<>(teamPlayers);
        this.rosterVersion = Math.max(0, rosterVersion);
        this.lastRosterUpdatedAtMs = nowMs;
        this.lastRosterRequestedAtMs = nowMs;
        this.rosterLoading = false;
    }

    public void clearRoster() {
        teamPlayers = new HashMap<>();
        rosterVersion = 0;
        lastRosterUpdatedAtMs = 0L;
        lastRosterRequestedAtMs = 0L;
        rosterLoading = false;
    }

    public String roomKey() {
        return roomKey;
    }

    public TdmRoomData summarySnapshot() {
        return summarySnapshot;
    }

    public long lastPreviewUpdatedAtMs() {
        return lastPreviewUpdatedAtMs;
    }

    public Map<String, List<PlayerInfo>> teamPlayers() {
        return teamPlayers;
    }

    public int rosterVersion() {
        return rosterVersion;
    }

    public long lastRosterUpdatedAtMs() {
        return lastRosterUpdatedAtMs;
    }

    public boolean isRosterLoading() {
        return rosterLoading;
    }

    public boolean hasRosterSnapshot() {
        return lastRosterUpdatedAtMs > 0L;
    }

    public int totalPlayerCount() {
        int total = 0;
        for (List<PlayerInfo> players : teamPlayers.values()) {
            total += players == null ? 0 : players.size();
        }
        return total;
    }

    public boolean hasPreview() {
        return roomKey != null && !roomKey.isBlank() && summarySnapshot != null;
    }
}
