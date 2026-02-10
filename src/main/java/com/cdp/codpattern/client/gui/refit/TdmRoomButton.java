package com.cdp.codpattern.client.gui.refit;

import com.cdp.codpattern.client.gui.screen.TdmRoomScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 用于打开 TDM 房间界面的按钮
 */
public class TdmRoomButton {
    public static Button create(int x, int y, int w, int h) {
        return Button.builder(Component.literal("团队竞技"), button -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(new TdmRoomScreen());
        }).bounds(x, y, w, h).build();
    }
}
