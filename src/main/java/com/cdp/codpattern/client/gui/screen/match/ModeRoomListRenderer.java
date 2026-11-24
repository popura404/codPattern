package com.cdp.codpattern.client.gui.screen.match;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.model.ModeCapability;
import com.cdp.codpattern.app.match.model.ModeDescriptor;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FastColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ModeRoomListRenderer {
    private static final float TITLE_SCALE = 1.32f;
    private static final int TEXT = 0xFFF3F6F4;
    private static final int SECONDARY = 0xFFBDC9C8;
    private static final int MUTED = 0xFF9EAEB0;
    private static final int WARNING = 0xFFFFC68C;
    private static final int DARK_INK = 0xFF111414;
    private static final int DARK_SECONDARY = 0xFF25302D;
    private static final int GAP = 3;
    private static final int PADDING = 9;
    private static final int FOOTER_HEIGHT = 16;
    private static final int NEW_ROOM_PULSE_MS = 550;

    private Map<String, ModeRoomData> lastSnapshot;
    private String lastJoinedRoom;
    private List<String> roomNames = List.of();
    private int rowHeight;
    private int rowStride;
    private final Map<String, LobbyModeEffects.Motion> rowMotion = new HashMap<>();

    public enum ActionType {JOIN, LEAVE, SWITCH}

    public record ActionHitbox(String roomName, ActionType type, int x, int y, int width,
            int height,
            boolean enabled
    ) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    public record RoomHitbox(String roomName, int x, int y, int width, int height
    ) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    public record RenderResult(
            List<String> roomNames,
            List<RoomHitbox> roomHitboxes,
            List<ActionHitbox> actionHitboxes,
            int scrollOffset,
            int maxScrollOffset
    ) {
        public static RenderResult empty() {
            return new RenderResult(List.of(), List.of(), List.of(), 0, 0);
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

    public static int listViewportHeight(int panelHeight) {
        return Math.max(1, panelHeight - scaled(FOOTER_HEIGHT));
    }

    public int scrollToRoom(String roomKey, int scrollOffset, int panelHeight) {
        int index = roomKey == null ? -1 : roomNames.indexOf(roomKey);
        if (index < 0) return scrollOffset;
        int top = index * rowStride;
        int viewport = listViewportHeight(panelHeight);
        int offset = scrollOffset;
        if (rowHeight > viewport && offset >= top && offset + viewport <= top + rowHeight) {
            return offset;
        }
        if (top < offset || rowHeight >= viewport) {
            offset = top;
        } else if (top + rowHeight > offset + viewport) {
            offset = top + rowHeight - viewport;
        }
        return Mth.clamp(offset, 0, Math.max(0, contentHeight() - viewport));
    }

    public RenderResult render(
            GuiGraphics graphics, Minecraft mc, int roomListX, int roomListY, int roomListWidth,
            int roomListHeight, int roomHeaderHeight, LobbySummaryState lobbySummaryState,
            String modeFilterGameType, String selectedRoom, String joinedRoom, int scrollOffset,
            Map<String, Long> roomEnteredAtMs, long nowMs,
            int mouseX, int mouseY, boolean hasPendingAction, boolean leavePending,
            String pendingSwitchTargetRoom, float panelAlphaFactor) {
        Font font = mc.font;
        int listHeight = listViewportHeight(roomListHeight);
        int bottom = roomListY + listHeight;
        int capacityHeight = GuiTextHelper.referenceLineHeight(font) + 2 * scaled(1);
        int firstLineHeight = Math.max(GuiTextHelper.referenceLineHeight(font, TITLE_SCALE), capacityHeight);
        int buttonHeight = Math.max(scaled(16), GuiTextHelper.referenceLineHeight(font) + scaled(4));
        int lineGap = scaled(2);
        int contentRowHeight = firstLineHeight + lineGap + buttonHeight;
        rowHeight = Math.max(contentRowHeight, Math.max(roomHeaderHeight, scaled(40)));
        rowStride = rowHeight + scaled(GAP);
        Map<String, ModeRoomData> rooms = lobbySummaryState.rooms();
        if (lastSnapshot != rooms || !Objects.equals(joinedRoom, lastJoinedRoom)) {
            roomNames = orderedRoomNames(rooms, joinedRoom);
            lastSnapshot = rooms;
            lastJoinedRoom = joinedRoom;
            rowMotion.keySet().retainAll(rooms.keySet());
            roomEnteredAtMs.keySet().retainAll(rooms.keySet());
        }

        if (roomNames.isEmpty()) {
            drawEmptyState(graphics, font, roomListX, roomListY, roomListWidth, roomListHeight, modeFilterGameType,
                    lobbySummaryState.isLoading() && !lobbySummaryState.hasLoaded(), panelAlphaFactor);
            return RenderResult.empty();
        }

        int contentHeight = contentHeight();
        int maxOffset = Math.max(0, contentHeight - listHeight);
        int rowWidth = Math.max(1, roomListWidth - (maxOffset > 0 ? scaled(4) : 0));
        int offset = Mth.clamp(scrollOffset, 0, maxOffset);
        List<RoomHitbox> roomHits = new ArrayList<>();
        List<ActionHitbox> actionHits = new ArrayList<>();
        graphics.enableScissor(roomListX, roomListY, roomListX + rowWidth, bottom);
        int first = (offset + rowStride - rowHeight) / rowStride;
        int last = Math.min(roomNames.size() - 1, (offset + listHeight - 1) / rowStride);
        int titleInset = (rowHeight - contentRowHeight) / 2;
        int buttonInset = titleInset + firstLineHeight + lineGap;
        int metaInset = buttonInset + Math.round((buttonHeight - GuiTextHelper.referenceLabelHeight(font, 1f)) / 2f);
        for (int index = first; index <= last; index++) {
            int y = roomListY + index * rowStride - offset;
            String key = roomNames.get(index);
            int hitTop = Math.max(y, roomListY);
            int hitBottom = Math.min(y + rowHeight, bottom);
            RoomHitbox hit = new RoomHitbox(key, roomListX, hitTop, rowWidth, hitBottom - hitTop);
            roomHits.add(hit);
            ModeRoomData room = rooms.get(key);
            boolean hovered = hit.contains(mouseX, mouseY);
            boolean selected = key.equals(selectedRoom);
            boolean joined = key.equals(joinedRoom);
            int accent = 0xFF000000 | ModePreviewPanel.accentColor(room.gameType);
            LobbyModeEffects.Motion motion = rowMotion.computeIfAbsent(key, ignored -> new LobbyModeEffects.Motion());
            motion.update(hovered || selected, nowMs);
            ScreenRectangle bounds = new ScreenRectangle(roomListX, y, rowWidth, rowHeight);
            LobbyModeEffects.drawFace(graphics, bounds, accent, joined, motion, panelAlphaFactor, scaled(2));
            float pulse = entryPulse(roomEnteredAtMs, key, nowMs);
            if (pulse > 0f) {
                graphics.fill(roomListX, y, bounds.right(), y + scaled(1), fade(accent, panelAlphaFactor * pulse));
            }

            boolean darkInk = joined && usesDarkInk(accent);
            int ink = darkInk ? DARK_INK : FastColor.ARGB32.lerp(motion.highlight(), 0xFFE0E6E3, 0xFFFFFFFF);
            int left = roomListX + scaled(PADDING) + Math.round(scaled(2) * motion.highlight());
            int right = roomListX + rowWidth - scaled(PADDING);
            int titleY = y + titleInset;
            int capacityX = drawCapacity(graphics, font, room, right, titleY, joined ? ink : accent, ink, panelAlphaFactor);
            GuiTextHelper.drawReferenceScaledEllipsizedString(graphics, font, roomTitle(room, darkInk), left, titleY,
                    Math.max(0, capacityX - scaled(5) - left), TITLE_SCALE, fade(ink, panelAlphaFactor), false);

            int metaY = y + metaInset;
            int metaRight = right;
            ActionType action = resolveActionType(joinedRoom, key, hovered, selected);
            if (action != null) {
                boolean confirming = action == ActionType.LEAVE && leavePending
                        || action == ActionType.SWITCH && key.equals(pendingSwitchTargetRoom);
                Component label = actionLabel(action, confirming);
                int buttonWidth = Math.min(Math.max(1, (right - left) / 2),
                        Math.max(scaled(35), GuiTextHelper.referenceWidth(font, label) + scaled(12)));
                int buttonX = right - buttonWidth;
                int buttonY = y + buttonInset;
                int buttonHitTop = Math.max(hitTop, buttonY);
                int buttonHitBottom = Math.min(hitBottom, buttonY + buttonHeight);
                if (buttonHitTop < buttonHitBottom) {
                    ActionHitbox button = new ActionHitbox(key, action, buttonX, buttonHitTop, buttonWidth,
                            buttonHitBottom - buttonHitTop, !hasPendingAction);
                    actionHits.add(button);
                    drawAction(graphics, font, buttonX, buttonY, buttonWidth, buttonHeight, label,
                            button.enabled(), button.contains(mouseX, mouseY), confirming, darkInk, panelAlphaFactor);
                }
                metaRight = buttonX - scaled(5);
            }

            GuiTextHelper.drawReferenceEllipsizedString(graphics, font,
                    metadata(room, joined, selected, modeFilterGameType, darkInk), left, metaY,
                    Math.max(0, metaRight - left), fade(darkInk ? DARK_SECONDARY : SECONDARY, panelAlphaFactor), false);
        }
        graphics.disableScissor();

        if (maxOffset > 0) {
            int trackX = roomListX + roomListWidth - scaled(2);
            int thumb = Math.min(listHeight, Math.max(scaled(7), Math.round(listHeight * (listHeight / (float) contentHeight))));
            int thumbY = roomListY + Math.round((listHeight - thumb) * (offset / (float) maxOffset));
            graphics.fill(trackX, roomListY, roomListX + roomListWidth, bottom, fade(0x800B0F12, panelAlphaFactor));
            graphics.fill(trackX, thumbY, roomListX + roomListWidth, thumbY + thumb, fade(0xDCE1E8E6, panelAlphaFactor));
        }
        boolean stale = lobbySummaryState.isStale(nowMs);
        if (stale || maxOffset > 0) {
            Component footer = stale ? Component.translatable("screen.codpattern.room.stale_hint",
                    lobbySummaryState.secondsSinceLastUpdate(nowMs)) : Component.translatable("screen.codpattern.room.scroll_hint");
            GuiTextHelper.drawReferenceEllipsizedString(graphics, font, footer, roomListX, bottom + scaled(3),
                    roomListWidth, fade(stale ? WARNING : SECONDARY, panelAlphaFactor), true);
        }
        return new RenderResult(roomNames, roomHits, actionHits, offset, maxOffset);
    }

    private int contentHeight() {
        return roomNames.isEmpty() ? 0 : (roomNames.size() - 1) * rowStride + rowHeight;
    }

    private static boolean usesDarkInk(int accent) {
        // Add-on modes may register a dark accent for the joined card.
        return 299 * FastColor.ARGB32.red(accent) + 587 * FastColor.ARGB32.green(accent)
                + 114 * FastColor.ARGB32.blue(accent) >= 128000;
    }

    private static float entryPulse(Map<String, Long> enteredAt, String key, long now) {
        Long start = enteredAt.get(key);
        if (start == null) return 0f;
        float pulse = Mth.clamp(1f - (now - start) / (float) NEW_ROOM_PULSE_MS, 0f, 1f);
        if (pulse == 0f) enteredAt.remove(key);
        return pulse;
    }

    private static Component roomTitle(ModeRoomData room, boolean darkInk) {
        MutableComponent title = Component.empty();
        if (room.hasCapability(ModeCapability.MATCH_END_TELEPORT) && !room.hasMatchEndTeleportPoint) {
            title.append(Component.literal("! ").withStyle(s -> s.withColor(darkInk ? 0x522A14 : WARNING & 0xFFFFFF)));
        }
        return title.append(Component.literal(room.mapName));
    }

    private static int drawCapacity(GuiGraphics graphics, Font font, ModeRoomData room, int right, int y,
                                    int fillColor, int ink, float alpha) {
        String occupancy = room.playerCount + "/" + room.maxPlayers;
        int textWidth = GuiTextHelper.referenceWidth(font, occupancy);
        int width = Math.max(scaled(25), textWidth);
        int left = right - width;
        GuiTextHelper.drawReferenceString(graphics, font, occupancy, right - textWidth, y, fade(ink, alpha), false);
        int meterY = y + GuiTextHelper.referenceLineHeight(font) + scaled(1);
        int filled = Math.round(width * Mth.clamp(room.playerCount / (float) Math.max(1, room.maxPlayers), 0f, 1f));
        graphics.fill(left, meterY, right, meterY + scaled(1), fade(ink, alpha * .28f));
        if (filled > 0) graphics.fill(left, meterY, left + filled, meterY + scaled(1), fade(fillColor, alpha));
        return left;
    }

    private static Component metadata(ModeRoomData room, boolean joined, boolean selected, String modeFilter, boolean darkInk) {
        int status = statusColor(room.state);
        if (darkInk) status = FastColor.ARGB32.lerp(.78f, status, DARK_INK);
        int phaseColor = status & 0xFFFFFF;
        int labelColor = (darkInk ? DARK_INK : TEXT) & 0xFFFFFF;
        int secondaryColor = (darkInk ? DARK_SECONDARY : SECONDARY) & 0xFFFFFF;
        MutableComponent info = Component.literal(ModeRoomTextFormatter.phaseStatusText(room.state, room.remainingTimeTicks))
                .withStyle(s -> s.withColor(phaseColor));
        if (joined || selected) {
            String badge = joined ? "screen.codpattern.room.badge.current" : "screen.codpattern.room.badge.selected";
            info.append("  ").append(Component.translatable(badge).withStyle(s -> s.withColor(labelColor)));
        }
        if (modeFilter == null || modeFilter.isBlank()) {
            ModeDescriptor mode = GameModeRegistry.getOrDefault(room.gameType);
            info.append("  ").append(Component.translatable(mode.displayNameKey()).withStyle(s -> s.withColor(secondaryColor)));
        }
        String metrics = ModeRoomTextFormatter.metricInlineText(room.metrics, 2, false);
        if (!metrics.isBlank()) info.append(Component.literal("  " + metrics)
                .withStyle(s -> s.withColor(darkInk ? secondaryColor : MUTED & 0xFFFFFF)));
        return info;
    }

    private static void drawLines(GuiGraphics graphics, Font font, List<FormattedCharSequence> lines,
                                  int x, int y, int color) {
        float scale = GuiTextHelper.referenceScale();
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0f);
        graphics.pose().scale(scale, scale, 1f);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), 0, i * font.lineHeight, color, false);
        }
        graphics.pose().popPose();
    }

    private static void drawEmptyState(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                                       String modeFilter, boolean loading, float alpha) {
        int padding = scaled(PADDING);
        int textWidth = Math.max(1, (int) ((width - padding * 2) / GuiTextHelper.referenceScale()));
        List<Component> messages = loading
                ? List.of(Component.translatable("screen.codpattern.room.loading"), Component.translatable("screen.codpattern.room.loading_hint"))
                : List.of(Component.translatable("screen.codpattern.room.no_rooms"), Component.translatable("screen.codpattern.room.create_hint"),
                        resolveCreateHint(modeFilter));
        int lineY = y + scaled(8);
        graphics.enableScissor(x, y, x + width, y + height);
        for (int i = 0; i < messages.size(); i++) {
            String cleanText = ChatFormatting.stripFormatting(messages.get(i).getString());
            List<FormattedCharSequence> lines = font.split(Component.literal(cleanText), textWidth);
            int lineHeight = Mth.ceil(lines.size() * font.lineHeight * GuiTextHelper.referenceScale());
            int sectionBottom = lineY + lineHeight + scaled(i == messages.size() - 1 ? 8 : 4);
            graphics.fill(x, i == 0 ? y : lineY, x + width, sectionBottom, fade(0xDC101719, alpha));
            drawLines(graphics, font, lines, x + padding, lineY, fade(i == 0 ? TEXT : SECONDARY, alpha));
            lineY = sectionBottom;
        }
        graphics.disableScissor();
    }

    private static ActionType resolveActionType(String joinedRoom, String room, boolean hovered, boolean selected) {
        if (room.equals(joinedRoom)) return ActionType.LEAVE;
        if (!hovered && !selected) return null;
        return joinedRoom == null || joinedRoom.isBlank() ? ActionType.JOIN : ActionType.SWITCH;
    }

    private static Component actionLabel(ActionType type, boolean confirming) {
        if (confirming) return Component.translatable("gui.cancel");
        return Component.translatable(switch (type) {
            case JOIN -> "screen.codpattern.room.action.join";
            case LEAVE -> "screen.codpattern.room.action.leave";
            case SWITCH -> "screen.codpattern.room.action.switch";
        });
    }

    private static void drawAction(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                                   Component label, boolean enabled, boolean hovered, boolean confirming,
                                   boolean darkInk, float alpha) {
        boolean focused = enabled && (hovered || confirming);
        int surface = focused ? (confirming ? WARNING : TEXT) : darkInk ? 0x24080F0E : 0x50A2B6B8;
        int ink = focused || darkInk ? DARK_INK : enabled ? TEXT : MUTED;
        if (!enabled) ink = fade(ink, .65f);
        graphics.fill(x, y, x + width, y + height, fade(surface, alpha));
        GuiTextHelper.drawReferenceButtonLabel(graphics, font, label, x, y, width, height,
                Math.max(1, width - scaled(4)), fade(ink, alpha));
    }

    public static List<String> orderedRoomNames(Map<String, ModeRoomData> rooms, String joinedRoom) {
        List<Map.Entry<String, ModeRoomData>> ordered = new ArrayList<>(rooms.entrySet());
        ordered.sort(roomComparator(joinedRoom));

        List<String> names = new ArrayList<>(ordered.size());
        for (Map.Entry<String, ModeRoomData> entry : ordered) {
            names.add(entry.getKey());
        }
        return List.copyOf(names);
    }

    private static Comparator<Map.Entry<String, ModeRoomData>> roomComparator(String joinedRoom) {
        return (left, right) -> {
            String leftRoomKey = left.getKey();
            String rightRoomKey = right.getKey();
            if (leftRoomKey.equals(joinedRoom) && !rightRoomKey.equals(joinedRoom)) {
                return -1;
            }
            if (rightRoomKey.equals(joinedRoom) && !leftRoomKey.equals(joinedRoom)) {
                return 1;
            }

            ModeRoomData leftData = left.getValue();
            ModeRoomData rightData = right.getValue();
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

            int mapCompare = leftData.mapName.compareToIgnoreCase(rightData.mapName);
            return mapCompare != 0 ? mapCompare : leftRoomKey.compareTo(rightRoomKey);
        };
    }

    private static int statePriority(String state) {
        if (state == null) {
            return 5;
        }
        return switch (state) {
            case "PLAYING", "WAVE_ACTIVE" -> 0;
            case "WARMUP", "INTERMISSION" -> 1;
            case "COUNTDOWN", "START_VOTE", "OPENING_COUNTDOWN" -> 2;
            case "WAITING" -> 3;
            case "ENDED", "VICTORY", "FAILED", "ENDING" -> 4;
            default -> 5;
        };
    }

    private static int statusColor(String state) {
        if (state == null) return SECONDARY & 0xFFFFFF;
        return switch (state) {
            case "WAITING", "VICTORY" -> 0x9BD0B4;
            case "COUNTDOWN", "WARMUP", "START_VOTE", "OPENING_COUNTDOWN", "INTERMISSION" -> 0xF1C783;
            case "PLAYING", "WAVE_ACTIVE" -> 0xF6A492;
            default -> SECONDARY & 0xFFFFFF;
        };
    }

    private static Component resolveCreateHint(String modeFilterGameType) {
        if (modeFilterGameType == null || modeFilterGameType.isBlank()) {
            return Component.translatable("screen.codpattern.room.create_command");
        }
        return GameModeRegistry.find(modeFilterGameType)
                .map(ModeDescriptor::createCommand)
                .filter(hint -> !hint.isBlank())
                .<Component>map(Component::translatable)
                .orElseGet(() -> Component.translatable(
                        "screen.codpattern.room.create_command_filtered", modeFilterGameType));
    }

    private static int scaled(int value) {
        return GuiTextHelper.referenceScaled(value);
    }

    private static int fade(int color, float alpha) {
        return (Math.round((color >>> 24) * Mth.clamp(alpha, 0f, 1f)) << 24) | (color & 0xFFFFFF);
    }
}
