package com.cdp.codpattern.client.gui.refit;

import com.cdp.codpattern.network.handler.ClientPacketHandler;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 用于背包设置的按钮
 */
public class BackpackButton {
    public static Button create(int x, int y, int w, int h) {
        return Button.builder(
                Component.translatable("screen.codpattern.backpack.manage_button"),
                button -> ClientPacketHandler.handleOpenBackpackScreen())
                .bounds(x, y, w, h)
                .build();
    }
}
