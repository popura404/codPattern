package com.cdp.codpattern.client.gui.screen.tdm;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.model.ModeDescriptor;
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

    private static final int BASE_FRAME_INSET = 5;
    private static final int BASE_PANEL_PADDING = 8;
    private static final int BASE_HEADER_SECTION_HEIGHT = 24;
    private static final int BASE_FOOTER_SECTION_HEIGHT = 20;
    private static final int BASE_ROW_TOP_PADDING = 2;
    private static final int BASE_ROW_SECONDARY_GAP = 0;
    private static final int BASE_ROW_BAR_BOTTOM_PADDING = 2;

    public enum ActionType {JOIN, LEAVE, SWITCH}

    public record ActionHitbox(String roomName, ActionType type, int x, int y, int width,
            int height,
            boolean enabled
    ) {
        public boolean contains(double mouseX, double mouseY) {return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    public record RoomHitbox(String roomName, int x, int y, int width, int height
    ) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    public record RenderResult(
            List<String> roomNames,
            List<RoomHitbox> roomHitboxes,
            List<ActionHitbox> actionHitboxes
    ) {
        public static RenderResult empty() {
            return new RenderResult(List.of(), List.of(), List.of());
        }

        public ActionHitbox actionAt(double mouseX, double mouseY) {
            for (ActionHitbox hitbox : actionHitboxes) {
                if (hitbox.contains(mouseX, mouseY)) return hitbox;
            }
            return null;
        }

        public String roomAt(double mouseX, double mouseY) {
            for (RoomHitbox hitbox : roomHitboxes) {
                if (hitbox.contains(mouseX, mouseY)) return hitbox.roomName();
            }
            return null;
        }
    }

    private TdmRoomListRenderer() {
    }

    public static int listViewportHeight(int panelHeight) {
        return Math.max(
                GuiTextHelper.referenceScaled(40),
                panelHeight - headerSectionHeight() - footerSectionHeight());
    }

    public static RenderResult render(
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
            boolean hasPendingAction,
            boolean leavePending,
            float panelAlphaFactor) {
        int referenceLineHeight = GuiTextHelper.referenceLineHeight(mc.font);
        int frameInset = frameInset();
        int panelPadding = panelPadding();
        int frameLeft = roomListX - frameInset;
        int frameTop = roomListY - frameInset;
        int frameRight = roomListX + roomListWidth + frameInset;
        int frameBottom = roomListY + roomListHeight + frameInset;
        int headerHeight = headerSectionHeight();
        int footerHeight = footerSectionHeight();
        int titleY = roomListY + GuiTextHelper.referenceScaled(4);
        int listTop = roomListY + headerHeight;
        int listBottom = roomListY + roomListHeight - footerHeight;
        int listHeight = Math.max(GuiTextHelper.referenceScaled(40), listBottom - listTop);
        int footerY = roomListY + roomListHeight - footerHeight + GuiTextHelper.referenceScaled(3);

        graphics.fillGradient(frameLeft, frameTop, frameRight, frameBottom,
                scaleAlpha(CodTheme.PANEL_BG, panelAlphaFactor),
                scaleAlpha(0xCC101010, panelAlphaFactor));
        graphics.fill(frameLeft, frameTop, frameRight, frameTop + 1, scaleAlpha(CodTheme.BORDER_SUBTLE, panelAlphaFactor));
        graphics.fill(frameLeft, frameBottom - 1, frameRight, frameBottom, scaleAlpha(CodTheme.BORDER_SUBTLE, panelAlphaFactor));
        graphics.fill(frameLeft, frameTop, frameLeft + 1, frameBottom, scaleAlpha(CodTheme.BORDER_SUBTLE, panelAlphaFactor));
        graphics.fill(frameRight - 1, frameTop, frameRight, frameBottom, scaleAlpha(CodTheme.BORDER_SUBTLE, panelAlphaFactor));
        graphics.fill(roomListX, listTop - GuiTextHelper.referenceScaled(4), roomListX + roomListWidth,
                listTop - GuiTextHelper.referenceScaled(3), scaleAlpha(CodTheme.DIVIDER, panelAlphaFactor));

        GuiTextHelper.drawReferenceString(
                graphics,
                mc.font,
                Component.translatable("screen.codpattern.tdm_room.available_rooms"),
                roomListX + panelPadding,
                titleY,
                scaleAlpha(CodTheme.TEXT_PRIMARY, panelAlphaFactor),
                false);

        List<String> roomNames = orderedRoomNames(rooms, joinedRoom);
        List<RoomHitbox> roomHitboxes = new ArrayList<>();
        List<ActionHitbox> actionHitboxes = new ArrayList<>();
        highlightProgress.keySet().retainAll(roomNames);
        roomEnteredAtMs.keySet().retainAll(roomNames);

        if (roomNames.isEmpty()) {
            int emptyY = listTop + GuiTextHelper.referenceScaled(8);
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    Component.translatable("screen.codpattern.tdm_room.no_rooms"),
                    roomListX + panelPadding,
                    emptyY,
                    scaleAlpha(CodTheme.TEXT_SECONDARY, panelAlphaFactor),
                    false);
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    Component.translatable("screen.codpattern.room.create_hint"),
                    roomListX + panelPadding,
                    emptyY + referenceLineHeight + GuiTextHelper.referenceScaled(3),
                    scaleAlpha(CodTheme.TEXT_DIM, panelAlphaFactor),
                    false);
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    Component.translatable("screen.codpattern.room.create_command"),
                    roomListX + panelPadding,
                    emptyY + (referenceLineHeight + GuiTextHelper.referenceScaled(3)) * 2,
                    scaleAlpha(CodTheme.TEXT_DIM, panelAlphaFactor),
                    false);
            return new RenderResult(roomNames, roomHitboxes, actionHitboxes);
        }

        int visibleCount = Math.max(1, listHeight / roomItemHeight);
        int maxScrollOffset = Math.max(0, roomNames.size() - visibleCount);
        int effectiveOffset = clamp(scrollOffset, 0, maxScrollOffset);
        int endIndex = Math.min(roomNames.size(), effectiveOffset + visibleCount);

        for (int index = effectiveOffset; index < endIndex; index++) {
            int visibleIndex = index - effectiveOffset;
            int y = listTop + visibleIndex * roomItemHeight;
            String roomKey = roomNames.get(index);
            TdmRoomData room = rooms.get(roomKey);
            if (room == null) {
                continue;
            }

            roomHitboxes.add(new RoomHitbox(roomKey, roomListX, y, roomListWidth, roomItemHeight));

            boolean hovered = mouseX >= roomListX && mouseX <= roomListX + roomListWidth
                    && mouseY >= y && mouseY < y + roomItemHeight;
            boolean selected = roomKey.equals(selectedRoom);
            boolean joined = roomKey.equals(joinedRoom);

            float targetHighlight = joined ? 1.0f : (selected ? 0.85f : (hovered ? 0.45f : 0.0f));
            float currentHighlight = approach(
                    highlightProgress.getOrDefault(roomKey, 0.0f),
                    targetHighlight,
                    HIGHLIGHT_APPROACH_STEP);
            highlightProgress.put(roomKey, currentHighlight);

            int rowBottom = y + roomItemHeight - 1;
            int baseTop = withAlpha(CodTheme.CARD_BG_TOP, 74 + (int) (currentHighlight * 84.0f));
            int baseBottom = withAlpha(CodTheme.CARD_BG_BOTTOM, 82 + (int) (currentHighlight * 96.0f));
            graphics.fillGradient(roomListX, y, roomListX + roomListWidth, rowBottom,
                    scaleAlpha(baseTop, panelAlphaFactor),
                    scaleAlpha(baseBottom, panelAlphaFactor));

            if (hovered || joined) {
                int hoverTop = withAlpha(CodTheme.HOVER_BG_TOP, hovered ? 96 : 60);
                int hoverBottom = withAlpha(CodTheme.HOVER_BG_BOTTOM, hovered ? 112 : 74);
                graphics.fillGradient(roomListX, y, roomListX + roomListWidth, rowBottom,
                        scaleAlpha(hoverTop, panelAlphaFactor),
                        scaleAlpha(hoverBottom, panelAlphaFactor));
                graphics.fill(roomListX, y, roomListX + roomListWidth, y + 1,
                        scaleAlpha(withAlpha(CodTheme.HOVER_BORDER, hovered ? 142 : 96), panelAlphaFactor));
                graphics.fill(roomListX, rowBottom - 1, roomListX + roomListWidth, rowBottom,
                        scaleAlpha(withAlpha(CodTheme.HOVER_BORDER_SEMI, hovered ? 128 : 92), panelAlphaFactor));
            }

            int edgeColor = joined ? CodTheme.HOVER_BORDER : (selected ? CodTheme.SELECTED_BORDER : 0);
            if (edgeColor != 0) {
                graphics.fill(roomListX, y, roomListX + GuiTextHelper.referenceScaled(2),
                        rowBottom,
                        scaleAlpha(edgeColor, panelAlphaFactor));
            }

            long enteredAt = roomEnteredAtMs.getOrDefault(roomKey, 0L);
            if (enteredAt > 0L) {
                long elapsed = nowMs - enteredAt;
                if (elapsed >= NEW_ROOM_PULSE_MS) {
                    roomEnteredAtMs.remove(roomKey);
                } else {
                    float pulse = 1.0f - (elapsed / (float) NEW_ROOM_PULSE_MS);
                    int pulseColor = withAlpha(CodTheme.HOVER_BORDER, Math.max(0, (int) (95.0f * pulse)));
                    graphics.fill(roomListX, y, roomListX + roomListWidth, rowBottom,
                            scaleAlpha(pulseColor, panelAlphaFactor));
                }
            }

            ActionType actionType = resolveActionType(joinedRoom, roomKey, hovered);
            boolean showAction = actionType != null;
            int contentLeft = roomListX + panelPadding;
            int contentRight = roomListX + roomListWidth - panelPadding;
            int actionGap = GuiTextHelper.referenceScaled(4);
            int actionSize = Math.max(GuiTextHelper.referenceScaled(12), roomItemHeight - GuiTextHelper.referenceScaled(14));
            int actionX = contentRight - actionSize;
            int actionY = y + Math.max(0, (roomItemHeight - actionSize) / 2);
            boolean actionHovered = showAction
                    && mouseX >= actionX
                    && mouseX <= actionX + actionSize
                    && mouseY >= actionY
                    && mouseY <= actionY + actionSize;
            int textRight = showAction ? actionX - actionGap : contentRight;

            String statusIcon = TdmRoomTextFormatter.statusIcon(room.state);
            String roomText = statusIcon + " " + room.mapName;
            if (!room.hasMatchEndTeleportPoint) {
                roomText += " §c!";
            }
            int roomTextColor = joined
                    ? CodTheme.TEXT_HOVER
                    : (selected ? CodTheme.SELECTED_TEXT : CodTheme.TEXT_PRIMARY);

            int primaryTextY = y + GuiTextHelper.referenceScaled(BASE_ROW_TOP_PADDING);
            int roomTextMaxWidth = Math.max(
                    GuiTextHelper.referenceScaled(34),
                    textRight - contentLeft);
            ModeDescriptor descriptor = GameModeRegistry.getOrDefault(room.gameType);
            String modeText = Component.translatable(descriptor.displayNameKey()).getString();
            GuiTextHelper.drawReferenceEllipsizedString(
                    graphics,
                    mc.font,
                    roomText,
                    contentLeft,
                    primaryTextY,
                    roomTextMaxWidth,
                    scaleAlpha(roomTextColor, panelAlphaFactor),
                    false);

            String playerText = room.playerCount + "/" + room.maxPlayers;
            String scoreText = TdmRoomTextFormatter.teamScoreText(room.teamScores);
            int playerTextY = primaryTextY + referenceLineHeight + GuiTextHelper.referenceScaled(BASE_ROW_SECONDARY_GAP);
            GuiTextHelper.drawReferenceEllipsizedString(
                    graphics,
                    mc.font,
                    modeText + "  " + playerText,
                    contentLeft,
                    playerTextY,
                    Math.max(GuiTextHelper.referenceScaled(24), textRight - contentLeft),
                    scaleAlpha(CodTheme.TEXT_SECONDARY, panelAlphaFactor),
                    false);
            GuiTextHelper.drawReferenceEllipsizedString(
                    graphics,
                    mc.font,
                    scoreText,
                    contentLeft,
                    playerTextY + referenceLineHeight,
                    Math.max(GuiTextHelper.referenceScaled(34), textRight - contentLeft),
                    scaleAlpha(0xFFAFAFAF, panelAlphaFactor),
                    false);

            int barThickness = Math.max(1, GuiTextHelper.referenceScaled(2));
            int barLeft = contentLeft;
            int barRight = Math.max(barLeft + 1, textRight);
            int barY = y + roomItemHeight - GuiTextHelper.referenceScaled(BASE_ROW_BAR_BOTTOM_PADDING);
            int barWidth = Math.max(1, barRight - barLeft);
            int fillWidth = Math.min(barWidth,
                    (int) (barWidth * (room.playerCount / (float) Math.max(1, room.maxPlayers))));
            graphics.fill(barLeft, barY, barRight, barY + barThickness,
                    scaleAlpha(0x26FFFFFF, panelAlphaFactor));
            if (fillWidth > 0) {
                graphics.fill(barLeft, barY, barLeft + fillWidth, barY + barThickness,
                        scaleAlpha(withAlpha(joined ? CodTheme.HOVER_BORDER : CodTheme.SELECTED_BORDER, 160), panelAlphaFactor));
            }

            if (showAction) {
                boolean enabled = !hasPendingAction;
                actionHitboxes.add(new ActionHitbox(roomKey, actionType, actionX, actionY, actionSize, actionSize, enabled));
                renderActionButton(graphics, mc, actionX, actionY, actionSize, actionType, actionHovered, enabled,
                        leavePending && actionType == ActionType.LEAVE, panelAlphaFactor);
            }
        }

        if (roomNames.size() > visibleCount) {
            int trackX = roomListX + roomListWidth - GuiTextHelper.referenceScaled(2);
            graphics.fill(trackX, listTop, trackX + GuiTextHelper.referenceScaled(2), listBottom,
                    scaleAlpha(0x22FFFFFF, panelAlphaFactor));

            int thumbHeight = Math.max(GuiTextHelper.referenceScaled(12),
                    (int) (listHeight * (visibleCount / (float) roomNames.size())));
            int thumbOffsetMax = Math.max(1, listHeight - thumbHeight);
            int thumbY = listTop + (int) (thumbOffsetMax * (effectiveOffset / (float) maxScrollOffset));
            graphics.fill(trackX, thumbY, trackX + GuiTextHelper.referenceScaled(2), thumbY + thumbHeight,
                    scaleAlpha(CodTheme.SELECTED_BORDER, panelAlphaFactor));
        }

        GuiTextHelper.drawReferenceString(
                graphics,
                mc.font,
                Component.translatable("screen.codpattern.tdm_room.scroll_hint"),
                roomListX + panelPadding,
                footerY,
                scaleAlpha(CodTheme.TEXT_DIM, panelAlphaFactor),
                false);

        return new RenderResult(roomNames, roomHitboxes, actionHitboxes);
    }

    private static ActionType resolveActionType(String joinedRoom, String mapName, boolean hovered) {
        if (mapName.equals(joinedRoom)) {
            return ActionType.LEAVE;
        }
        if (!hovered) {
            return null;
        }
        if (joinedRoom == null || joinedRoom.isBlank()) {
            return ActionType.JOIN;
        }
        return ActionType.SWITCH;
    }

    private static void renderActionButton(
            GuiGraphics graphics,
            Minecraft mc,
            int x,
            int y,
            int size,
            ActionType type,
            boolean hovered,
            boolean enabled,
            boolean leavePending,
            float panelAlphaFactor) {
        int accentColor = switch (type) {
            case JOIN -> CodTheme.HOVER_BORDER;
            case LEAVE -> CodTheme.TEXT_DANGER;
            case SWITCH -> CodTheme.SELECTED_BORDER;
        };

        int bgTop = enabled ? withAlpha(CodTheme.CARD_BG_TOP, hovered ? 224 : 208) : withAlpha(CodTheme.DISABLED_BG, 214);
        int bgBottom = enabled ? withAlpha(CodTheme.CARD_BG_BOTTOM, hovered ? 238 : 222) : withAlpha(0xFF1A1A1A, 222);
        graphics.fillGradient(x, y, x + size, y + size,
                scaleAlpha(bgTop, panelAlphaFactor),
                scaleAlpha(bgBottom, panelAlphaFactor));

        if (enabled) {
            int overlayTop = withAlpha(CodTheme.HOVER_BG_TOP, hovered ? 96 : 52);
            int overlayBottom = withAlpha(CodTheme.HOVER_BG_BOTTOM, hovered ? 118 : 68);
            graphics.fillGradient(x, y, x + size, y + size,
                    scaleAlpha(overlayTop, panelAlphaFactor),
                    scaleAlpha(overlayBottom, panelAlphaFactor));
        }

        int borderColor = enabled
                ? withAlpha(accentColor, leavePending ? 255 : (hovered ? 228 : 176))
                : withAlpha(CodTheme.BORDER_SUBTLE, 180);
        graphics.fill(x, y, x + size, y + 1, scaleAlpha(borderColor, panelAlphaFactor));
        graphics.fill(x, y + size - 1, x + size, y + size, scaleAlpha(borderColor, panelAlphaFactor));
        graphics.fill(x, y, x + 1, y + size, scaleAlpha(borderColor, panelAlphaFactor));
        graphics.fill(x + size - 1, y, x + size, y + size, scaleAlpha(borderColor, panelAlphaFactor));

        if (leavePending) {
            int pulseColor = scaleAlpha(withAlpha(CodTheme.SELECTED_BORDER, hovered ? 168 : 132), panelAlphaFactor);
            graphics.fill(x + 1, y + 1, x + size - 1, y + 2, pulseColor);
        }

        drawActionGlyph(graphics, mc, x, y, size, type, enabled ? accentColor : CodTheme.DISABLED_TEXT, panelAlphaFactor);
    }

    private static void drawActionGlyph(
            GuiGraphics graphics,
            Minecraft mc,
            int x,
            int y,
            int size,
            ActionType type,
            int color,
            float panelAlphaFactor) {
        int scaledColor = scaleAlpha(color, panelAlphaFactor);
        if (type == ActionType.JOIN || type == ActionType.LEAVE) {
            int blockSize = Math.max(GuiTextHelper.referenceScaled(4), size / 3);
            int blockX = x + (size - blockSize) / 2;
            int blockY = y + (size - blockSize) / 2;
            graphics.fill(blockX, blockY, blockX + blockSize, blockY + blockSize, scaledColor);
            return;
        }

        String glyph = ">";
        int glyphY = y + (size - GuiTextHelper.referenceLineHeight(mc.font)) / 2;
        GuiTextHelper.drawReferenceCenteredString(
                graphics,
                mc.font,
                glyph,
                x + size / 2.0f,
                glyphY,
                scaledColor,
                false);
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
            String leftRoomKey = left.getKey();
            String rightRoomKey = right.getKey();
            if (leftRoomKey.equals(joinedRoom) && !rightRoomKey.equals(joinedRoom)) {
                return -1;
            }
            if (rightRoomKey.equals(joinedRoom) && !leftRoomKey.equals(joinedRoom)) {
                return 1;
            }

            TdmRoomData leftData = left.getValue();
            TdmRoomData rightData = right.getValue();
            int modeCompare = leftData.gameType.compareToIgnoreCase(rightData.gameType);
            if (modeCompare != 0) {
                return modeCompare;
            }
            int stateCompare = Integer.compare(statePriority(leftData.state), statePriority(rightData.state));
            if (stateCompare != 0) {
                return stateCompare;
            }

            int playerCompare = Integer.compare(rightData.playerCount, leftData.playerCount);
            if (playerCompare != 0) {
                return playerCompare;
            }

            return leftData.mapName.compareToIgnoreCase(rightData.mapName);
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

    private static int frameInset() {
        return GuiTextHelper.referenceScaled(BASE_FRAME_INSET);
    }

    private static int panelPadding() {
        return GuiTextHelper.referenceScaled(BASE_PANEL_PADDING);
    }

    private static int headerSectionHeight() {
        return GuiTextHelper.referenceScaled(BASE_HEADER_SECTION_HEIGHT);
    }

    private static int footerSectionHeight() {
        return GuiTextHelper.referenceScaled(BASE_FOOTER_SECTION_HEIGHT);
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
