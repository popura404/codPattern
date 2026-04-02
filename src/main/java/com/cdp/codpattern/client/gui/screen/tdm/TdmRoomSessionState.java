package com.cdp.codpattern.client.gui.screen.tdm;

import com.cdp.codpattern.client.ClientTdmState;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;

import java.util.List;
import java.util.Map;

public final class TdmRoomSessionState {
    private final LobbySummaryState lobbySummaryState = new LobbySummaryState();
    private final JoinedRoomLiveState joinedRoomLiveState = new JoinedRoomLiveState();
    private final SelectedRoomPreviewState selectedRoomPreviewState = new SelectedRoomPreviewState();
    private String selectedRoom;
    private String joinedRoom;

    public LobbySummaryState lobbySummaryState() {
        return lobbySummaryState;
    }

    public JoinedRoomLiveState joinedRoomLiveState() {
        return joinedRoomLiveState;
    }

    public SelectedRoomPreviewState selectedRoomPreviewState() {
        return selectedRoomPreviewState;
    }

    public Map<String, TdmRoomData> rooms() {
        return lobbySummaryState.rooms();
    }

    public void setRooms(Map<String, TdmRoomData> rooms) {
        lobbySummaryState.applySnapshot(rooms, lobbySummaryState.snapshotVersion(), System.currentTimeMillis());
        selectedRoomPreviewState.syncFromLobby(lobbySummaryState, System.currentTimeMillis());
    }

    public String selectedRoom() {
        return selectedRoom;
    }

    public void setSelectedRoom(String selectedRoom) {
        this.selectedRoom = selectedRoom;
        selectedRoomPreviewState.updateSelection(selectedRoom, lobbySummaryState, System.currentTimeMillis());
    }

    public void beginSelectedRoomPreviewRosterLoad() {
        selectedRoomPreviewState.beginRosterLoading(System.currentTimeMillis());
    }

    public void updateSelectedRoomPreviewRoster(String roomKey, int rosterVersion, Map<String, List<PlayerInfo>> teamPlayers) {
        selectedRoomPreviewState.applyRoster(roomKey, rosterVersion, teamPlayers, System.currentTimeMillis());
    }

    public String joinedRoom() {
        return joinedRoom;
    }

    public void setJoinedRoom(String joinedRoom) {
        this.joinedRoom = joinedRoom;
        if (joinedRoom == null || joinedRoom.isBlank()) {
            joinedRoomLiveState.clear();
            return;
        }
        joinedRoomLiveState.setRoomKey(joinedRoom);
    }

    public Map<String, List<PlayerInfo>> teamPlayers() {
        return joinedRoomLiveState.teamPlayers();
    }

    public void setTeamPlayers(Map<String, List<PlayerInfo>> teamPlayers) {
        // 当前 UI 的实时名单从 ClientTdmState 拉取，这里保留兼容入口。
        joinedRoomLiveState.refreshFromClientState();
    }

    public void clearTeamPlayers() {
        joinedRoomLiveState.clear();
        if (joinedRoom != null && !joinedRoom.isBlank()) {
            joinedRoomLiveState.setRoomKey(joinedRoom);
        }
    }

    public boolean restoreJoinedRoomFromClientState() {
        if (joinedRoom != null && !joinedRoom.isBlank()) {
            return false;
        }
        String clientRoomContext = ClientTdmState.roomContextName();
        if (clientRoomContext == null || clientRoomContext.isBlank()) {
            return false;
        }
        setJoinedRoom(clientRoomContext);
        return true;
    }

    public void refreshJoinedRoomLiveState() {
        restoreJoinedRoomFromClientState();
        if (joinedRoom == null || joinedRoom.isBlank()) {
            joinedRoomLiveState.clear();
            return;
        }
        joinedRoomLiveState.setRoomKey(joinedRoom);
        joinedRoomLiveState.refreshFromClientState();
    }

    public String currentRoomState(String fallbackPhase) {
        return TdmRoomStateEvaluator.currentRoomState(
                joinedRoom,
                rooms(),
                joinedRoomLiveState.phase(),
                fallbackPhase);
    }
}
