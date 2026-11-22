package com.cdp.codpattern.client.gui.screen.match;

import com.cdp.codpattern.client.gui.GuiTextHelper;
import net.minecraft.client.gui.GuiGraphics;

/** The lobby lays out once in 2× units. Only drawing and native input cross this boundary. */
public final class LobbyCanvas {
    private LobbyCanvas() {}

    public static void begin(GuiGraphics graphics) {
        float scale = GuiTextHelper.referenceScale();
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0f);
    }

    public static int toGui(int referenceCoordinate) {
        return Math.round(referenceCoordinate * GuiTextHelper.referenceScale());
    }

    public static double toReference(double guiCoordinate) {
        return guiCoordinate / GuiTextHelper.referenceScaleFactor();
    }
}
