package com.cdp.codpattern.client.gui.screen.tdm;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class TdmRoomButtonStateBinder {
    private TdmRoomButtonStateBinder() {
    }

    public static void refresh(
            Button joinButton,
            Button leaveButton,
            Button readyButton,
            Button voteStartButton,
            Button voteEndButton,
            Button kortacButton,
            Button specgruButton,
            boolean hasSelectedRoom,
            boolean selectedDifferentRoom,
            boolean hasJoinedRoom,
            boolean hasPendingAction,
            String currentRoomState,
            TdmRoomUiState.PendingAction pendingAction,
            boolean leavePending,
            int leaveSecondsRemaining,
            boolean localPlayerReady,
            boolean localSpectatorInPlaying,
            boolean joinGamePending,
            int joinGameSecondsRemaining
    ) {
        boolean canSwitchTeam = TdmRoomStateEvaluator.isTeamSwitchAllowed(currentRoomState) || localSpectatorInPlaying;
        boolean canStartVote = TdmRoomStateEvaluator.canStartVote(currentRoomState);
        boolean canEndVote = TdmRoomStateEvaluator.canEndVote(currentRoomState);
        boolean canJoin = !hasPendingAction && ((!hasJoinedRoom && hasSelectedRoom) || (hasJoinedRoom && selectedDifferentRoom));

        if (joinButton != null) {
            joinButton.active = canJoin;
            if (pendingAction == TdmRoomUiState.PendingAction.JOINING) {
                joinButton.setMessage(Component.translatable("screen.codpattern.tdm_room.joining"));
            } else if (hasJoinedRoom && selectedDifferentRoom) {
                joinButton.setMessage(Component.translatable("screen.codpattern.tdm_room.switch_room"));
            } else {
                joinButton.setMessage(Component.translatable("screen.codpattern.tdm_room.join_room"));
            }
        }

        if (leaveButton != null) {
            leaveButton.active = hasJoinedRoom && !hasPendingAction;
            if (pendingAction == TdmRoomUiState.PendingAction.LEAVING) {
                leaveButton.setMessage(Component.translatable("screen.codpattern.tdm_room.leaving"));
            } else if (leavePending) {
                leaveButton.setMessage(Component.translatable(
                        "screen.codpattern.tdm_room.leave_room_pending",
                        leaveSecondsRemaining));
            } else {
                leaveButton.setMessage(Component.translatable("screen.codpattern.tdm_room.leave_room"));
            }
        }

        if (readyButton != null) {
            if (localSpectatorInPlaying) {
                readyButton.active = false;
                readyButton.setMessage(Component.translatable("screen.codpattern.tdm.join_game_disabled"));
            } else {
                readyButton.active = hasJoinedRoom && "WAITING".equals(currentRoomState) && !hasPendingAction;
                readyButton.setMessage(Component.translatable(
                        localPlayerReady ? "screen.codpattern.tdm.ready_cancel" : "screen.codpattern.tdm.ready"));
            }
        }

        if (voteStartButton != null) {
            voteStartButton.active = hasJoinedRoom && canStartVote && !hasPendingAction;
        }

        if (voteEndButton != null) {
            voteEndButton.active = hasJoinedRoom && canEndVote && !hasPendingAction;
        }

        if (kortacButton != null) {
            kortacButton.active = canSwitchTeam && !hasPendingAction;
        }

        if (specgruButton != null) {
            specgruButton.active = canSwitchTeam && !hasPendingAction;
        }
    }
}
