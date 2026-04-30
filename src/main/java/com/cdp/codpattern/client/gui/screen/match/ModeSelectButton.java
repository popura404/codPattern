package com.cdp.codpattern.client.gui.screen.match;

import com.cdp.codpattern.app.match.model.ModeDescriptor;
import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class ModeSelectButton extends Button {
    private static final float TITLE_SCALE = 1.22f;
    private static final long PULSE_MS = 1600L;

    private final ModeDescriptor descriptor;
    private final int accentColor;
    private final long createdAtMs = System.currentTimeMillis();
    private boolean previewing;

    public ModeSelectButton(
            int x,
            int y,
            int width,
            int height,
            ModeDescriptor descriptor,
            int accentColor,
            OnPress onPress) {
        super(x, y, width, height, Component.translatable(descriptor.displayNameKey()), onPress, DEFAULT_NARRATION);
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
    public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = this.isHoveredOrFocused() && this.active;
        float pulse = pulseStrength();
        int x = this.getX();
        int y = this.getY();
        int right = x + this.width;
        int bottom = y + this.height;
        int innerLeft = x + 1;
        int innerTop = y + 1;
        int innerRight = right - 1;
        int innerBottom = bottom - 1;
        int accentWidth = GuiTextHelper.referenceScaled(previewing ? 4 : 3);

        graphics.fill(x + 2, y + 2, right + 2, bottom + 2, 0x30000000);
        graphics.fillGradient(x, y, right, bottom, CodTheme.CARD_BG_TOP, CodTheme.CARD_BG_BOTTOM);
        graphics.fillGradient(
                x,
                y,
                right,
                bottom,
                withAlpha(accentColor, hovered || previewing ? 42 : 26),
                withAlpha(accentColor, hovered || previewing ? 88 : 52));

        if (hovered) {
            graphics.fillGradient(
                    x,
                    y,
                    right,
                    bottom,
                    withAlpha(CodTheme.HOVER_BG_TOP, 104),
                    withAlpha(CodTheme.HOVER_BG_BOTTOM, 128));
        }
        if (previewing) {
            graphics.fillGradient(
                    x,
                    y,
                    right,
                    bottom,
                    withAlpha(accentColor, 26 + (int) (28.0f * pulse)),
                    withAlpha(accentColor, 10 + (int) (34.0f * pulse)));
        }

        graphics.fill(x, y, x + accentWidth, bottom,
                withAlpha(accentColor, previewing ? 255 : 228));
        graphics.fill(x, y, right, y + 1, withAlpha(0xFFFFFFFF, hovered ? 110 : 54));
        graphics.fill(x, bottom - 1, right, bottom, withAlpha(accentColor, previewing ? 205 : 132));
        graphics.fill(x, y, x + 1, bottom, withAlpha(0xFFFFFFFF, 24));
        graphics.fill(right - 1, y, right, bottom, withAlpha(accentColor, 68));

        if (previewing) {
            graphics.fill(right - GuiTextHelper.referenceScaled(14), y, right, y + 1, withAlpha(accentColor, 255));
            graphics.fill(right - 1, y, right, y + GuiTextHelper.referenceScaled(8), withAlpha(accentColor, 255));
        }

        graphics.fill(innerLeft, innerTop, innerRight, innerTop + 1, withAlpha(0xFFFFFFFF, 14));
        graphics.fill(innerLeft, innerBottom - 1, innerRight, innerBottom, withAlpha(accentColor, 56));

        Minecraft mc = Minecraft.getInstance();
        int padding = GuiTextHelper.referenceScaled(8);
        String modeCode = descriptor.gameType().toUpperCase(Locale.ROOT);
        int codeY = y + GuiTextHelper.referenceScaled(4);
        GuiTextHelper.drawReferenceEllipsizedString(
                graphics,
                mc.font,
                modeCode,
                x + padding,
                codeY,
                this.width - padding * 2,
                hovered || previewing ? withAlpha(accentColor, 235) : CodTheme.TEXT_SECONDARY,
                false);

        int titleY = y + GuiTextHelper.referenceScaled(18);
        GuiTextHelper.drawReferenceScaledEllipsizedString(
                graphics,
                mc.font,
                Component.translatable(descriptor.displayNameKey()),
                x + padding,
                titleY,
                this.width - padding * 2,
                TITLE_SCALE,
                hovered || previewing ? CodTheme.TEXT_PRIMARY : 0xFFE7E7E7,
                false);

        int footerY = bottom - GuiTextHelper.referenceLineHeight(mc.font) - GuiTextHelper.referenceScaled(4);
        GuiTextHelper.drawReferenceRightAlignedEllipsizedString(
                graphics,
                mc.font,
                Component.translatable("screen.codpattern.mode_select.open").getString(),
                right - padding,
                footerY,
                Math.max(GuiTextHelper.referenceScaled(40), this.width - padding * 2),
                previewing ? withAlpha(accentColor, 255) : withAlpha(accentColor, 220),
                false);
    }

    private float pulseStrength() {
        long elapsed = System.currentTimeMillis() - createdAtMs;
        float normalized = (elapsed % PULSE_MS) / (float) PULSE_MS;
        return 0.5f + (0.5f * (float) Math.sin(normalized * Math.PI * 2.0d));
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }
}
