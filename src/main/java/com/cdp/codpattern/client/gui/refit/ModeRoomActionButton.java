package com.cdp.codpattern.client.gui.refit;

import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class ModeRoomActionButton extends Button {
    private int accentColor;
    private boolean primaryStyle = false;
    private String primaryGlyph = null;

    public ModeRoomActionButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        this(x, y, width, height, message, onPress, CodTheme.HOVER_BORDER);
    }

    public ModeRoomActionButton(int x, int y, int width, int height, Component message, OnPress onPress, int accentColor) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.accentColor = accentColor;
    }

    public void setAccentColor(int accentColor) {
        this.accentColor = accentColor;
    }

    public void setPrimaryStyle(boolean primaryStyle) {
        this.primaryStyle = primaryStyle;
    }

    public void setPrimaryGlyph(String primaryGlyph) {
        this.primaryGlyph = (primaryGlyph == null || primaryGlyph.isBlank()) ? null : primaryGlyph;
    }

    public void setTooltipText(Component tooltipText) {
        this.setTooltip(tooltipText == null ? null : Tooltip.create(tooltipText));
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int right = x + width;
        int bottom = y + height;
        boolean focused = isFocused() && Minecraft.getInstance().getLastInputType().isKeyboard();
        boolean highlighted = active && (isHovered || focused);
        boolean filled = active && primaryStyle;
        int surface = filled ? accentColor : active ? 0xF02D393C : 0xAA242C2E;
        graphics.fill(x, y, right, bottom, surface);
        if (highlighted) {
            graphics.fill(x, y, right, bottom, 0x26FFFFFF);
            graphics.renderOutline(x, y, width, height, 0xDFFFFFFF);
        }
        if (active && !filled) {
            int inset = GuiTextHelper.referenceScaled(4);
            graphics.fill(x, y + inset, x + GuiTextHelper.referenceScaled(2), bottom - inset, accentColor);
        }
        var font = Minecraft.getInstance().font;
        int ink = filled ? 0xFF111B19 : active ? CodTheme.TEXT_PRIMARY : 0xFF8A9495;
        Component label = primaryStyle && primaryGlyph != null ? Component.literal(primaryGlyph) : getMessage();
        GuiTextHelper.drawReferenceButtonLabel(graphics, font, label, x, y, width, height,
                width - GuiTextHelper.referenceScaled(8), ink);
    }
}
