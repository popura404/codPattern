package com.cdp.codpattern.client.gui.screen.tdm;

import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

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
            String joinedRoom,
            String selectedRoom,
            Map<String, TdmRoomData> rooms,
            Map<String, List<PlayerInfo>> teamPlayers,
            boolean leavePending,
            boolean joinGamePending,
            boolean hasRoomNotice,
            String roomNoticeText,
            int roomNoticeColor,
            float panelAlphaFactor,
            float contentAlphaFactor) {
        int referenceLineHeight = GuiTextHelper.referenceLineHeight(mc.font);
        int frameLeft = panelX - GuiTextHelper.referenceScaled(5);
        int frameTop = panelY - GuiTextHelper.referenceScaled(5);
        int frameRight = panelX + panelWidth + GuiTextHelper.referenceScaled(5);
        int frameBottom = panelY + panelHeight + GuiTextHelper.referenceScaled(5);
        float contentFactor = Math.max(0.0f, Math.min(1.0f, panelAlphaFactor * contentAlphaFactor));

        graphics.fillGradient(frameLeft, frameTop, frameRight, frameBottom,
                scaleAlpha(CodTheme.PANEL_BG, panelAlphaFactor),
                scaleAlpha(0xCC101010, panelAlphaFactor));
        graphics.fill(frameLeft, frameTop, frameRight, frameTop + 1, scaleAlpha(CodTheme.BORDER_SUBTLE, panelAlphaFactor));
        graphics.fill(frameLeft, frameBottom - 1, frameRight, frameBottom, scaleAlpha(CodTheme.BORDER_SUBTLE, panelAlphaFactor));
        graphics.fill(frameLeft, frameTop, frameLeft + 1, frameBottom, scaleAlpha(CodTheme.BORDER_SUBTLE, panelAlphaFactor));
        graphics.fill(frameRight - 1, frameTop, frameRight, frameBottom, scaleAlpha(CodTheme.BORDER_SUBTLE, panelAlphaFactor));

        int contentX = panelX + GuiTextHelper.referenceScaled(8);
        int contentWidth = Math.max(GuiTextHelper.referenceScaled(40), panelWidth - GuiTextHelper.referenceScaled(16));

        renderHeader(graphics, mc, contentX, panelY, joinedRoom, selectedRoom, contentFactor);

        TdmRoomData activeRoom = joinedRoom != null ? rooms.get(joinedRoom)
                : (selectedRoom != null ? rooms.get(selectedRoom) : null);
        renderOverviewCard(graphics, mc, contentX, panelY + GuiTextHelper.referenceScaled(28), contentWidth, activeRoom,
                contentFactor);

        GuiTextHelper.drawReferenceString(
                graphics,
                mc.font,
                Component.translatable("screen.codpattern.tdm_room.room_actions"),
                contentX,
                panelY + GuiTextHelper.referenceScaled(76),
                scaleAlpha(CodTheme.TEXT_SECONDARY, contentFactor),
                false);
        int dividerEndX = contentX + Math.max(GuiTextHelper.referenceScaled(24),
                Math.min(GuiTextHelper.referenceScaled(220), contentWidth - GuiTextHelper.referenceScaled(8)));
        graphics.fill(contentX, panelY + GuiTextHelper.referenceScaled(85), dividerEndX, panelY + GuiTextHelper.referenceScaled(86),
                scaleAlpha(CodTheme.DIVIDER, contentFactor));

        int infoY = Math.max(panelY + GuiTextHelper.referenceScaled(90), infoActionBottomY + GuiTextHelper.referenceScaled(12));
        if (joinedRoom != null) {
            TdmRoomData joined = rooms.get(joinedRoom);
            if (joined != null) {
                GuiTextHelper.drawReferenceString(
                        graphics,
                        mc.font,
                        Component.translatable("screen.codpattern.tdm_room.current_score",
                                TdmRoomTextFormatter.teamScoreText(joined.teamScores)),
                        contentX,
                        infoY,
                        scaleAlpha(0xFFE5E5E5, contentFactor),
                        false);
                GuiTextHelper.drawReferenceString(
                        graphics,
                        mc.font,
                        Component.translatable("screen.codpattern.tdm_room.current_phase",
                                TdmRoomTextFormatter.phaseStatusText(joined.state, joined.remainingTimeTicks)),
                        contentX,
                        infoY + referenceLineHeight + GuiTextHelper.referenceScaled(3),
                        scaleAlpha(0xFFB0B0B0, contentFactor),
                        false);
                infoY += referenceLineHeight * 2 + GuiTextHelper.referenceScaled(8);
            }
        }

        if (activeRoom != null && !activeRoom.hasMatchEndTeleportPoint) {
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    Component.translatable("screen.codpattern.tdm_room.warning_no_end_tp"),
                    contentX,
                    infoY,
                    scaleAlpha(CodTheme.TEXT_DANGER, contentFactor),
                    false);
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    Component.translatable("screen.codpattern.tdm_room.warning_no_end_tp_hint"),
                    contentX,
                    infoY + referenceLineHeight + GuiTextHelper.referenceScaled(3),
                    scaleAlpha(0xFFAA55, contentFactor),
                    false);
            infoY += referenceLineHeight * 2 + GuiTextHelper.referenceScaled(6);
        }

        int hintLines = (leavePending ? 1 : 0) + (joinGamePending ? 1 : 0) + (hasRoomNotice ? 1 : 0);
        int rosterTop = Math.max(infoActionBottomY + GuiTextHelper.referenceScaled(18), infoY + GuiTextHelper.referenceScaled(8));
        int rosterBottom = panelY + panelHeight - GuiTextHelper.referenceScaled(24)
                - (hintLines * (referenceLineHeight + GuiTextHelper.referenceScaled(3)));
        if (joinedRoom != null && rosterTop < rosterBottom) {
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    Component.translatable("screen.codpattern.tdm_room.roster_title"),
                    contentX,
                    rosterTop - referenceLineHeight - GuiTextHelper.referenceScaled(2),
                    scaleAlpha(CodTheme.TEXT_SECONDARY, contentFactor),
                    false);
            TdmRoomRosterRenderer.render(
                    graphics,
                    mc,
                    contentX,
                    contentWidth,
                    rosterTop,
                    rosterBottom,
                    teamPlayers,
                    contentFactor,
                    System.currentTimeMillis());
        }

        int hintY = panelY + panelHeight - GuiTextHelper.referenceScaled(24);
        if (leavePending) {
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    Component.translatable("screen.codpattern.tdm_room.leave_room_cancel_hint"),
                    contentX,
                    hintY,
                    scaleAlpha(0xFFFFD75E, contentFactor),
                    false);
            hintY -= referenceLineHeight + GuiTextHelper.referenceScaled(3);
        }
        if (joinGamePending) {
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    Component.translatable("screen.codpattern.tdm.join_game_cancel_hint"),
                    contentX,
                    hintY,
                    scaleAlpha(0xFFFFD75E, contentFactor),
                    false);
            hintY -= referenceLineHeight + GuiTextHelper.referenceScaled(3);
        }
        if (hasRoomNotice) {
            GuiTextHelper.drawReferenceString(graphics, mc.font, roomNoticeText, contentX, hintY,
                    scaleAlpha(roomNoticeColor, contentFactor), false);
        }
    }

    private static void renderHeader(
            GuiGraphics graphics,
            Minecraft mc,
            int x,
            int panelY,
            String joinedRoom,
            String selectedRoom,
            float alphaFactor) {
        int secondaryY = panelY + GuiTextHelper.referenceLineHeight(mc.font) + GuiTextHelper.referenceScaled(4);
        if (joinedRoom != null) {
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    Component.translatable("screen.codpattern.tdm_room.current_room", joinedRoom),
                    x,
                    panelY,
                    scaleAlpha(CodTheme.TEXT_PRIMARY, alphaFactor),
                    false);
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    Component.translatable("screen.codpattern.tdm_room.select_team"),
                    x,
                    secondaryY,
                    scaleAlpha(CodTheme.TEXT_SECONDARY, alphaFactor),
                    false);
            return;
        }

        if (selectedRoom != null) {
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    Component.translatable("screen.codpattern.tdm_room.selected_room", selectedRoom),
                    x,
                    panelY,
                    scaleAlpha(CodTheme.TEXT_PRIMARY, alphaFactor),
                    false);
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    Component.translatable("screen.codpattern.tdm_room.join_hint"),
                    x,
                    secondaryY,
                    scaleAlpha(CodTheme.TEXT_SECONDARY, alphaFactor),
                    false);
            return;
        }

        GuiTextHelper.drawReferenceString(
                graphics,
                mc.font,
                Component.translatable("screen.codpattern.tdm_room.select_hint"),
                x,
                panelY,
                scaleAlpha(CodTheme.TEXT_SECONDARY, alphaFactor),
                false);
    }

    private static void renderOverviewCard(
            GuiGraphics graphics,
            Minecraft mc,
            int x,
            int y,
            int width,
            TdmRoomData activeRoom,
            float alphaFactor) {
        int cardHeight = GuiTextHelper.referenceScaled(42);
        int lineHeight = GuiTextHelper.referenceLineHeight(mc.font);
        graphics.fillGradient(
                x,
                y,
                x + width,
                y + cardHeight,
                scaleAlpha(withAlpha(CodTheme.CARD_BG_TOP, 180), alphaFactor),
                scaleAlpha(withAlpha(CodTheme.CARD_BG_BOTTOM, 200), alphaFactor));
        graphics.fill(x, y + cardHeight - GuiTextHelper.referenceScaled(2), x + width, y + cardHeight,
                scaleAlpha(withAlpha(CodTheme.SELECTED_BORDER, 170), alphaFactor));

        GuiTextHelper.drawReferenceString(
                graphics,
                mc.font,
                Component.translatable("screen.codpattern.tdm_room.overview"),
                x + GuiTextHelper.referenceScaled(6),
                y + GuiTextHelper.referenceScaled(4),
                scaleAlpha(CodTheme.TEXT_SECONDARY, alphaFactor),
                false);

        if (activeRoom == null) {
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    Component.translatable("screen.codpattern.tdm_room.select_hint"),
                    x + GuiTextHelper.referenceScaled(6),
                    y + GuiTextHelper.referenceScaled(4) + lineHeight + GuiTextHelper.referenceScaled(3),
                    scaleAlpha(CodTheme.TEXT_DIM, alphaFactor),
                    false);
            return;
        }

        GuiTextHelper.drawReferenceString(
                graphics,
                mc.font,
                Component.translatable(
                        "screen.codpattern.tdm_room.players",
                        activeRoom.playerCount,
                        activeRoom.maxPlayers),
                x + GuiTextHelper.referenceScaled(6),
                y + GuiTextHelper.referenceScaled(4) + lineHeight + GuiTextHelper.referenceScaled(3),
                scaleAlpha(CodTheme.TEXT_PRIMARY, alphaFactor),
                false);

        String phaseText = TdmRoomTextFormatter.phaseStatusText(activeRoom.state, activeRoom.remainingTimeTicks);
        int phaseColor = "PLAYING".equals(activeRoom.state) ? 0xFFFF7A7A : 0xFFB8C7D8;
        GuiTextHelper.drawReferenceString(
                graphics,
                mc.font,
                Component.translatable("screen.codpattern.tdm_room.current_phase", phaseText),
                x + GuiTextHelper.referenceScaled(6),
                y + GuiTextHelper.referenceScaled(4) + (lineHeight + GuiTextHelper.referenceScaled(3)) * 2,
                scaleAlpha(phaseColor, alphaFactor),
                false);
    }

    private static int scaleAlpha(int color, float factor) {
        int alpha = (color >>> 24) & 0xFF;
        int scaled = clamp((int) (alpha * Math.max(0.0f, Math.min(1.0f, factor))), 0, 255);
        return (scaled << 24) | (color & 0x00FFFFFF);
    }

    private static int withAlpha(int color, int alpha) {
        return (clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
