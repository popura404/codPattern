package com.cdp.codpattern.client.gui.screen.tdm;

public final class SelectedRoomPreviewState {
    private String roomKey;
    private TdmRoomData summarySnapshot;
    private long lastPreviewUpdatedAtMs = 0L;

    public void updateSelection(String selectedRoom, LobbySummaryState lobbySummaryState, long nowMs) {
        roomKey = (selectedRoom == null || selectedRoom.isBlank()) ? null : selectedRoom;
        syncFromLobby(lobbySummaryState, nowMs);
    }

    public void syncFromLobby(LobbySummaryState lobbySummaryState, long nowMs) {
        if (roomKey == null || roomKey.isBlank()) {
            summarySnapshot = null;
            lastPreviewUpdatedAtMs = 0L;
            return;
        }
        if (lobbySummaryState == null) {
            summarySnapshot = null;
            return;
        }
        summarySnapshot = lobbySummaryState.rooms().get(roomKey);
        if (summarySnapshot != null) {
            lastPreviewUpdatedAtMs = nowMs;
        }
    }

    public void clear() {
        roomKey = null;
        summarySnapshot = null;
        lastPreviewUpdatedAtMs = 0L;
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

    public boolean hasPreview() {
        return roomKey != null && !roomKey.isBlank() && summarySnapshot != null;
    }
}
