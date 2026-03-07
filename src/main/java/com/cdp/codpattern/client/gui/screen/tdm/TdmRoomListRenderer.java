package com.cdp.codpattern.client.gui.screen.tdm;

import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class TdmRoomListRenderer {
    private static final float HIGHLIGHT_APPROACH_STEP = 0.44f;
    private static final long NEW_ROOM_PULSE_MS = 550L;

    private TdmRoomListRenderer() {
    }

    public static List<String> render(
            GuiGraphics graphics,
            Minecraft mc,
            int roomListX,
            int roomListY,
            int roomListWidth,
            int roomListHeight,
            int roomItemHeight,
            Map<String, TdmRoomData> rooms,
            String selectedRoom,
            String joinedRoom,
            int scrollOffset,
            Map<String, Float> highlightProgress,
            Map<String, Long> roomEnteredAtMs,
            long nowMs,
            int mouseX,
            int mouseY,
            float panelAlphaFactor) {
        int referenceLineHeight = GuiTextHelper.referenceLineHeight(mc.font);
        int panelLeft = roomListX - GuiTextHelper.referenceScaled(5);
        int panelTop = roomListY - GuiTextHelper.referenceScaled(25);
        int panelRight = roomListX + roomListWidth + GuiTextHelper.referenceScaled(5);
        int panelBottom = roomListY + roomListHeight + GuiTextHelper.referenceScaled(5);

        graphics.fillGradient(panelLeft, panelTop, panelRight, panelBottom,
                scaleAlpha(CodTheme.PANEL_BG, panelAlphaFactor),
                scaleAlpha(0xCC101010, panelAlphaFactor));
        graphics.fill(panelLeft, panelTop, panelRight, panelTop + 1, scaleAlpha(CodTheme.BORDER_SUBTLE, panelAlphaFactor));
        graphics.fill(panelLeft, panelBottom - 1, panelRight, panelBottom, scaleAlpha(CodTheme.BORDER_SUBTLE, panelAlphaFactor));
        graphics.fill(panelLeft, panelTop, panelLeft + 1, panelBottom, scaleAlpha(CodTheme.BORDER_SUBTLE, panelAlphaFactor));
        graphics.fill(panelRight - 1, panelTop, panelRight, panelBottom, scaleAlpha(CodTheme.BORDER_SUBTLE, panelAlphaFactor));

        GuiTextHelper.drawReferenceString(
                graphics,
                mc.font,
                Component.translatable("screen.codpattern.tdm_room.available_rooms"),
                roomListX,
                roomListY - GuiTextHelper.referenceScaled(20),
                scaleAlpha(CodTheme.TEXT_PRIMARY, panelAlphaFactor),
                false);

        List<String> roomNames = orderedRoomNames(rooms, joinedRoom);
        highlightProgress.keySet().retainAll(roomNames);
        roomEnteredAtMs.keySet().retainAll(roomNames);

        if (roomNames.isEmpty()) {
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    Component.translatable("screen.codpattern.tdm_room.no_rooms"),
                    roomListX,
                    roomListY + GuiTextHelper.referenceScaled(10),
                    scaleAlpha(CodTheme.TEXT_SECONDARY, panelAlphaFactor),
                    false);
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    Component.translatable("screen.codpattern.tdm_room.create_hint"),
                    roomListX,
                    roomListY + GuiTextHelper.referenceScaled(10) + referenceLineHeight + GuiTextHelper.referenceScaled(3),
                    scaleAlpha(CodTheme.TEXT_DIM, panelAlphaFactor),
                    false);
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    Component.translatable("screen.codpattern.tdm_room.create_command"),
                    roomListX,
                    roomListY + GuiTextHelper.referenceScaled(10) + (referenceLineHeight + GuiTextHelper.referenceScaled(3)) * 2,
                    scaleAlpha(CodTheme.TEXT_DIM, panelAlphaFactor),
                    false);
            return roomNames;
        }

        int visibleCount = Math.max(1, roomListHeight / roomItemHeight);
        int maxScrollOffset = Math.max(0, roomNames.size() - visibleCount);
        int effectiveOffset = clamp(scrollOffset, 0, maxScrollOffset);
        int endIndex = Math.min(roomNames.size(), effectiveOffset + visibleCount);

        for (int index = effectiveOffset; index < endIndex; index++) {
            int visibleIndex = index - effectiveOffset;
            int y = roomListY + visibleIndex * roomItemHeight;
            String mapName = roomNames.get(index);
            TdmRoomData room = rooms.get(mapName);
            if (room == null) {
                continue;
            }

            boolean hovered = mouseX >= roomListX && mouseX <= roomListX + roomListWidth
                    && mouseY >= y && mouseY < y + roomItemHeight;
            boolean selected = mapName.equals(selectedRoom);
            boolean joined = mapName.equals(joinedRoom);

            float targetHighlight = joined ? 1.0f : (selected ? 0.85f : (hovered ? 0.45f : 0.0f));
            float currentHighlight = approach(
                    highlightProgress.getOrDefault(mapName, 0.0f),
                    targetHighlight,
                    HIGHLIGHT_APPROACH_STEP);
            highlightProgress.put(mapName, currentHighlight);

            int baseTop = withAlpha(CodTheme.CARD_BG_TOP, 70 + (int) (currentHighlight * 80.0f));
            int baseBottom = withAlpha(CodTheme.CARD_BG_BOTTOM, 78 + (int) (currentHighlight * 92.0f));
            graphics.fillGradient(
                    roomListX,
                    y,
                    roomListX + roomListWidth,
                    y + roomItemHeight - 2,
                    scaleAlpha(baseTop, panelAlphaFactor),
                    scaleAlpha(baseBottom, panelAlphaFactor));

            int edgeColor = joined ? CodTheme.HOVER_BORDER : (selected ? CodTheme.SELECTED_BORDER : 0);
            if (edgeColor != 0) {
                graphics.fill(roomListX, y, roomListX + GuiTextHelper.referenceScaled(2),
                        y + roomItemHeight - GuiTextHelper.referenceScaled(2),
                        scaleAlpha(edgeColor, panelAlphaFactor));
            }

            long enteredAt = roomEnteredAtMs.getOrDefault(mapName, 0L);
            if (enteredAt > 0L) {
                long elapsed = nowMs - enteredAt;
                if (elapsed >= NEW_ROOM_PULSE_MS) {
                    roomEnteredAtMs.remove(mapName);
                } else {
                    float pulse = 1.0f - (elapsed / (float) NEW_ROOM_PULSE_MS);
                    int pulseColor = withAlpha(CodTheme.HOVER_BORDER, Math.max(0, (int) (95.0f * pulse)));
                    graphics.fill(roomListX, y, roomListX + roomListWidth, y + roomItemHeight - 2,
                            scaleAlpha(pulseColor, panelAlphaFactor));
                }
            }

            String statusIcon = TdmRoomTextFormatter.statusIcon(room.state);
            String roomText = statusIcon + " " + mapName;
            if (!room.hasMatchEndTeleportPoint) {
                roomText += " §c!";
            }
            String infoText = String.format("%d/%d", room.playerCount, room.maxPlayers);
            int roomTextColor = joined
                    ? CodTheme.TEXT_HOVER
                    : (selected ? CodTheme.SELECTED_TEXT : CodTheme.TEXT_PRIMARY);

            int primaryTextY = y + GuiTextHelper.referenceScaled(4);
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    roomText,
                    roomListX + GuiTextHelper.referenceScaled(5),
                    primaryTextY,
                    scaleAlpha(roomTextColor, panelAlphaFactor),
                    false);
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    infoText,
                    roomListX + roomListWidth - GuiTextHelper.referenceWidth(mc.font, infoText) - GuiTextHelper.referenceScaled(5),
                    primaryTextY,
                    scaleAlpha(CodTheme.TEXT_SECONDARY, panelAlphaFactor),
                    false);

            String statusText = TdmRoomTextFormatter.roomListStatusText(
                    room.state,
                    room.remainingTimeTicks,
                    room.teamScores);
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    statusText,
                    roomListX + GuiTextHelper.referenceScaled(5),
                    primaryTextY + referenceLineHeight + GuiTextHelper.referenceScaled(2),
                    scaleAlpha(0xFFAFAFAF, panelAlphaFactor),
                    false);

            int barLeft = roomListX + GuiTextHelper.referenceScaled(5);
            int barRight = roomListX + roomListWidth - GuiTextHelper.referenceScaled(5);
            int barY = y + roomItemHeight - GuiTextHelper.referenceScaled(6);
            int barWidth = Math.max(1, barRight - barLeft);
            int fillWidth = Math.min(barWidth,
                    (int) (barWidth * (room.playerCount / (float) Math.max(1, room.maxPlayers))));
            graphics.fill(barLeft, barY, barRight, barY + GuiTextHelper.referenceScaled(2),
                    scaleAlpha(0x26FFFFFF, panelAlphaFactor));
            if (fillWidth > 0) {
                graphics.fill(barLeft, barY, barLeft + fillWidth, barY + GuiTextHelper.referenceScaled(2),
                        scaleAlpha(withAlpha(joined ? CodTheme.HOVER_BORDER : CodTheme.SELECTED_BORDER, 160), panelAlphaFactor));
            }
        }

        if (roomNames.size() > visibleCount) {
            int trackX = roomListX + roomListWidth - GuiTextHelper.referenceScaled(2);
            int trackTop = roomListY;
            int trackBottom = roomListY + roomListHeight;
            graphics.fill(trackX, trackTop, trackX + GuiTextHelper.referenceScaled(2), trackBottom,
                    scaleAlpha(0x22FFFFFF, panelAlphaFactor));

            int thumbHeight = Math.max(GuiTextHelper.referenceScaled(14),
                    (int) (roomListHeight * (visibleCount / (float) roomNames.size())));
            int thumbOffsetMax = Math.max(1, roomListHeight - thumbHeight);
            int thumbY = trackTop + (int) (thumbOffsetMax * (effectiveOffset / (float) maxScrollOffset));
            graphics.fill(trackX, thumbY, trackX + GuiTextHelper.referenceScaled(2), thumbY + thumbHeight,
                    scaleAlpha(CodTheme.SELECTED_BORDER, panelAlphaFactor));

            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    Component.translatable("screen.codpattern.tdm_room.scroll_hint"),
                    roomListX,
                    roomListY + roomListHeight + GuiTextHelper.referenceScaled(8),
                    scaleAlpha(CodTheme.TEXT_DIM, panelAlphaFactor),
                    false);
        }

        return roomNames;
    }

    private static List<String> orderedRoomNames(Map<String, TdmRoomData> rooms, String joinedRoom) {
        List<Map.Entry<String, TdmRoomData>> ordered = new ArrayList<>(rooms.entrySet());
        ordered.sort(roomComparator(joinedRoom));

        List<String> names = new ArrayList<>(ordered.size());
        for (Map.Entry<String, TdmRoomData> entry : ordered) {
            names.add(entry.getKey());
        }
        return names;
    }

    private static Comparator<Map.Entry<String, TdmRoomData>> roomComparator(String joinedRoom) {
        return (left, right) -> {
            String leftName = left.getKey();
            String rightName = right.getKey();
            if (leftName.equals(joinedRoom) && !rightName.equals(joinedRoom)) {
                return -1;
            }
            if (rightName.equals(joinedRoom) && !leftName.equals(joinedRoom)) {
                return 1;
            }

            TdmRoomData leftData = left.getValue();
            TdmRoomData rightData = right.getValue();
            int stateCompare = Integer.compare(statePriority(leftData.state), statePriority(rightData.state));
            if (stateCompare != 0) {
                return stateCompare;
            }

            int playerCompare = Integer.compare(rightData.playerCount, leftData.playerCount);
            if (playerCompare != 0) {
                return playerCompare;
            }

            return leftName.compareToIgnoreCase(rightName);
        };
    }

    private static int statePriority(String state) {
        if (state == null) {
            return 5;
        }
        return switch (state) {
            case "PLAYING" -> 0;
            case "WARMUP" -> 1;
            case "COUNTDOWN" -> 2;
            case "WAITING" -> 3;
            case "ENDED" -> 4;
            default -> 5;
        };
    }

    private static float approach(float current, float target, float step) {
        if (Math.abs(target - current) <= step) {
            return target;
        }
        return current + Math.copySign(step, target - current);
    }

    private static int scaleAlpha(int color, float factor) {
        int alpha = (color >>> 24) & 0xFF;
        int scaledAlpha = clamp((int) (alpha * Math.max(0.0f, Math.min(1.0f, factor))), 0, 255);
        return (scaledAlpha << 24) | (color & 0x00FFFFFF);
    }

    private static int withAlpha(int color, int alpha) {
        return (clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
