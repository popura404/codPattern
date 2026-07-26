package com.cdp.codpattern.client.gui.screen.match;

import com.cdp.codpattern.app.match.GameModeBootstrap;
import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.model.ClientModePresentation;
import com.cdp.codpattern.app.match.model.ClientModePresentationRegistry;
import com.cdp.codpattern.app.match.model.ModeDescriptor;
import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class ModePreviewPanel {
    private static final PreviewTexture SHARED_PREVIEW = new PreviewTexture(
            new ResourceLocation("codpattern", "textures/gui/modes/shared_preview.png"),
            3840,
            2160);
    private static final float MODE_OVERLAY_ALPHA_FACTOR = 0.40f;
    private static final int[] FALLBACK_ACCENTS = {
            0xFF88D6FF,
            0xFFFFB347,
            0xFF78F2BE,
            0xFFE685FF
    };

    private record PreviewTexture(ResourceLocation location, int width, int height) {
    }

    private ModePreviewPanel() {
    }

    public static void renderFullscreenBase(GuiGraphics graphics, int screenWidth, int screenHeight, float alphaFactor) {
        if (screenWidth <= 0 || screenHeight <= 0 || alphaFactor <= 0.0f) {
            return;
        }

        float clampedAlpha = Math.max(0.0f, Math.min(1.0f, alphaFactor));
        renderFullscreenTexture(graphics, SHARED_PREVIEW, screenWidth, screenHeight, clampedAlpha);

        graphics.fillGradient(
                0,
                0,
                screenWidth,
                screenHeight,
                scaleAlpha(0x34040608, clampedAlpha),
                scaleAlpha(0x9C090A0C, clampedAlpha));
    }

    public static void renderFullscreenModeLayer(
            GuiGraphics graphics,
            int screenWidth,
            int screenHeight,
            ModeDescriptor descriptor,
            float alphaFactor) {
        if (descriptor == null || alphaFactor <= 0.0f) {
            return;
        }

        int accentColor = accentColor(descriptor.gameType());
        float clampedAlpha = Math.max(0.0f, Math.min(1.0f, alphaFactor));
        float overlayAlpha = clampedAlpha * MODE_OVERLAY_ALPHA_FACTOR;
        int centerX = screenWidth / 2;
        int leftInset = GuiTextHelper.referenceScaled(18);
        int topInset = GuiTextHelper.referenceScaled(18);
        int rightInset = screenWidth - leftInset;
        int bottomInset = screenHeight - GuiTextHelper.referenceScaled(18);

        renderFullscreenTexture(graphics, resolvePreviewTexture(descriptor.gameType()), screenWidth, screenHeight,
                clampedAlpha);
        graphics.fillGradient(
                0,
                0,
                screenWidth,
                screenHeight,
                scaleAlpha(withAlpha(accentColor, 24), overlayAlpha),
                scaleAlpha(0x76090D10, overlayAlpha));
        graphics.fillGradient(
                0,
                0,
                centerX,
                screenHeight,
                scaleAlpha(withAlpha(accentColor, 22), overlayAlpha),
                0x00000000);
        graphics.fillGradient(
                centerX,
                0,
                screenWidth,
                screenHeight,
                0x00000000,
                scaleAlpha(withAlpha(0xFF050608, 98), overlayAlpha));

        renderVariantOverlay(graphics, descriptor.gameType(), accentColor, screenWidth, screenHeight, overlayAlpha);

        graphics.fill(0, 0, screenWidth, 1, scaleAlpha(withAlpha(0xFFFFFFFF, 86), overlayAlpha));
        graphics.fill(0, screenHeight - 1, screenWidth, screenHeight, scaleAlpha(withAlpha(accentColor, 148), overlayAlpha));
        graphics.fill(0, 0, GuiTextHelper.referenceScaled(2), screenHeight, scaleAlpha(withAlpha(accentColor, 90), overlayAlpha));
        graphics.fill(screenWidth - 1, topInset, screenWidth, bottomInset, scaleAlpha(withAlpha(accentColor, 60), overlayAlpha));
        graphics.fill(leftInset, topInset, rightInset, topInset + 1, scaleAlpha(CodTheme.BORDER_SUBTLE, overlayAlpha));
    }

    public static int accentColor(String gameType) {
        return resolvePresentation(gameType).accentColor();
    }

    public static Component resolveModeDescription(String gameType) {
        return Component.translatable(resolvePresentation(gameType).descriptionKey());
    }

    private static void renderVariantOverlay(
            GuiGraphics graphics,
            String gameType,
            int accentColor,
            int screenWidth,
            int screenHeight,
            float alphaFactor) {
        String overlayStyle = resolvePresentation(gameType).overlayStyle();
        if ("frontline".equals(overlayStyle)) {
            renderFrontlineOverlay(graphics, accentColor, screenWidth, screenHeight, alphaFactor);
            return;
        }
        if ("teamdeathmatch".equals(overlayStyle)) {
            renderTeamDeathmatchOverlay(graphics, accentColor, screenWidth, screenHeight, alphaFactor);
            return;
        }
        renderFallbackOverlay(graphics, accentColor, screenWidth, screenHeight, alphaFactor);
    }

    private static void renderFrontlineOverlay(
            GuiGraphics graphics,
            int accentColor,
            int screenWidth,
            int screenHeight,
            float alphaFactor) {
        int lineSpacing = Math.max(6, GuiTextHelper.referenceScaled(10));
        int left = GuiTextHelper.referenceScaled(18);
        int right = screenWidth - GuiTextHelper.referenceScaled(36);
        int startY = screenHeight / 3;
        for (int y = startY; y < screenHeight - GuiTextHelper.referenceScaled(54); y += lineSpacing) {
            int endX = right - ((y - startY) / lineSpacing % 5) * GuiTextHelper.referenceScaled(22);
            graphics.fill(
                    left,
                    y,
                    endX,
                    y + 1,
                    scaleAlpha(withAlpha(accentColor, 32), alphaFactor));
        }

        int wedgeWidth = Math.max(GuiTextHelper.referenceScaled(72), screenWidth / 5);
        graphics.fillGradient(
                left,
                GuiTextHelper.referenceScaled(34),
                left + wedgeWidth,
                screenHeight - GuiTextHelper.referenceScaled(72),
                scaleAlpha(withAlpha(accentColor, 56), alphaFactor),
                0x00000000);
    }

    private static void renderTeamDeathmatchOverlay(
            GuiGraphics graphics,
            int accentColor,
            int screenWidth,
            int screenHeight,
            float alphaFactor) {
        int stripeWidth = Math.max(10, GuiTextHelper.referenceScaled(18));
        int stripeGap = Math.max(6, GuiTextHelper.referenceScaled(12));
        int right = screenWidth - GuiTextHelper.referenceScaled(26);
        int top = GuiTextHelper.referenceScaled(36);
        int bottom = screenHeight - GuiTextHelper.referenceScaled(54);
        int startX = screenWidth / 2 + GuiTextHelper.referenceScaled(26);

        for (int x = startX; x < right; x += stripeWidth + stripeGap) {
            graphics.fillGradient(
                    x,
                    top,
                    x + stripeWidth,
                    bottom,
                    scaleAlpha(withAlpha(accentColor, 54), alphaFactor),
                    scaleAlpha(withAlpha(0xFF050608, 4), alphaFactor));
        }

        int gridSpacing = Math.max(14, GuiTextHelper.referenceScaled(22));
        int gridLeft = startX;
        int gridRight = right;
        int gridTop = screenHeight / 4;
        int gridBottom = bottom - GuiTextHelper.referenceScaled(12);
        for (int x = gridLeft; x < gridRight; x += gridSpacing) {
            graphics.fill(x, gridTop, x + 1, gridBottom, scaleAlpha(withAlpha(accentColor, 24), alphaFactor));
        }
        for (int y = gridTop; y < gridBottom; y += gridSpacing) {
            graphics.fill(gridLeft, y, gridRight, y + 1, scaleAlpha(withAlpha(accentColor, 16), alphaFactor));
        }
    }

    private static void renderFallbackOverlay(
            GuiGraphics graphics,
            int accentColor,
            int screenWidth,
            int screenHeight,
            float alphaFactor) {
        int stripeHeight = Math.max(6, GuiTextHelper.referenceScaled(8));
        int gap = Math.max(8, GuiTextHelper.referenceScaled(12));
        int left = GuiTextHelper.referenceScaled(24);
        int maxWidth = Math.max(GuiTextHelper.referenceScaled(92), screenWidth / 4);
        int y = GuiTextHelper.referenceScaled(42);
        int step = 0;
        while (y < screenHeight - GuiTextHelper.referenceScaled(68)) {
            int stripeWidth = Math.max(GuiTextHelper.referenceScaled(36), maxWidth - step * GuiTextHelper.referenceScaled(16));
            graphics.fillGradient(
                    left,
                    y,
                    left + stripeWidth,
                    y + stripeHeight,
                    scaleAlpha(withAlpha(accentColor, 52), alphaFactor),
                    0x00000000);
            y += stripeHeight + gap;
            step = (step + 1) % 4;
        }
    }

    private static PreviewTexture resolvePreviewTexture(String gameType) {
        ClientModePresentation presentation = resolvePresentation(gameType);
        if (presentation.previewTexture() == null) {
            return SHARED_PREVIEW;
        }
        return new PreviewTexture(
                presentation.previewTexture(),
                presentation.textureWidth(),
                presentation.textureHeight());
    }

    private static ClientModePresentation resolvePresentation(String gameType) {
        GameModeBootstrap.registerClientPresentations();
        return ClientModePresentationRegistry.find(gameType).orElseGet(() -> fallbackPresentation(gameType));
    }

    private static ClientModePresentation fallbackPresentation(String gameType) {
        String canonical = GameModeRegistry.canonicalize(gameType);
        int accent = FALLBACK_ACCENTS[Math.floorMod(canonical.hashCode(), FALLBACK_ACCENTS.length)];
        return new ClientModePresentation(
                (ResourceLocation) null,
                SHARED_PREVIEW.width(),
                SHARED_PREVIEW.height(),
                accent,
                "screen.codpattern.mode_select.preview_hint",
                "fallback");
    }

    private static void renderFullscreenTexture(
            GuiGraphics graphics,
            PreviewTexture texture,
            int screenWidth,
            int screenHeight,
            float alphaFactor) {
        if (texture == null || alphaFactor <= 0.0f || screenWidth <= 0 || screenHeight <= 0) {
            return;
        }

        float clampedAlpha = Math.max(0.0f, Math.min(1.0f, alphaFactor));
        float scale = screenHeight / (float) texture.height();
        int drawWidth = Math.max(1, Math.round(texture.width() * scale));
        int drawX = (screenWidth - drawWidth) / 2;

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, clampedAlpha);
        graphics.blit(
                texture.location(),
                drawX,
                0,
                drawWidth,
                screenHeight,
                0.0f,
                0.0f,
                texture.width(),
                texture.height(),
                texture.width(),
                texture.height());
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
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
