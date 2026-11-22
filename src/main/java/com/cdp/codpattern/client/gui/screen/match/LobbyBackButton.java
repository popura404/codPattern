package com.cdp.codpattern.client.gui.screen.match;

import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

public final class LobbyBackButton extends LobbyButton {
    private static final String KEY_LABEL = "ESC";

    public LobbyBackButton(int x, int y, int width, int height, OnPress onPress) {
        this(fromGui(x, y, width, height), onPress);
    }

    public LobbyBackButton(ScreenRectangle bounds, OnPress onPress) {
        super(bounds, Component.translatable("screen.codpattern.common.back"), onPress);
    }

    @Override
    protected void drawButton(GuiGraphics graphics, ScreenRectangle bounds) {
        Font font = Minecraft.getInstance().font;
        int x = bounds.left();
        int lineHeight = font.lineHeight;
        int textY = bounds.top() + (bounds.height() - lineHeight) / 2;
        int padding = 2;
        int keyWidth = font.width(KEY_LABEL) + padding * 2 + 2;
        int keyY = textY - padding;
        int keyHeight = lineHeight + padding * 2;
        int labelX = x + keyWidth + 5;
        int borderColor = isHoveredOrFocused() ? CodTheme.TEXT_PRIMARY : 0xFFC1C3C4;

        graphics.fill(x, keyY, x + keyWidth, keyY + keyHeight, 0xC025292B);
        graphics.renderOutline(x, keyY, keyWidth, keyHeight, borderColor);
        graphics.drawString(font, KEY_LABEL, x + padding + 1, textY, CodTheme.TEXT_PRIMARY, false);
        graphics.drawString(font, GuiTextHelper.ellipsizeFormatted(font, getMessage(), bounds.right() - labelX),
                labelX, textY, borderColor, true);
    }
}
