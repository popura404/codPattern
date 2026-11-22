package com.cdp.codpattern.client.gui.screen.match;

import com.cdp.codpattern.app.match.model.ModeDescriptor;
import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

public class ModeSelectButton extends LobbyButton {
    private static final String PREVIEW_ARROW = "›";
    public static final float LABEL_SCALE = 1.5f;

    private final ModeDescriptor descriptor;
    private final int accentColor;
    private boolean previewing;

    public ModeSelectButton(
            int x,
            int y,
            int width,
            int height,
            ModeDescriptor descriptor,
            int accentColor,
            OnPress onPress) {
        this(fromGui(x, y, width, height), descriptor, accentColor, onPress);
    }

    public ModeSelectButton(ScreenRectangle bounds, ModeDescriptor descriptor, int accentColor, OnPress onPress) {
        super(bounds, Component.translatable(descriptor.displayNameKey()), onPress);
        this.descriptor = descriptor;
        this.accentColor = accentColor;
    }

    public ModeDescriptor descriptor() {
        return descriptor;
    }

    public void setPreviewing(boolean previewing) {
        this.previewing = previewing;
    }

    @Override
    protected void drawButton(GuiGraphics graphics, ScreenRectangle bounds) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int x = bounds.left();
        int y = bounds.top();
        int right = bounds.right();
        int bottom = bounds.bottom();
        int padding = 8;
        int arrowX = right - padding - font.width(PREVIEW_ARROW);

        graphics.fill(x, y, right, bottom, previewing ? 0xED263335 : 0xB80B0F12);
        if (previewing) {
            graphics.fill(x, y, x + 2, bottom, accentColor);
            graphics.drawString(font, PREVIEW_ARROW, arrowX, y + (bounds.height() - font.lineHeight) / 2, accentColor, false);
        }
        int textColor = previewing ? CodTheme.TEXT_PRIMARY : 0xFFE6E8E6;
        int maxWidth = (int) Math.floor((arrowX - x - padding - 3) / LABEL_SCALE);
        GuiTextHelper.drawScaledString(graphics, font, GuiTextHelper.ellipsizeFormatted(font, getMessage(), maxWidth),
                x + padding, y + (bounds.height() - font.lineHeight * LABEL_SCALE) / 2f, LABEL_SCALE, textColor, false);
        if (isFocused() && minecraft.getLastInputType().isKeyboard()) {
            graphics.renderOutline(x, y, bounds.width(), bounds.height(), CodTheme.TEXT_PRIMARY);
        }
    }
}
