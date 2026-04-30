package com.cdp.codpattern.client.gui.refit;

import net.minecraft.client.gui.components.Button;

/**
 * Legacy entry point for older callers. New code should use {@link ModeRoomButton}.
 */
public class TdmRoomButton {
    public static Button create(int x, int y, int w, int h) {
        return ModeRoomButton.create(x, y, w, h);
    }
}
