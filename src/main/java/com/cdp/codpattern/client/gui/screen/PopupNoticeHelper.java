package com.cdp.codpattern.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class PopupNoticeHelper {
    private PopupNoticeHelper() {
    }

    public static void show(Component message) {
        show(Component.translatable("screen.codpattern.popup.title"), message);
    }

    public static void show(Component title, Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.execute(() -> {
            Screen current = minecraft.screen;
            Screen parent = resolveParent(current);
            minecraft.setScreen(new NoticePopupScreen(parent, title, message));
        });
    }

    private static Screen resolveParent(Screen screen) {
        Screen current = screen;
        while (current instanceof NoticePopupScreen popupScreen) {
            current = popupScreen.parentScreen();
        }
        return current;
    }
}
