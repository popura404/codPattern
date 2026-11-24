package com.cdp.codpattern.client.gui.screen.match;

import com.cdp.codpattern.client.gui.BackNavigation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;

public final class LobbyBackButton extends LobbyButton {
    public LobbyBackButton(int x, int y, int width, int height, OnPress onPress) {
        this(fromGui(x, y, width, height), onPress);
    }

    public LobbyBackButton(ScreenRectangle bounds, OnPress onPress) {
        super(bounds, BackNavigation.LABEL, onPress);
    }

    @Override
    protected void drawButton(GuiGraphics graphics, ScreenRectangle bounds) {
        BackNavigation.draw(graphics, bounds, isHoveredOrFocused());
    }
}
