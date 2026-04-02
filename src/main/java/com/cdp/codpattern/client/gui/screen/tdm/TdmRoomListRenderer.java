package com.cdp.codpattern.client.gui.screen.tdm;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.model.ModeDescriptor;
import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class TdmRoomListRenderer {
    private static final float HIGHLIGHT_APPROACH_STEP = 0.44f;
    private static final long NEW_ROOM_PULSE_MS = 550L;
    private static final float MAP_TITLE_SCALE = 1.28f;
    private static final float MODE_INFO_SCALE = 1.04f;
    private static final int FRONTLINE_MODE_COLOR = 0xFF62F08A;
    private static final int TACTICAL_MODE_COLOR = 0xFF5FC7C3;
    private static final int UNKNOWN_MODE_COLOR = 0xFFB8C2CC;

    private static final int BASE_FRAME_INSET = 5;
    private static final int BASE_PANEL_PADDING = 8;
    private static final int BASE_HEADER_SECTION_HEIGHT = 8;
    private static final int BASE_FOOTER_SECTION_HEIGHT = 20;
    private static final int BASE_ROW_TOP_PADDING = 4;
    private static final int BASE_ROW_SECONDARY_GAP = 1;
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
            LobbySummaryState lobbySummaryState,
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
            String pendingSwitchTargetRoom,
            float panelAlphaFactor) {
        Map<String, TdmRoomData> rooms = lobbySummaryState.rooms();
        int referenceLineHeight = GuiTextHelper.referenceLineHeight(mc.font);
        int frameInset = frameInset();
        int panelPadding = panelPadding();
        int frameLeft = roomListX - frameInset;
        int frameTop = roomListY - frameInset;
        int frameRight = roomListX + roomListWidth + frameInset;
        int frameBottom = roomListY + roomListHeight + frameInset;
        int headerHeight = headerSectionHeight();
        int footerHeight = footerSectionHeight();
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
        List<String> roomNames = orderedRoomNames(rooms, joinedRoom);
        List<RoomHitbox> roomHitboxes = new ArrayList<>();
        List<ActionHitbox> actionHitboxes = new ArrayList<>();
        highlightProgress.keySet().retainAll(roomNames);
        roomEnteredAtMs.keySet().retainAll(roomNames);

        if (lobbySummaryState.isLoading() && !lobbySummaryState.hasLoaded()) {
            int emptyY = listTop + GuiTextHelper.referenceScaled(8);
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    Component.translatable("screen.codpattern.tdm_room.loading"),
                    roomListX + panelPadding,
                    emptyY,
                    scaleAlpha(CodTheme.TEXT_SECONDARY, panelAlphaFactor),
                    false);
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    Component.translatable("screen.codpattern.tdm_room.loading_hint"),
                    roomListX + panelPadding,
                    emptyY + referenceLineHeight + GuiTextHelper.referenceScaled(3),
                    scaleAlpha(CodTheme.TEXT_DIM, panelAlphaFactor),
                    false);
            return new RenderResult(roomNames, roomHitboxes, actionHitboxes);
        }

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

            ActionType actionType = resolveActionType(joinedRoom, roomKey, hovered, selected);
            boolean showAction = actionType != null;
            int contentLeft = roomListX + panelPadding;
            int contentRight = roomListX + roomListWidth - panelPadding;
            int actionGap = GuiTextHelper.referenceScaled(4);
            String actionLabel = showAction ? actionLabel(actionType) : "";
            int actionHeight = Math.max(GuiTextHelper.referenceScaled(12), roomItemHeight - GuiTextHelper.referenceScaled(16));
            int actionWidth = showAction
                    ? Math.max(
                            GuiTextHelper.referenceScaled(28),
                            GuiTextHelper.referenceWidth(mc.font, actionLabel) + GuiTextHelper.referenceScaled(10))
                    : 0;
            int actionX = contentRight - actionWidth;
            int actionY = y + Math.max(0, (roomItemHeight - actionHeight) / 2);
            boolean actionHovered = showAction
                    && mouseX >= actionX
                    && mouseX <= actionX + actionWidth
                    && mouseY >= actionY
                    && mouseY <= actionY + actionHeight;
            int textRight = showAction ? actionX - actionGap : contentRight;

            String badgeText = null;
            int badgeAccent = 0;
            int badgeTextColor = 0xFF081008;
            if (joined) {
                badgeText = Component.translatable("screen.codpattern.tdm_room.badge.current").getString();
                badgeAccent = CodTheme.HOVER_BORDER;
            } else if (selected) {
                badgeText = Component.translatable("screen.codpattern.tdm_room.badge.selected").getString();
                badgeAccent = CodTheme.SELECTED_BORDER;
                badgeTextColor = 0xFF08121F;
            }
            if (badgeText != null) {
                int badgePaddingX = GuiTextHelper.referenceScaled(4);
                int badgePaddingY = GuiTextHelper.referenceScaled(1);
                int badgeHeight = referenceLineHeight + badgePaddingY * 2;
                int badgeWidth = GuiTextHelper.referenceWidth(mc.font, badgeText) + badgePaddingX * 2;
                int badgeRight = showAction ? actionX - actionGap : contentRight;
                int badgeX = Math.max(contentLeft + GuiTextHelper.referenceScaled(44), badgeRight - badgeWidth);
                int badgeY = y + GuiTextHelper.referenceScaled(3);

                graphics.fillGradient(
                        badgeX,
                        badgeY,
                        badgeRight,
                        badgeY + badgeHeight,
                        scaleAlpha(withAlpha(badgeAccent, hovered ? 116 : 92), panelAlphaFactor),
                        scaleAlpha(withAlpha(CodTheme.HOVER_BG_BOTTOM, hovered ? 168 : 148), panelAlphaFactor));
                graphics.fill(
                        badgeX,
                        badgeY + badgeHeight - 1,
                        badgeRight,
                        badgeY + badgeHeight,
                        scaleAlpha(withAlpha(badgeAccent, 210), panelAlphaFactor));
                GuiTextHelper.drawReferenceCenteredEllipsizedString(
                        graphics,
                        mc.font,
                        badgeText,
                        badgeX + (badgeRight - badgeX) / 2,
                        badgeY + badgePaddingY,
                        Math.max(GuiTextHelper.referenceScaled(22), badgeRight - badgeX - badgePaddingX * 2),
                        scaleAlpha(badgeTextColor, panelAlphaFactor),
                        false);
                textRight = Math.min(textRight, badgeX - actionGap);
            }

            int roomTextColor = joined
                    ? CodTheme.TEXT_HOVER
                    : (selected ? CodTheme.SELECTED_TEXT : CodTheme.TEXT_PRIMARY);

            int primaryTextY = y + GuiTextHelper.referenceScaled(BASE_ROW_TOP_PADDING);
            int roomTextMaxWidth = Math.max(
                    GuiTextHelper.referenceScaled(34),
                    textRight - contentLeft);
            ModeDescriptor descriptor = GameModeRegistry.getOrDefault(room.gameType);
            MutableComponent roomTitle = buildRoomTitle(room);
            MutableComponent modeInfo = buildModeInfo(descriptor, room, joined, selected);
            GuiTextHelper.drawReferenceScaledEllipsizedString(
                    graphics,
                    mc.font,
                    roomTitle,
                    contentLeft,
                    primaryTextY,
                    roomTextMaxWidth,
                    MAP_TITLE_SCALE,
                    scaleAlpha(roomTextColor, panelAlphaFactor),
                    false);

            int titleLineHeight = GuiTextHelper.referenceLineHeight(mc.font, MAP_TITLE_SCALE);
            int playerTextY = primaryTextY + titleLineHeight + GuiTextHelper.referenceScaled(BASE_ROW_SECONDARY_GAP);
            GuiTextHelper.drawReferenceScaledEllipsizedString(
                    graphics,
                    mc.font,
                    modeInfo,
                    contentLeft,
                    playerTextY,
                    Math.max(GuiTextHelper.referenceScaled(34), textRight - contentLeft),
                    MODE_INFO_SCALE,
                    scaleAlpha(CodTheme.TEXT_SECONDARY, panelAlphaFactor),
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
                actionHitboxes.add(new ActionHitbox(roomKey, actionType, actionX, actionY, actionWidth, actionHeight, enabled));
                boolean confirmPending = (leavePending && actionType == ActionType.LEAVE)
                        || (actionType == ActionType.SWITCH
                        && pendingSwitchTargetRoom != null
                        && pendingSwitchTargetRoom.equals(roomKey));
                renderActionButton(graphics, mc, actionX, actionY, actionWidth, actionHeight, actionType, actionLabel,
                        actionHovered, enabled, confirmPending, panelAlphaFactor);
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

        Component footerText = lobbySummaryState.isStale(nowMs)
                ? Component.translatable(
                        "screen.codpattern.tdm_room.stale_hint",
                        Math.max(1, lobbySummaryState.secondsSinceLastUpdate(nowMs)))
                : Component.translatable("screen.codpattern.tdm_room.scroll_hint");
        int footerColor = lobbySummaryState.isStale(nowMs) ? CodTheme.TEXT_DANGER : CodTheme.TEXT_DIM;
        GuiTextHelper.drawReferenceString(
                graphics,
                mc.font,
                footerText,
                roomListX + panelPadding,
                footerY,
                scaleAlpha(footerColor, panelAlphaFactor),
                false);

        return new RenderResult(roomNames, roomHitboxes, actionHitboxes);
    }

    private static ActionType resolveActionType(String joinedRoom, String mapName, boolean hovered, boolean selected) {
        if (mapName.equals(joinedRoom)) {
            return ActionType.LEAVE;
        }
        if (!hovered && !selected) {
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
            int width,
            int height,
            ActionType type,
            String label,
            boolean hovered,
            boolean enabled,
            boolean confirmPending,
            float panelAlphaFactor) {
        int accentColor = switch (type) {
            case JOIN -> CodTheme.HOVER_BORDER;
            case LEAVE -> CodTheme.TEXT_DANGER;
            case SWITCH -> CodTheme.SELECTED_BORDER;
        };

        int bgTop = enabled ? withAlpha(CodTheme.CARD_BG_TOP, hovered ? 224 : 208) : withAlpha(CodTheme.DISABLED_BG, 214);
        int bgBottom = enabled ? withAlpha(CodTheme.CARD_BG_BOTTOM, hovered ? 238 : 222) : withAlpha(0xFF1A1A1A, 222);
        graphics.fillGradient(x, y, x + width, y + height,
                scaleAlpha(bgTop, panelAlphaFactor),
                scaleAlpha(bgBottom, panelAlphaFactor));

        if (enabled) {
            int overlayTop = withAlpha(CodTheme.HOVER_BG_TOP, hovered ? 96 : 52);
            int overlayBottom = withAlpha(CodTheme.HOVER_BG_BOTTOM, hovered ? 118 : 68);
            graphics.fillGradient(x, y, x + width, y + height,
                    scaleAlpha(overlayTop, panelAlphaFactor),
                    scaleAlpha(overlayBottom, panelAlphaFactor));
        }

        int borderColor = enabled
                ? withAlpha(accentColor, confirmPending ? 255 : (hovered ? 228 : 176))
                : withAlpha(CodTheme.BORDER_SUBTLE, 180);
        graphics.fill(x, y, x + width, y + 1, scaleAlpha(borderColor, panelAlphaFactor));
        graphics.fill(x, y + height - 1, x + width, y + height, scaleAlpha(borderColor, panelAlphaFactor));
        graphics.fill(x, y, x + 1, y + height, scaleAlpha(borderColor, panelAlphaFactor));
        graphics.fill(x + width - 1, y, x + width, y + height, scaleAlpha(borderColor, panelAlphaFactor));
        graphics.fill(x, y, x + 2, y + height, scaleAlpha(withAlpha(accentColor, enabled ? 220 : 110), panelAlphaFactor));

        if (confirmPending) {
            int pulseColor = scaleAlpha(withAlpha(accentColor, hovered ? 168 : 132), panelAlphaFactor);
            graphics.fill(x + 1, y + 1, x + width - 1, y + 2, pulseColor);
        }

        drawActionGlyph(
                graphics,
                mc,
                x,
                y,
                width,
                height,
                label,
                enabled ? accentColor : CodTheme.DISABLED_TEXT,
                panelAlphaFactor);
    }

    private static void drawActionGlyph(
            GuiGraphics graphics,
            Minecraft mc,
            int x,
            int y,
            int width,
            int height,
            String label,
            int color,
            float panelAlphaFactor) {
        int scaledColor = scaleAlpha(color, panelAlphaFactor);
        int glyphY = y + (height - GuiTextHelper.referenceLineHeight(mc.font)) / 2;
        GuiTextHelper.drawReferenceCenteredString(
                graphics,
                mc.font,
                label,
                x + width / 2.0f,
                glyphY,
                scaledColor,
                false);
    }

    private static String actionLabel(ActionType type) {
        return Component.translatable(switch (type) {
            case JOIN -> "screen.codpattern.tdm_room.action.join";
            case LEAVE -> "screen.codpattern.tdm_room.action.leave";
            case SWITCH -> "screen.codpattern.tdm_room.action.switch";
        }).getString();
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

    private static MutableComponent buildRoomTitle(TdmRoomData room) {
        MutableComponent title = Component.empty()
                .append(Component.literal("\u25cf ").withStyle(style -> style.withColor(statusColor(room.state))))
                .append(Component.literal(room.mapName == null ? "" : room.mapName));
        if (!room.hasMatchEndTeleportPoint) {
            title.append(Component.literal(" !").withStyle(style -> style.withColor(CodTheme.TEXT_DANGER & 0x00FFFFFF)));
        }
        return title;
    }

    private static MutableComponent buildModeInfo(
            ModeDescriptor descriptor,
            TdmRoomData room,
            boolean joined,
            boolean selected
    ) {
        String playerText = room.playerCount + "/" + room.maxPlayers;
        String phaseText = TdmRoomTextFormatter.phaseStatusText(room.state, room.remainingTimeTicks);
        int statsColor = joined ? 0xFFE3EDF6 : (selected ? 0xFFD5E5FB : 0xFFB7C2CD);
        int separatorColor = joined ? 0xFF8FA7B4 : 0xFF70808D;

        return Component.empty()
                .append(Component.literal(Component.translatable(descriptor.displayNameKey()).getString())
                        .withStyle(style -> style.withItalic(true).withColor(modeAccentColor(room.gameType))))
                .append(Component.literal("  ").withStyle(style -> style.withColor(separatorColor)))
                .append(Component.literal(playerText).withStyle(style -> style.withColor(statsColor)))
                .append(Component.literal("  ").withStyle(style -> style.withColor(separatorColor)))
                .append(Component.literal(phaseText).withStyle(style -> style.withColor(statusColor(room.state))));
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

    private static int statusColor(String state) {
        return switch (state) {
            case "WAITING" -> 0xFF69D88D;
            case "COUNTDOWN", "WARMUP" -> 0xFFF1C15B;
            case "PLAYING" -> 0xFFF16666;
            case "ENDED" -> 0xFFADB8C6;
            default -> 0xFFF2F5F8;
        };
    }

    private static int modeAccentColor(String gameType) {
        if (gameType == null || gameType.isBlank()) {
            return UNKNOWN_MODE_COLOR;
        }
        String normalized = gameType.toLowerCase();
        if (normalized.contains("frontline")) {
            return FRONTLINE_MODE_COLOR;
        }
        if (normalized.contains("teamdeathmatch") || normalized.contains("tactical")) {
            return TACTICAL_MODE_COLOR;
        }
        return UNKNOWN_MODE_COLOR;
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
