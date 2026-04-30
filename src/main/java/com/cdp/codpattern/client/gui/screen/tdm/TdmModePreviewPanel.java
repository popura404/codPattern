package com.cdp.codpattern.client.gui.screen.tdm;

import com.cdp.codpattern.app.match.model.ModeDescriptor;
import com.cdp.codpattern.client.gui.screen.match.ModePreviewPanel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Legacy preview helper name retained for older callers. New code should use {@link ModePreviewPanel}.
 */
@Deprecated(forRemoval = false)
public final class TdmModePreviewPanel {
    private TdmModePreviewPanel() {
    }

    public static void renderFullscreenBase(GuiGraphics graphics, int screenWidth, int screenHeight, float alphaFactor) {
        ModePreviewPanel.renderFullscreenBase(graphics, screenWidth, screenHeight, alphaFactor);
    }

    public static void renderFullscreenModeLayer(
            GuiGraphics graphics,
            int screenWidth,
            int screenHeight,
            ModeDescriptor descriptor,
            float alphaFactor) {
        ModePreviewPanel.renderFullscreenModeLayer(graphics, screenWidth, screenHeight, descriptor, alphaFactor);
    }

    public static int accentColor(String gameType) {
        return ModePreviewPanel.accentColor(gameType);
    }

    public static Component resolveModeDescription(String gameType) {
        return ModePreviewPanel.resolveModeDescription(gameType);
    }
}
