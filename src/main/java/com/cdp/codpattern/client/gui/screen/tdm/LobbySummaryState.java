package com.cdp.codpattern.client.gui.screen.tdm;

import java.util.HashMap;
import java.util.Map;

public final class LobbySummaryState {
    private static final long STALE_THRESHOLD_MS = 3000L;

    private Map<String, TdmRoomData> rooms = new HashMap<>();
    private long snapshotVersion = 0L;
    private long lastUpdatedAtMs = 0L;
    private boolean loading = true;
    private boolean loaded = false;

    public void beginLoading() {
        loading = true;
        if (!loaded) {
            rooms = new HashMap<>();
            snapshotVersion = 0L;
            lastUpdatedAtMs = 0L;
        }
    }

    public void applySnapshot(Map<String, TdmRoomData> nextRooms, long nextSnapshotVersion, long nowMs) {
        rooms = nextRooms == null ? new HashMap<>() : new HashMap<>(nextRooms);
        snapshotVersion = Math.max(0L, nextSnapshotVersion);
        lastUpdatedAtMs = nowMs;
        loading = false;
        loaded = true;
    }

    public Map<String, TdmRoomData> rooms() {
        return rooms;
    }

    public long snapshotVersion() {
        return snapshotVersion;
    }

    public long lastUpdatedAtMs() {
        return lastUpdatedAtMs;
    }

    public boolean isLoading() {
        return loading;
    }

    public boolean hasLoaded() {
        return loaded;
    }

    public boolean isEmpty() {
        return rooms.isEmpty();
    }

    public boolean isStale(long nowMs) {
        return loaded && lastUpdatedAtMs > 0L && nowMs - lastUpdatedAtMs >= STALE_THRESHOLD_MS;
    }

    public int secondsSinceLastUpdate(long nowMs) {
        if (lastUpdatedAtMs <= 0L || nowMs <= lastUpdatedAtMs) {
            return 0;
        }
        return (int) ((nowMs - lastUpdatedAtMs) / 1000L);
    }
}
