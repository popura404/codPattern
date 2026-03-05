package com.cdp.codpattern.client.gui.screen.tdm;

import com.cdp.codpattern.client.gui.CodTheme;
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
        int frameLeft = panelX - 5;
        int frameTop = panelY - 5;
        int frameRight = panelX + panelWidth + 5;
        int frameBottom = panelY + panelHeight + 5;
        float contentFactor = Math.max(0.0f, Math.min(1.0f, panelAlphaFactor * contentAlphaFactor));

        graphics.fillGradient(frameLeft, frameTop, frameRight, frameBottom,
                scaleAlpha(CodTheme.PANEL_BG, panelAlphaFactor),
                scaleAlpha(0xCC101010, panelAlphaFactor));
        graphics.fill(frameLeft, frameTop, frameRight, frameTop + 1, scaleAlpha(CodTheme.BORDER_SUBTLE, panelAlphaFactor));
        graphics.fill(frameLeft, frameBottom - 1, frameRight, frameBottom, scaleAlpha(CodTheme.BORDER_SUBTLE, panelAlphaFactor));
        graphics.fill(frameLeft, frameTop, frameLeft + 1, frameBottom, scaleAlpha(CodTheme.BORDER_SUBTLE, panelAlphaFactor));
        graphics.fill(frameRight - 1, frameTop, frameRight, frameBottom, scaleAlpha(CodTheme.BORDER_SUBTLE, panelAlphaFactor));

        int contentX = panelX + 8;
        int contentWidth = Math.max(40, panelWidth - 16);

        renderHeader(graphics, mc, contentX, panelY, joinedRoom, selectedRoom, contentFactor);

        TdmRoomData activeRoom = joinedRoom != null ? rooms.get(joinedRoom)
                : (selectedRoom != null ? rooms.get(selectedRoom) : null);
        renderOverviewCard(graphics, mc, contentX, panelY + 28, contentWidth, activeRoom, contentFactor);

        graphics.drawString(mc.font,
                Component.translatable("screen.codpattern.tdm_room.room_actions"),
                contentX,
                panelY + 76,
                scaleAlpha(CodTheme.TEXT_SECONDARY, contentFactor));
        int dividerEndX = contentX + Math.max(24, Math.min(220, contentWidth - 8));
        graphics.fill(contentX, panelY + 85, dividerEndX, panelY + 86, scaleAlpha(CodTheme.DIVIDER, contentFactor));

        int infoY = Math.max(panelY + 90, infoActionBottomY + 12);
        if (joinedRoom != null) {
            TdmRoomData joined = rooms.get(joinedRoom);
            if (joined != null) {
                graphics.drawString(mc.font,
                        Component.translatable("screen.codpattern.tdm_room.current_score",
                                TdmRoomTextFormatter.teamScoreText(joined.teamScores)),
                        contentX,
                        infoY,
                        scaleAlpha(0xFFE5E5E5, contentFactor));
                graphics.drawString(mc.font,
                        Component.translatable("screen.codpattern.tdm_room.current_phase",
                                TdmRoomTextFormatter.phaseStatusText(joined.state, joined.remainingTimeTicks)),
                        contentX,
                        infoY + 12,
                        scaleAlpha(0xFFB0B0B0, contentFactor));
                infoY += 26;
            }
        }

        if (activeRoom != null && !activeRoom.hasMatchEndTeleportPoint) {
            graphics.drawString(mc.font,
                    Component.translatable("screen.codpattern.tdm_room.warning_no_end_tp"),
                    contentX,
                    infoY,
                    scaleAlpha(CodTheme.TEXT_DANGER, contentFactor));
            graphics.drawString(mc.font,
                    Component.translatable("screen.codpattern.tdm_room.warning_no_end_tp_hint"),
                    contentX,
                    infoY + 12,
                    scaleAlpha(0xFFAA55, contentFactor));
            infoY += 24;
        }

        int hintLines = (leavePending ? 1 : 0) + (joinGamePending ? 1 : 0) + (hasRoomNotice ? 1 : 0);
        int rosterTop = Math.max(infoActionBottomY + 18, infoY + 8);
        int rosterBottom = panelY + panelHeight - 24 - (hintLines * 12);
        if (joinedRoom != null && rosterTop < rosterBottom) {
            graphics.drawString(mc.font,
                    Component.translatable("screen.codpattern.tdm_room.roster_title"),
                    contentX,
                    rosterTop - 11,
                    scaleAlpha(CodTheme.TEXT_SECONDARY, contentFactor));
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

        int hintY = panelY + panelHeight - 24;
        if (leavePending) {
            graphics.drawString(mc.font,
                    Component.translatable("screen.codpattern.tdm_room.leave_room_cancel_hint"),
                    contentX,
                    hintY,
                    scaleAlpha(0xFFFFD75E, contentFactor));
            hintY -= 12;
        }
        if (joinGamePending) {
            graphics.drawString(mc.font,
                    Component.translatable("screen.codpattern.tdm.join_game_cancel_hint"),
                    contentX,
                    hintY,
                    scaleAlpha(0xFFFFD75E, contentFactor));
            hintY -= 12;
        }
        if (hasRoomNotice) {
            graphics.drawString(mc.font, roomNoticeText, contentX, hintY, scaleAlpha(roomNoticeColor, contentFactor));
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
        if (joinedRoom != null) {
            graphics.drawString(mc.font,
                    Component.translatable("screen.codpattern.tdm_room.current_room", joinedRoom),
                    x,
                    panelY,
                    scaleAlpha(CodTheme.TEXT_PRIMARY, alphaFactor));
            graphics.drawString(mc.font,
                    Component.translatable("screen.codpattern.tdm_room.select_team"),
                    x,
                    panelY + 13,
                    scaleAlpha(CodTheme.TEXT_SECONDARY, alphaFactor));
            return;
        }

        if (selectedRoom != null) {
            graphics.drawString(mc.font,
                    Component.translatable("screen.codpattern.tdm_room.selected_room", selectedRoom),
                    x,
                    panelY,
                    scaleAlpha(CodTheme.TEXT_PRIMARY, alphaFactor));
            graphics.drawString(mc.font,
                    Component.translatable("screen.codpattern.tdm_room.join_hint"),
                    x,
                    panelY + 13,
                    scaleAlpha(CodTheme.TEXT_SECONDARY, alphaFactor));
            return;
        }

        graphics.drawString(mc.font,
                Component.translatable("screen.codpattern.tdm_room.select_hint"),
                x,
                panelY,
                scaleAlpha(CodTheme.TEXT_SECONDARY, alphaFactor));
    }

    private static void renderOverviewCard(
            GuiGraphics graphics,
            Minecraft mc,
            int x,
            int y,
            int width,
            TdmRoomData activeRoom,
            float alphaFactor) {
        int cardHeight = 42;
        graphics.fillGradient(
                x,
                y,
                x + width,
                y + cardHeight,
                scaleAlpha(withAlpha(CodTheme.CARD_BG_TOP, 180), alphaFactor),
                scaleAlpha(withAlpha(CodTheme.CARD_BG_BOTTOM, 200), alphaFactor));
        graphics.fill(x, y + cardHeight - 2, x + width, y + cardHeight, scaleAlpha(withAlpha(CodTheme.SELECTED_BORDER, 170), alphaFactor));

        graphics.drawString(mc.font,
                Component.translatable("screen.codpattern.tdm_room.overview"),
                x + 6,
                y + 4,
                scaleAlpha(CodTheme.TEXT_SECONDARY, alphaFactor));

        if (activeRoom == null) {
            graphics.drawString(mc.font,
                    Component.translatable("screen.codpattern.tdm_room.select_hint"),
                    x + 6,
                    y + 16,
                    scaleAlpha(CodTheme.TEXT_DIM, alphaFactor));
            return;
        }

        graphics.drawString(mc.font,
                Component.translatable(
                        "screen.codpattern.tdm_room.players",
                        activeRoom.playerCount,
                        activeRoom.maxPlayers),
                x + 6,
                y + 16,
                scaleAlpha(CodTheme.TEXT_PRIMARY, alphaFactor));

        String phaseText = TdmRoomTextFormatter.phaseStatusText(activeRoom.state, activeRoom.remainingTimeTicks);
        int phaseColor = "PLAYING".equals(activeRoom.state) ? 0xFFFF7A7A : 0xFFB8C7D8;
        graphics.drawString(mc.font,
                Component.translatable("screen.codpattern.tdm_room.current_phase", phaseText),
                x + 6,
                y + 28,
                scaleAlpha(phaseColor, alphaFactor));
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
