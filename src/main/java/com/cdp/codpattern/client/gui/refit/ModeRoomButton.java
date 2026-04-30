package com.cdp.codpattern.client.gui.refit;

import com.cdp.codpattern.client.gui.screen.ModeSelectScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class ModeRoomButton {
    private ModeRoomButton() {
    }

    public static Button create(int x, int y, int w, int h) {
        return Button.builder(Component.translatable("screen.codpattern.room.button"), button -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(new ModeSelectScreen(minecraft.screen));
        }).bounds(x, y, w, h).build();
    }
}
