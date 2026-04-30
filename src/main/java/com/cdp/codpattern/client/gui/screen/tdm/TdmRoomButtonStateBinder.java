package com.cdp.codpattern.client.gui.screen.tdm;

import com.cdp.codpattern.client.gui.screen.match.ModeRoomButtonStateBinder;
import net.minecraft.client.gui.components.Button;

/**
 * Legacy helper name retained for older callers. New code should use {@link ModeRoomButtonStateBinder}.
 */
@Deprecated(forRemoval = false)
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
            boolean localPlayerReady,
            boolean hasTeamSelection,
            boolean hasReadyState,
            boolean hasStartVote,
            boolean hasEndVote
    ) {
        ModeRoomButtonStateBinder.refresh(
                readyButton,
                voteStartButton,
                voteEndButton,
                kortacButton,
                specgruButton,
                hasJoinedRoom,
                hasPendingAction,
                currentRoomState,
                localPlayerReady,
                hasTeamSelection,
                hasReadyState,
                hasStartVote,
                hasEndVote);
    }
}
