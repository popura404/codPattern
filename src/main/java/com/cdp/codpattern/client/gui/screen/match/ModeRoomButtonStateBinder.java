package com.cdp.codpattern.client.gui.screen.match;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class ModeRoomButtonStateBinder {
    private ModeRoomButtonStateBinder() {
    }

    public static void refresh(
            Button readyButton,
            Button voteStartButton,
            Button voteEndButton,
            Button kortacButton,
            Button specgruButton,
            boolean hasJoinedRoom,
            boolean hasPendingAction,
            String currentRoomState,
            boolean localPlayerReady,
            boolean hasTeamSelection,
            boolean hasReadyState,
            boolean hasStartVote,
            boolean hasEndVote
    ) {
        boolean canSwitchTeam = ModeRoomStateEvaluator.isTeamSwitchAllowed(currentRoomState);
        boolean canStartVote = ModeRoomStateEvaluator.canStartVote(currentRoomState);
        boolean canEndVote = ModeRoomStateEvaluator.canEndVote(currentRoomState);

        if (readyButton != null) {
            readyButton.active = hasReadyState && hasJoinedRoom && "WAITING".equals(currentRoomState) && !hasPendingAction;
            readyButton.setMessage(Component.translatable(
                    localPlayerReady ? "screen.codpattern.room.ready_cancel" : "screen.codpattern.room.ready"));
        }

        if (voteStartButton != null) {
            voteStartButton.active = hasStartVote && hasJoinedRoom && canStartVote && !hasPendingAction;
        }

        if (voteEndButton != null) {
            voteEndButton.active = hasEndVote && hasJoinedRoom && canEndVote && !hasPendingAction;
        }

        if (kortacButton != null) {
            kortacButton.active = hasTeamSelection && hasJoinedRoom && canSwitchTeam && !hasPendingAction;
        }

        if (specgruButton != null) {
            specgruButton.active = hasTeamSelection && hasJoinedRoom && canSwitchTeam && !hasPendingAction;
        }
    }
}
