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
            boolean hasJoinedRoom,
            boolean hasPendingAction,
            String currentRoomState,
            TdmRoomUiState.PendingAction pendingAction,
            boolean leavePending,
            int leaveSecondsRemaining,
            boolean localPlayerReady
    ) {
        boolean canSwitchTeam = TdmRoomStateEvaluator.isTeamSwitchAllowed(currentRoomState);
        boolean canStartVote = TdmRoomStateEvaluator.canStartVote(currentRoomState);
        boolean canEndVote = TdmRoomStateEvaluator.canEndVote(currentRoomState);

        if (joinButton != null) {
            joinButton.active = hasSelectedRoom && !hasJoinedRoom && !hasPendingAction;
            joinButton.setMessage(pendingAction == TdmRoomUiState.PendingAction.JOINING
                    ? Component.literal("加入中...")
                    : Component.translatable("screen.codpattern.tdm_room.join_room"));
        }

        if (leaveButton != null) {
            leaveButton.active = hasJoinedRoom && !hasPendingAction;
            if (pendingAction == TdmRoomUiState.PendingAction.LEAVING) {
                leaveButton.setMessage(Component.literal("离开中..."));
            } else if (leavePending) {
                leaveButton.setMessage(Component.translatable(
                        "screen.codpattern.tdm_room.leave_room_pending",
                        leaveSecondsRemaining));
            } else {
                leaveButton.setMessage(Component.translatable("screen.codpattern.tdm_room.leave_room"));
            }
        }

        if (readyButton != null) {
            readyButton.active = hasJoinedRoom && "WAITING".equals(currentRoomState) && !hasPendingAction;
            readyButton.setMessage(Component.literal(localPlayerReady ? "取消准备" : "准备"));
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
