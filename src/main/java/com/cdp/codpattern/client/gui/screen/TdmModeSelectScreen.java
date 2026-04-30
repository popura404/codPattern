package com.cdp.codpattern.client.gui.screen;

import net.minecraft.client.gui.screens.Screen;

/**
 * Legacy screen name retained for older callers. New code should use {@link ModeSelectScreen}.
 */
public class TdmModeSelectScreen extends ModeSelectScreen {
    public TdmModeSelectScreen(Screen previousScreen) {
        super(previousScreen);
    }
}
