package com.cdp.codpattern.client.gui.screen.match;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.model.ClientModePresentation;
import com.cdp.codpattern.app.match.model.ClientModePresentationRegistry;
import com.cdp.codpattern.app.match.model.ModeDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class ModePreviewPanel {
    private static final ResourceLocation SHARED_PREVIEW =
            new ResourceLocation("codpattern", "textures/gui/modes/shared_preview.png");
    private static final ResourceLocation LOBBY_SHADE =
            new ResourceLocation("codpattern", "textures/gui/modes/lobby_shade.png");
    private static final int[] FALLBACK_ACCENTS = {0xFF88D6FF, 0xFFFFB347, 0xFF78F2BE, 0xFFE685FF};

    private ModePreviewPanel() {
    }

    public static void renderFullscreenBase(GuiGraphics graphics, int width, int height, float alpha) {
        if (alpha <= 0.0f || width <= 0 || height <= 0) {
            return;
        }
        drawPhoto(graphics, SHARED_PREVIEW, 3840, 2160, width, height, alpha);
        float opacity = Math.min(alpha, 1.0f);
        graphics.fillGradient(0, 0, width, height, (Math.round(52 * opacity) << 24) | 0x040608,
                (Math.round(156 * opacity) << 24) | 0x090A0C);
    }

    public static void renderFullscreenModeLayer(GuiGraphics graphics, int width, int height,
                                                ModeDescriptor mode, float alpha) {
        if (mode == null || alpha <= 0.0f) {
            return;
        }
        ClientModePresentation presentation = ClientModePresentationRegistry.find(mode.gameType()).orElse(null);
        if (presentation != null && presentation.previewTexture() != null) {
            drawPhoto(graphics, presentation.previewTexture(), presentation.textureWidth(), presentation.textureHeight(),
                    width, height, alpha);
        }
    }

    public static void renderLobbyShade(GuiGraphics graphics, int width, int height) {
        RenderSystem.enableBlend();
        graphics.blit(LOBBY_SHADE, 0, 0, width, height, 0.0f, 0.0f, 256, 144, 256, 144);
    }

    public static int accentColor(String gameType) {
        return ClientModePresentationRegistry.find(gameType).map(ClientModePresentation::accentColor)
                .orElseGet(() -> FALLBACK_ACCENTS[Math.floorMod(
                        GameModeRegistry.canonicalize(gameType).hashCode(), FALLBACK_ACCENTS.length)]);
    }

    public static Component resolveModeDescription(String gameType) {
        return ClientModePresentationRegistry.find(gameType).map(ClientModePresentation::descriptionKey)
                .filter(key -> !key.isBlank() && !"screen.codpattern.mode_select.preview_hint".equals(key))
                .map(Component::translatable).orElseGet(Component::empty);
    }

    private static void drawPhoto(GuiGraphics graphics, ResourceLocation texture, int textureWidth, int textureHeight,
                                  int width, int height, float alpha) {
        if (alpha <= 0.0f || width <= 0 || height <= 0 || textureWidth <= 0 || textureHeight <= 0) {
            return;
        }
        float scale = Math.max(width / (float) textureWidth, height / (float) textureHeight);
        int drawWidth = Math.max(width, Math.round(textureWidth * scale));
        int drawHeight = Math.max(height, Math.round(textureHeight * scale));
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, Math.min(1.0f, alpha));
        graphics.blit(texture, (width - drawWidth) / 2, (height - drawHeight) / 2, drawWidth, drawHeight,
                0.0f, 0.0f, textureWidth, textureHeight, textureWidth, textureHeight);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
