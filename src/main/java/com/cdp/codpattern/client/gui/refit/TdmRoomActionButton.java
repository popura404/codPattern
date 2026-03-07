package com.cdp.codpattern.client.gui.refit;

import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * TDM 房间界面按钮，统一到 COD 主题风格。
 */
public class TdmRoomActionButton extends Button {
    private static final int MAX_HOVER_TICKS = 6;
    private int hoverTicks = 0;
    private final int accentColor;

    public TdmRoomActionButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        this(x, y, width, height, message, onPress, CodTheme.HOVER_BORDER);
    }

    public TdmRoomActionButton(int x, int y, int width, int height, Component message, OnPress onPress, int accentColor) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.accentColor = accentColor;
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.isHoveredOrFocused() && this.active) {
            hoverTicks = Math.min(MAX_HOVER_TICKS, hoverTicks + 1);
        } else {
            hoverTicks = Math.max(0, hoverTicks - 1);
        }

        int x = this.getX();
        int y = this.getY();
        int right = x + this.width;
        int bottom = y + this.height;

        if (this.active) {
            graphics.fillGradient(x, y, right, bottom, CodTheme.CARD_BG_TOP, CodTheme.CARD_BG_BOTTOM);
        } else {
            graphics.fillGradient(x, y, right, bottom, CodTheme.DISABLED_BG, 0xE0242424);
        }

        if (hoverTicks > 0 && this.active) {
            int alphaTop = Math.min(180, 18 * hoverTicks);
            int alphaBottom = Math.min(200, 22 * hoverTicks);
            graphics.fillGradient(x, y, right, bottom,
                    withAlpha(CodTheme.HOVER_BG_TOP, alphaTop),
                    withAlpha(CodTheme.HOVER_BG_BOTTOM, alphaBottom));

            int accentBottom = withAlpha(accentColor, 84 + hoverTicks * 20);
            graphics.fill(x, y, right, y + 1, accentColor);
            graphics.fill(x, bottom - 1, right, bottom, accentBottom);
        }

        // 细边框增强层次，避免低分辨率下按钮边缘糊在背景里。
        graphics.fill(x - 1, y - 1, right + 1, y, CodTheme.BORDER_SUBTLE);
        graphics.fill(x - 1, bottom, right + 1, bottom + 1, CodTheme.BORDER_SUBTLE);
        graphics.fill(x - 1, y, x, bottom, CodTheme.BORDER_SUBTLE);
        graphics.fill(right, y, right + 1, bottom, CodTheme.BORDER_SUBTLE);

        int textColor;
        if (!this.active) {
            textColor = CodTheme.DISABLED_TEXT;
        } else if (hoverTicks > 0) {
            textColor = CodTheme.TEXT_HOVER;
        } else {
            textColor = CodTheme.TEXT_PRIMARY;
        }

        int textX = x + this.width / 2;
        int textY = y + (this.height - GuiTextHelper.referenceLineHeight(Minecraft.getInstance().font)) / 2;
        GuiTextHelper.drawReferenceCenteredEllipsizedString(
                graphics,
                Minecraft.getInstance().font,
                this.getMessage(),
                textX,
                textY,
                this.width - GuiTextHelper.referenceScaled(6),
                textColor,
                false);
    }

    private int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }
}
