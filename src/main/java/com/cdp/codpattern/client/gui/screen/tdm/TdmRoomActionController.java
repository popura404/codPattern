package com.cdp.codpattern.client.gui.screen.tdm;

import com.cdp.codpattern.client.gui.screen.match.ModeRoomActionController;
import com.cdp.codpattern.client.gui.screen.match.ModeRoomSessionState;
import com.cdp.codpattern.client.gui.screen.match.ModeRoomUiState;

/**
 * Legacy controller name retained for older callers. New code should use {@link ModeRoomActionController}.
 */
public class TdmRoomActionController extends ModeRoomActionController {
    public TdmRoomActionController(
            ModeRoomSessionState roomState,
            ModeRoomUiState uiState,
            Runnable buttonStateUpdater
    ) {
        super(roomState, uiState, buttonStateUpdater);
    }
}
