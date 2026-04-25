package com.cdp.codpattern.client.gui.screen.tdm;

import com.cdp.codpattern.CodPattern;
import com.cdp.codpattern.app.match.model.ModeDescriptor;
import com.cdp.codpattern.app.tdm.model.TdmGameTypes;
import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

public final class TdmModePreviewPanel {
    private static final ResourceLocation SHARED_PREVIEW = ResourceLocation.fromNamespaceAndPath(
            CodPattern.MODID,
            "textures/gui/modes/shared_preview.png");
    private static final int PREVIEW_TEXTURE_WIDTH = 3840;
    private static final int PREVIEW_TEXTURE_HEIGHT = 2160;

    private TdmModePreviewPanel() {
    }

    public static void render(
            GuiGraphics graphics,
            Minecraft mc,
            int x,
            int y,
            int width,
            int height,
            ModeDescriptor descriptor,
            int accentColor,
            float alphaFactor) {
        int frameLeft = x - GuiTextHelper.referenceScaled(3);
        int frameTop = y - GuiTextHelper.referenceScaled(3);
        int frameRight = x + width + GuiTextHelper.referenceScaled(3);
        int frameBottom = y + height + GuiTextHelper.referenceScaled(3);
        int innerInset = GuiTextHelper.referenceScaled(6);
        int innerLeft = x + innerInset;
        int innerTop = y + innerInset;
        int innerRight = x + width - innerInset;
        int innerBottom = y + height - innerInset;
        int innerWidth = Math.max(1, innerRight - innerLeft);
        int innerHeight = Math.max(1, innerBottom - innerTop);

        graphics.fillGradient(
                frameLeft,
                frameTop,
                frameRight,
                frameBottom,
                scaleAlpha(CodTheme.PANEL_BG, alphaFactor),
                scaleAlpha(0xD0101010, alphaFactor));
        graphics.fill(frameLeft, frameTop, frameRight, frameTop + 1, scaleAlpha(CodTheme.BORDER_SUBTLE, alphaFactor));
        graphics.fill(frameLeft, frameBottom - 1, frameRight, frameBottom, scaleAlpha(CodTheme.BORDER_SUBTLE, alphaFactor));
        graphics.fill(frameLeft, frameTop, frameLeft + 1, frameBottom, scaleAlpha(CodTheme.BORDER_SUBTLE, alphaFactor));
        graphics.fill(frameRight - 1, frameTop, frameRight, frameBottom, scaleAlpha(CodTheme.BORDER_SUBTLE, alphaFactor));

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, Math.max(0.0f, Math.min(1.0f, alphaFactor)));
        graphics.blit(
                SHARED_PREVIEW,
                innerLeft,
                innerTop,
                0,
                0,
                innerWidth,
                innerHeight,
                PREVIEW_TEXTURE_WIDTH,
                PREVIEW_TEXTURE_HEIGHT);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        graphics.fillGradient(
                innerLeft,
                innerTop,
                innerRight,
                innerBottom,
                scaleAlpha(withAlpha(accentColor, 54), alphaFactor),
                scaleAlpha(0xA0101010, alphaFactor));
        graphics.fill(innerLeft, innerTop, innerLeft + GuiTextHelper.referenceScaled(4), innerBottom,
                scaleAlpha(withAlpha(accentColor, 220), alphaFactor));
        graphics.fill(innerLeft, innerTop, innerRight, innerTop + 1, scaleAlpha(withAlpha(0xFFFFFFFF, 120), alphaFactor));
        graphics.fill(innerLeft, innerBottom - 1, innerRight, innerBottom, scaleAlpha(withAlpha(accentColor, 160), alphaFactor));

        renderVariantOverlay(graphics, innerLeft, innerTop, innerRight, innerBottom, descriptor.gameType(), accentColor, alphaFactor);
        renderText(graphics, mc, innerLeft, innerTop, innerWidth, innerHeight, descriptor, accentColor, alphaFactor);
    }

    private static void renderVariantOverlay(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            int bottom,
            String gameType,
            int accentColor,
            float alphaFactor) {
        int variant = overlayVariant(gameType);
        int inset = GuiTextHelper.referenceScaled(10);

        if (variant == 0) {
            int spacing = GuiTextHelper.referenceScaled(9);
            for (int y = top + inset; y < bottom - inset; y += spacing) {
                graphics.fill(left + inset, y, right - inset, y + 1, scaleAlpha(withAlpha(accentColor, 36), alphaFactor));
            }
            return;
        }

        if (variant == 1) {
            int barWidth = GuiTextHelper.referenceScaled(22);
            int barGap = GuiTextHelper.referenceScaled(14);
            int currentX = right - inset - barWidth;
            while (currentX > left + inset) {
                graphics.fillGradient(
                        currentX,
                        top + inset,
                        currentX + barWidth,
                        bottom - inset,
                        scaleAlpha(withAlpha(accentColor, 54), alphaFactor),
                        scaleAlpha(withAlpha(0xFF060A0E, 10), alphaFactor));
                currentX -= barWidth + barGap;
            }
            return;
        }

        int stripeWidth = GuiTextHelper.referenceScaled(42);
        int stripeHeight = GuiTextHelper.referenceScaled(8);
        int startY = top + inset;
        while (startY < bottom - inset) {
            graphics.fillGradient(
                    left + inset,
                    startY,
                    left + inset + stripeWidth,
                    startY + stripeHeight,
                    scaleAlpha(withAlpha(accentColor, 68), alphaFactor),
                    scaleAlpha(withAlpha(0xFF000000, 0), alphaFactor));
            startY += stripeHeight + GuiTextHelper.referenceScaled(10);
        }
    }

    private static void renderText(
            GuiGraphics graphics,
            Minecraft mc,
            int left,
            int top,
            int width,
            int height,
            ModeDescriptor descriptor,
            int accentColor,
            float alphaFactor) {
        int contentX = left + GuiTextHelper.referenceScaled(18);
        int contentRight = left + width - GuiTextHelper.referenceScaled(16);
        int modeCodeY = top + GuiTextHelper.referenceScaled(14);
        int titleY = top + GuiTextHelper.referenceScaled(34);
        int bodyY = titleY + GuiTextHelper.referenceLineHeight(mc.font, 1.6f) + GuiTextHelper.referenceScaled(10);
        int footerY = top + height - GuiTextHelper.referenceLineHeight(mc.font) * 2 - GuiTextHelper.referenceScaled(18);
        int textWidth = Math.max(GuiTextHelper.referenceScaled(60), contentRight - contentX);

        GuiTextHelper.drawReferenceEllipsizedString(
                graphics,
                mc.font,
                descriptor.gameType().toUpperCase(Locale.ROOT),
                contentX,
                modeCodeY,
                textWidth,
                scaleAlpha(withAlpha(accentColor, 225), alphaFactor),
                false);
        GuiTextHelper.drawReferenceScaledEllipsizedString(
                graphics,
                mc.font,
                Component.translatable(descriptor.displayNameKey()),
                contentX,
                titleY,
                textWidth,
                1.6f,
                scaleAlpha(CodTheme.TEXT_PRIMARY, alphaFactor),
                true);
        GuiTextHelper.drawReferenceEllipsizedString(
                graphics,
                mc.font,
                resolveModeDescription(descriptor.gameType()),
                contentX,
                bodyY,
                textWidth,
                scaleAlpha(0xFFE8E8E8, alphaFactor),
                false);
        GuiTextHelper.drawReferenceEllipsizedString(
                graphics,
                mc.font,
                Component.translatable("screen.codpattern.mode_select.preview_hint"),
                contentX,
                footerY,
                textWidth,
                scaleAlpha(CodTheme.TEXT_SECONDARY, alphaFactor),
                false);
        GuiTextHelper.drawReferenceEllipsizedString(
                graphics,
                mc.font,
                Component.translatable("screen.codpattern.mode_select.open"),
                contentX,
                footerY + GuiTextHelper.referenceLineHeight(mc.font) + GuiTextHelper.referenceScaled(4),
                textWidth,
                scaleAlpha(withAlpha(accentColor, 235), alphaFactor),
                false);
    }

    private static Component resolveModeDescription(String gameType) {
        String canonical = TdmGameTypes.canonicalize(gameType);
        if (TdmGameTypes.FRONTLINE.equals(canonical)) {
            return Component.translatable("screen.codpattern.mode_select.hover_frontline");
        }
        if (TdmGameTypes.TEAM_DEATHMATCH.equals(canonical)) {
            return Component.translatable("screen.codpattern.mode_select.hover_teamdeathmatch");
        }
        return Component.translatable("screen.codpattern.mode_select.preview_hint");
    }

    private static int overlayVariant(String gameType) {
        String canonical = TdmGameTypes.canonicalize(gameType);
        if (TdmGameTypes.FRONTLINE.equals(canonical)) {
            return 0;
        }
        if (TdmGameTypes.TEAM_DEATHMATCH.equals(canonical)) {
            return 1;
        }
        return Math.abs(canonical.hashCode()) % 3;
    }

    private static int scaleAlpha(int color, float factor) {
        int alpha = (color >>> 24) & 0xFF;
        int scaledAlpha = Math.max(0, Math.min(255, (int) (alpha * Math.max(0.0f, Math.min(1.0f, factor)))));
        return (scaledAlpha << 24) | (color & 0x00FFFFFF);
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }
}
