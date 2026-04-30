package com.cdp.codpattern.client.gui.screen.tdm;

import com.cdp.codpattern.client.gui.screen.match.ModeRoomRosterRenderer;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.Map;

/**
 * Legacy renderer name retained for older callers. New code should use {@link ModeRoomRosterRenderer}.
 */
public final class TdmRoomRosterRenderer {
    private TdmRoomRosterRenderer() {
    }

    public static void render(
            GuiGraphics graphics,
            Minecraft mc,
            int panelX,
            int panelWidth,
            int startY,
            int maxY,
            Map<String, List<PlayerInfo>> teamPlayers,
            float alphaFactor,
            long nowMs) {
        ModeRoomRosterRenderer.render(
                graphics,
                mc,
                panelX,
                panelWidth,
                startY,
                maxY,
                teamPlayers,
                alphaFactor,
                nowMs);
    }
}
