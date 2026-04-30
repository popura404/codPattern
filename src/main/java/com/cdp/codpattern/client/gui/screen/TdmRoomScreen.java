package com.cdp.codpattern.client.gui.screen;

import net.minecraft.client.gui.screens.Screen;

/**
 * Legacy screen name retained for older callers. New code should use {@link ModeRoomScreen}.
 */
public class TdmRoomScreen extends ModeRoomScreen {
    public TdmRoomScreen() {
        super();
    }

    public TdmRoomScreen(String modeFilterGameType) {
        super(modeFilterGameType);
    }

    public TdmRoomScreen(String modeFilterGameType, Screen previousScreen) {
        super(modeFilterGameType, previousScreen);
    }
}
