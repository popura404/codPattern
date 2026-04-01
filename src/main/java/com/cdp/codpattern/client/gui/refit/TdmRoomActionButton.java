package com.cdp.codpattern.client.gui.refit;

import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * TDM 房间界面按钮，统一到 COD 主题风格。
 */
public class TdmRoomActionButton extends Button {
    private int accentColor;
    private boolean primaryStyle = false;
    private String primaryGlyph = null;

    public TdmRoomActionButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        this(x, y, width, height, message, onPress, CodTheme.HOVER_BORDER);
    }

    public TdmRoomActionButton(int x, int y, int width, int height, Component message, OnPress onPress, int accentColor) {
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
        boolean hovered = this.isHoveredOrFocused() && this.active;
        int x = this.getX();
        int y = this.getY();
        int right = x + this.width;
        int bottom = y + this.height;
        int innerLeft = x + 1;
        int innerTop = y + 1;
        int innerRight = right - 1;
        int innerBottom = bottom - 1;
        int accentWidth = GuiTextHelper.referenceScaled(primaryStyle ? 3 : 2);

        if (this.active) {
            graphics.fillGradient(x, y, right, bottom, CodTheme.CARD_BG_TOP, CodTheme.CARD_BG_BOTTOM);
            graphics.fillGradient(
                    x,
                    y,
                    right,
                    bottom,
                    withAlpha(accentColor, hovered ? 34 : 22),
                    withAlpha(accentColor, hovered ? 76 : 52));
        } else {
            graphics.fillGradient(x, y, right, bottom, CodTheme.DISABLED_BG, 0xE0242424);
        }

        graphics.fill(x, y, x + accentWidth, bottom,
                this.active ? withAlpha(accentColor, 220) : withAlpha(CodTheme.BORDER_SUBTLE, 180));
        if (this.active && primaryStyle) {
            graphics.fillGradient(
                    x + accentWidth,
                    y + 1,
                    right - 1,
                    y + Math.max(2, this.height / 2),
                    withAlpha(accentColor, hovered ? 68 : 48),
                    withAlpha(accentColor, 8));
            graphics.fill(x + accentWidth, y + 1, right - 1, y + 2, withAlpha(accentColor, hovered ? 168 : 132));
            renderPrimaryCorners(graphics, x, y, right, bottom, hovered);
        } else {
            renderSubtleCorners(graphics, x, y, right, bottom);
        }

        if (hovered) {
            graphics.fillGradient(x, y, right, bottom,
                    withAlpha(CodTheme.HOVER_BG_TOP, 108),
                    withAlpha(CodTheme.HOVER_BG_BOTTOM, 126));

            int accentBottom = withAlpha(accentColor, 176);
            graphics.fill(x, y, right, y + 1, accentColor);
            graphics.fill(x, bottom - 1, right, bottom, accentBottom);
        }

        renderInnerFrame(graphics, innerLeft, innerTop, innerRight, innerBottom);

        // 细边框增强层次，避免低分辨率下按钮边缘糊在背景里。
        graphics.fill(x - 1, y - 1, right + 1, y, CodTheme.BORDER_SUBTLE);
        graphics.fill(x - 1, bottom, right + 1, bottom + 1, CodTheme.BORDER_SUBTLE);
        graphics.fill(x - 1, y, x, bottom, CodTheme.BORDER_SUBTLE);
        graphics.fill(right, y, right + 1, bottom, CodTheme.BORDER_SUBTLE);
        graphics.fill(x, bottom - 1, right, bottom, this.active ? withAlpha(accentColor, 190) : CodTheme.BORDER_SUBTLE);
        if (this.active && primaryStyle) {
            graphics.fill(right - 1, y + 1, right, bottom - 1, withAlpha(accentColor, hovered ? 148 : 112));
        }

        int textColor;
        if (!this.active) {
            textColor = CodTheme.DISABLED_TEXT;
        } else if (hovered) {
            textColor = CodTheme.TEXT_HOVER;
        } else {
            textColor = CodTheme.TEXT_PRIMARY;
        }

        int textX = x + this.width / 2;
        int textY = y + (this.height - GuiTextHelper.referenceLineHeight(Minecraft.getInstance().font)) / 2;
        GuiTextHelper.drawReferenceCenteredEllipsizedString(
                graphics,
                Minecraft.getInstance().font,
                displayText(),
                textX,
                textY,
                this.width - GuiTextHelper.referenceScaled(6),
                textColor,
                false);
    }

    private String displayText() {
        if (primaryStyle && primaryGlyph != null) {
            return primaryGlyph;
        }
        return this.getMessage().getString();
    }

    private void renderInnerFrame(GuiGraphics graphics, int left, int top, int right, int bottom) {
        if (right - left < 4 || bottom - top < 4) {
            return;
        }
        graphics.fill(left, top, right, top + 1, withAlpha(0xFFFFFFFF, primaryStyle ? 42 : 28));
        graphics.fill(left, bottom - 1, right, bottom, withAlpha(accentColor, primaryStyle ? 96 : 64));
        graphics.fill(left, top, left + 1, bottom, withAlpha(0xFFFFFFFF, primaryStyle ? 18 : 12));
        graphics.fill(right - 1, top, right, bottom, withAlpha(accentColor, primaryStyle ? 48 : 28));
    }

    private void renderPrimaryCorners(GuiGraphics graphics, int x, int y, int right, int bottom, boolean hovered) {
        int notch = GuiTextHelper.referenceScaled(5);
        graphics.fill(x, y, x + notch, y + 1, withAlpha(accentColor, 220));
        graphics.fill(x, y, x + 1, y + notch, withAlpha(accentColor, 220));
        graphics.fill(right - notch, y, right, y + 1, withAlpha(0xFFFFFFFF, hovered ? 140 : 112));
        graphics.fill(right - 1, y, right, y + notch, withAlpha(0xFFFFFFFF, hovered ? 140 : 112));
        graphics.fill(x, bottom - 1, x + notch, bottom, withAlpha(0xFFFFFFFF, 72));
        graphics.fill(right - notch, bottom - 1, right, bottom, withAlpha(accentColor, 210));
        graphics.fill(right - 1, bottom - notch, right, bottom, withAlpha(accentColor, 210));
    }

    private void renderSubtleCorners(GuiGraphics graphics, int x, int y, int right, int bottom) {
        int notch = GuiTextHelper.referenceScaled(3);
        graphics.fill(x, y, x + notch, y + 1, withAlpha(0xFFFFFFFF, 38));
        graphics.fill(right - notch, bottom - 1, right, bottom, withAlpha(accentColor, 90));
    }

    private int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }
}
