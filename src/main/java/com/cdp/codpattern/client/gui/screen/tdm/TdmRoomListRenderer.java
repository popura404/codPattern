package com.cdp.codpattern.client.gui.screen.tdm;

import com.cdp.codpattern.client.gui.CodTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TdmRoomListRenderer {
    private TdmRoomListRenderer() {
    }

    public static List<String> render(GuiGraphics graphics,
            Minecraft mc,
            int roomListX,
            int roomListY,
            int roomListWidth,
            int roomListHeight,
            int roomItemHeight,
            Map<String, TdmRoomData> rooms,
            String selectedRoom,
            String joinedRoom,
            int mouseX,
            int mouseY) {
        int panelLeft = roomListX - 5;
        int panelTop = roomListY - 25;
        int panelRight = roomListX + roomListWidth + 5;
        int panelBottom = roomListY + roomListHeight + 5;

        graphics.fillGradient(panelLeft, panelTop, panelRight, panelBottom, CodTheme.PANEL_BG, 0xCC101010);
        graphics.fill(panelLeft, panelTop, panelRight, panelTop + 1, CodTheme.BORDER_SUBTLE);
        graphics.fill(panelLeft, panelBottom - 1, panelRight, panelBottom, CodTheme.BORDER_SUBTLE);
        graphics.fill(panelLeft, panelTop, panelLeft + 1, panelBottom, CodTheme.BORDER_SUBTLE);
        graphics.fill(panelRight - 1, panelTop, panelRight, panelBottom, CodTheme.BORDER_SUBTLE);

        graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.available_rooms"), roomListX,
                roomListY - 20, CodTheme.TEXT_PRIMARY);

        List<String> roomNames = new ArrayList<>();
        if (rooms.isEmpty()) {
            graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.no_rooms"), roomListX,
                    roomListY + 10, CodTheme.TEXT_SECONDARY);
            graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.create_hint"), roomListX,
                    roomListY + 25, CodTheme.TEXT_DIM);
            graphics.drawString(mc.font, "§b/fpsm map create cdptdm <名称> <从> <到>", roomListX, roomListY + 40, CodTheme.TEXT_DIM);
            return roomNames;
        }

        int y = roomListY;
        for (Map.Entry<String, TdmRoomData> entry : rooms.entrySet()) {
            if (y + roomItemHeight > roomListY + roomListHeight) {
                break;
            }

            String mapName = entry.getKey();
            TdmRoomData room = entry.getValue();
            roomNames.add(mapName);

            boolean hovered = mouseX >= roomListX && mouseX <= roomListX + roomListWidth &&
                    mouseY >= y && mouseY < y + roomItemHeight;
            boolean selected = mapName.equals(selectedRoom);
            boolean joined = mapName.equals(joinedRoom);

            int bgColor;
            int edgeColor = 0;
            if (joined) {
                bgColor = withAlpha(CodTheme.HOVER_BG_BOTTOM, 150);
                edgeColor = CodTheme.HOVER_BORDER;
            } else if (selected) {
                bgColor = withAlpha(CodTheme.CARD_BG_TOP, 180);
                edgeColor = CodTheme.SELECTED_BORDER;
            } else if (hovered) {
                bgColor = withAlpha(CodTheme.HOVER_BG_TOP, 110);
            } else {
                bgColor = withAlpha(CodTheme.CARD_BG_TOP, 70);
            }
            graphics.fill(roomListX, y, roomListX + roomListWidth, y + roomItemHeight - 2, bgColor);
            if (edgeColor != 0) {
                graphics.fill(roomListX, y, roomListX + 2, y + roomItemHeight - 2, edgeColor);
            }

            String statusIcon = TdmRoomTextFormatter.statusIcon(room.state);
            String roomText = String.format("%s %s", statusIcon, mapName);
            if (!room.hasMatchEndTeleportPoint) {
                roomText += " §c!";
            }
            String infoText = String.format("%d/%d", room.playerCount, room.maxPlayers);

            int textColor = joined ? CodTheme.TEXT_HOVER : (selected ? CodTheme.SELECTED_TEXT : CodTheme.TEXT_PRIMARY);
            graphics.drawString(mc.font, roomText, roomListX + 5, y + 4, textColor);
            graphics.drawString(mc.font, infoText, roomListX + roomListWidth - mc.font.width(infoText) - 5, y + 4,
                    CodTheme.TEXT_SECONDARY);

            String statusText = TdmRoomTextFormatter.roomListStatusText(
                    room.state,
                    room.remainingTimeTicks,
                    room.teamScores
            );
            graphics.drawString(mc.font, statusText, roomListX + 5, y + 18, 0xFFAFAFAF);

            y += roomItemHeight;
        }

        return roomNames;
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }
}
