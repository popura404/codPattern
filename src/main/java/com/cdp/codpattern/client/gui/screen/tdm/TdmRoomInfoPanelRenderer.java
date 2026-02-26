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

    public static void render(GuiGraphics graphics,
            Minecraft mc,
            int screenWidth,
            int screenHeight,
            int roomListX,
            int roomListWidth,
            int infoActionBottomY,
            String joinedRoom,
            String selectedRoom,
            Map<String, TdmRoomData> rooms,
            Map<String, List<PlayerInfo>> teamPlayers,
            boolean leavePending,
            boolean joinGamePending,
            boolean hasRoomNotice,
            String roomNoticeText,
            int roomNoticeColor) {
        int padding = 15;
        int headerHeight = 45;
        int panelX = roomListX + roomListWidth + padding * 2;
        int panelY = headerHeight;
        int panelWidth = screenWidth - panelX - padding;
        int panelHeight = screenHeight - headerHeight - 50 - padding;

        graphics.fillGradient(panelX - 5, panelY - 5, panelX + panelWidth + 5, panelY + panelHeight + 5,
                CodTheme.PANEL_BG, 0xCC101010);
        graphics.fill(panelX - 5, panelY - 5, panelX + panelWidth + 5, panelY - 4, CodTheme.BORDER_SUBTLE);
        graphics.fill(panelX - 5, panelY + panelHeight + 4, panelX + panelWidth + 5, panelY + panelHeight + 5,
                CodTheme.BORDER_SUBTLE);
        graphics.fill(panelX - 5, panelY - 5, panelX - 4, panelY + panelHeight + 5, CodTheme.BORDER_SUBTLE);
        graphics.fill(panelX + panelWidth + 4, panelY - 5, panelX + panelWidth + 5, panelY + panelHeight + 5,
                CodTheme.BORDER_SUBTLE);

        if (joinedRoom != null) {
            graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.current_room", joinedRoom),
                    panelX, panelY, CodTheme.TEXT_PRIMARY);
            graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.select_team"), panelX,
                    panelY + 15, CodTheme.TEXT_SECONDARY);
        } else if (selectedRoom != null) {
            graphics.drawString(mc.font,
                    Component.translatable("screen.codpattern.tdm_room.selected_room", selectedRoom), panelX, panelY,
                    CodTheme.TEXT_PRIMARY);
            graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.join_hint"), panelX,
                    panelY + 15, CodTheme.TEXT_SECONDARY);
        } else {
            graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.select_hint"), panelX,
                    panelY, CodTheme.TEXT_SECONDARY);
        }

        graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.room_actions"), panelX,
                panelY + 30, CodTheme.TEXT_SECONDARY);
        int actionDividerRight = panelX + Math.max(20, Math.min(190, panelWidth - 8));
        graphics.fill(panelX, panelY + 40, actionDividerRight, panelY + 41, CodTheme.DIVIDER);

        int infoY = Math.max(panelY + 78, infoActionBottomY + 12);
        if (joinedRoom != null) {
            TdmRoomData joined = rooms.get(joinedRoom);
            if (joined != null) {
                graphics.drawString(mc.font,
                        Component.translatable("screen.codpattern.tdm_room.current_score",
                                TdmRoomTextFormatter.teamScoreText(joined.teamScores)),
                        panelX, infoY, 0xFFE5E5E5);
                graphics.drawString(mc.font,
                        Component.translatable("screen.codpattern.tdm_room.current_phase",
                                TdmRoomTextFormatter.phaseStatusText(joined.state, joined.remainingTimeTicks)),
                        panelX, infoY + 12, 0xFFB0B0B0);
                infoY += 26;
            }
        }

        TdmRoomData selected = null;
        if (joinedRoom != null) {
            selected = rooms.get(joinedRoom);
        } else if (selectedRoom != null) {
            selected = rooms.get(selectedRoom);
        }
        if (selected != null && !selected.hasMatchEndTeleportPoint) {
            graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.warning_no_end_tp"),
                    panelX, infoY, CodTheme.TEXT_DANGER);
            graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.warning_no_end_tp_hint"),
                    panelX, infoY + 12, 0xFFAA55);
            infoY += 24;
        }

        int hintLines = (leavePending ? 1 : 0) + (joinGamePending ? 1 : 0);
        int rosterTop = Math.max(infoActionBottomY + 18, infoY + 8);
        int rosterBottom = panelY + panelHeight - 24 - (hintLines * 12);
        if (joinedRoom != null && rosterTop < rosterBottom) {
            graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.roster_title"),
                    panelX, rosterTop - 11, CodTheme.TEXT_SECONDARY);
            TdmRoomRosterRenderer.render(graphics, mc, panelX, panelWidth, rosterTop, rosterBottom, teamPlayers);
        }

        int hintY = panelY + panelHeight - 24;
        if (leavePending) {
            graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.leave_room_cancel_hint"),
                    panelX, hintY, 0xFFFFD75E);
            hintY -= 12;
        }
        if (joinGamePending) {
            graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm.join_game_cancel_hint"),
                    panelX, hintY, 0xFFFFD75E);
            hintY -= 12;
        }
        if (hasRoomNotice) {
            int noticeY = hintY;
            graphics.drawString(mc.font, roomNoticeText, panelX, noticeY, roomNoticeColor);
        }
    }
}
