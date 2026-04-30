package com.cdp.codpattern.client.gui.screen.tdm;

import com.cdp.codpattern.client.gui.screen.match.ModeRoomInfoPanelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Legacy renderer name retained for older callers. New code should use {@link ModeRoomInfoPanelRenderer}.
 */
public final class TdmRoomInfoPanelRenderer {
    private TdmRoomInfoPanelRenderer() {
    }

    public static void render(
            GuiGraphics graphics,
            Minecraft mc,
            int panelX,
            int panelY,
            int panelWidth,
            int panelHeight,
            int infoActionBottomY,
            LobbySummaryState lobbySummaryState,
            JoinedRoomLiveState joinedRoomLiveState,
            SelectedRoomPreviewState selectedRoomPreviewState,
            String joinedRoom,
            String selectedRoom,
            boolean hasConfirmHint,
            String confirmHintText,
            int confirmHintColor,
            boolean hasRoomNotice,
            String roomNoticeText,
            int roomNoticeColor,
            float panelAlphaFactor,
            float contentAlphaFactor) {
        ModeRoomInfoPanelRenderer.render(
                graphics,
                mc,
                panelX,
                panelY,
                panelWidth,
                panelHeight,
                infoActionBottomY,
                lobbySummaryState,
                joinedRoomLiveState,
                selectedRoomPreviewState,
                joinedRoom,
                selectedRoom,
                hasConfirmHint,
                confirmHintText,
                confirmHintColor,
                hasRoomNotice,
                roomNoticeText,
                roomNoticeColor,
                panelAlphaFactor,
                contentAlphaFactor);
    }
}
