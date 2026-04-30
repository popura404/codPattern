package com.cdp.codpattern.client.gui.screen.tdm;

import com.cdp.codpattern.client.gui.screen.match.ModeRoomData;
import com.cdp.codpattern.client.gui.screen.match.ModeRoomStateEvaluator;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Legacy helper name retained for older callers. New code should use {@link ModeRoomStateEvaluator}.
 */
public final class TdmRoomStateEvaluator {
    private TdmRoomStateEvaluator() {
    }

    public static String currentRoomState(
            String joinedRoom,
            Map<String, ModeRoomData> rooms,
            String livePhase,
            String fallbackPhase
    ) {
        return ModeRoomStateEvaluator.currentRoomState(joinedRoom, rooms, livePhase, fallbackPhase);
    }

    public static boolean isTeamSwitchAllowed(String currentRoomState) {
        return ModeRoomStateEvaluator.isTeamSwitchAllowed(currentRoomState);
    }

    public static boolean canStartVote(String currentRoomState) {
        return ModeRoomStateEvaluator.canStartVote(currentRoomState);
    }

    public static boolean canEndVote(String currentRoomState) {
        return ModeRoomStateEvaluator.canEndVote(currentRoomState);
    }

    public static boolean isLocalPlayerReady(UUID localPlayerId, Map<String, List<PlayerInfo>> teamPlayers) {
        return ModeRoomStateEvaluator.isLocalPlayerReady(localPlayerId, teamPlayers);
    }
}
