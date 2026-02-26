package com.cdp.codpattern.client.gui.screen.tdm;

import com.cdp.codpattern.fpsmatch.room.PlayerInfo;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TdmRoomStateEvaluator {
    private TdmRoomStateEvaluator() {
    }

    public static String currentRoomState(String joinedRoom, Map<String, TdmRoomData> rooms, String fallbackPhase) {
        if (joinedRoom == null) {
            return null;
        }
        TdmRoomData joined = rooms.get(joinedRoom);
        if (joined != null) {
            return joined.state;
        }
        return fallbackPhase;
    }

    public static boolean isTeamSwitchAllowed(String currentRoomState) {
        return "WAITING".equals(currentRoomState);
    }

    public static boolean canStartVote(String currentRoomState) {
        return "WAITING".equals(currentRoomState);
    }

    public static boolean canEndVote(String currentRoomState) {
        return "WARMUP".equals(currentRoomState) || "PLAYING".equals(currentRoomState);
    }

    public static boolean isLocalPlayerReady(UUID localPlayerId, Map<String, List<PlayerInfo>> teamPlayers) {
        if (localPlayerId == null) {
            return false;
        }
        for (List<PlayerInfo> players : teamPlayers.values()) {
            for (PlayerInfo info : players) {
                if (info.uuid().equals(localPlayerId)) {
                    return info.isReady();
                }
            }
        }
        return false;
    }

    public static boolean isLocalPlayerInTeamRoster(UUID localPlayerId, Map<String, List<PlayerInfo>> teamPlayers) {
        if (localPlayerId == null || teamPlayers == null || teamPlayers.isEmpty()) {
            return false;
        }
        for (List<PlayerInfo> players : teamPlayers.values()) {
            for (PlayerInfo info : players) {
                if (info.uuid().equals(localPlayerId)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isLocalSpectatorInPlaying(
            UUID localPlayerId,
            boolean hasJoinedRoom,
            String currentRoomState,
            Map<String, List<PlayerInfo>> teamPlayers) {
        if (!hasJoinedRoom
                || !"PLAYING".equals(currentRoomState)
                || localPlayerId == null
                || teamPlayers == null
                || teamPlayers.isEmpty()) {
            return false;
        }
        return !isLocalPlayerInTeamRoster(localPlayerId, teamPlayers);
    }
}
