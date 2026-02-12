package com.cdp.codpattern.client.gui.screen.tdm;

import com.cdp.codpattern.fpsmatch.room.PlayerInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TdmRoomSessionState {
    private Map<String, TdmRoomData> rooms = new HashMap<>();
    private String selectedRoom;
    private String joinedRoom;
    private Map<String, List<PlayerInfo>> teamPlayers = new HashMap<>();

    public Map<String, TdmRoomData> rooms() {
        return rooms;
    }

    public void setRooms(Map<String, TdmRoomData> rooms) {
        this.rooms = rooms == null ? new HashMap<>() : rooms;
    }

    public String selectedRoom() {
        return selectedRoom;
    }

    public void setSelectedRoom(String selectedRoom) {
        this.selectedRoom = selectedRoom;
    }

    public String joinedRoom() {
        return joinedRoom;
    }

    public void setJoinedRoom(String joinedRoom) {
        this.joinedRoom = joinedRoom;
    }

    public Map<String, List<PlayerInfo>> teamPlayers() {
        return teamPlayers;
    }

    public void setTeamPlayers(Map<String, List<PlayerInfo>> teamPlayers) {
        this.teamPlayers = teamPlayers == null ? new HashMap<>() : teamPlayers;
    }

    public void clearTeamPlayers() {
        teamPlayers.clear();
    }

    public String currentRoomState(String fallbackPhase) {
        return TdmRoomStateEvaluator.currentRoomState(joinedRoom, rooms, fallbackPhase);
    }
}
