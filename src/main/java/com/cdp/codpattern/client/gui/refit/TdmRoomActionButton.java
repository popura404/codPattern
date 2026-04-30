package com.cdp.codpattern.client.gui.refit;

import net.minecraft.network.chat.Component;

/**
 * Legacy button name retained for older callers. New code should use {@link ModeRoomActionButton}.
 */
@Deprecated(forRemoval = false)
public class TdmRoomActionButton extends ModeRoomActionButton {
    public TdmRoomActionButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress);
    }

    public TdmRoomActionButton(int x, int y, int width, int height, Component message, OnPress onPress, int accentColor) {
        super(x, y, width, height, message, onPress, accentColor);
    }
}
