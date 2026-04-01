package com.cdp.codpattern.client.gui.screen.tdm;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class TdmRoomButtonStateBinder {
    private TdmRoomButtonStateBinder() {
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
            boolean localPlayerReady
    ) {
        boolean canSwitchTeam = TdmRoomStateEvaluator.isTeamSwitchAllowed(currentRoomState);
        boolean canStartVote = TdmRoomStateEvaluator.canStartVote(currentRoomState);
        boolean canEndVote = TdmRoomStateEvaluator.canEndVote(currentRoomState);

        if (readyButton != null) {
            readyButton.active = hasJoinedRoom && "WAITING".equals(currentRoomState) && !hasPendingAction;
            readyButton.setMessage(Component.translatable(
                    localPlayerReady ? "screen.codpattern.tdm.ready_cancel" : "screen.codpattern.tdm.ready"));
        }

        if (voteStartButton != null) {
            voteStartButton.active = hasJoinedRoom && canStartVote && !hasPendingAction;
        }

        if (voteEndButton != null) {
            voteEndButton.active = hasJoinedRoom && canEndVote && !hasPendingAction;
        }

        if (kortacButton != null) {
            kortacButton.active = hasJoinedRoom && canSwitchTeam && !hasPendingAction;
        }

        if (specgruButton != null) {
            specgruButton.active = hasJoinedRoom && canSwitchTeam && !hasPendingAction;
        }
    }
}
