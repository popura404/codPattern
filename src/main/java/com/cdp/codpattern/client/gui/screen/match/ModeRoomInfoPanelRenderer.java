package com.cdp.codpattern.client.gui.screen.match;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.model.ModeCapability;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.model.RoomSummaryMetric;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.cdp.codpattern.client.gui.GuiTextHelper.referenceScaled;

public final class ModeRoomInfoPanelRenderer {
    private static final int PRIMARY = 0xFFF3F6F6;
    private static final int SECONDARY = 0xFFBDC7C9;
    private static final int WARNING = 0xFFFFBB8C;
    private static final float SCORE_SCALE = 2.75f;
    private static final float NAME_SCALE = 1.65f;

    private ModeRoomRosterRenderer cachedRoster;
    private Map<String, List<PlayerInfo>> cachedPlayers;
    private String cachedGameType;
    private int cachedWidth;
    private float cachedScale;

    public int render(
            GuiGraphics graphics, Minecraft mc, int panelX, int panelY, int panelWidth, int panelHeight,
            int actionTopY, LobbySummaryState lobby, JoinedRoomLiveState live, SelectedRoomPreviewState preview,
            String joinedRoom, String selectedRoom, boolean hasConfirmHint, String confirmHintText,
            int confirmHintColor, boolean hasRoomNotice, String roomNoticeText, int roomNoticeColor,
            float panelAlphaFactor, float contentAlphaFactor, int scrollOffset) {
        if (panelWidth <= 0 || panelHeight <= 0) {
            return 0;
        }
        boolean previewing = selectedRoom != null && selectedRoom.equals(preview.roomKey())
                && !selectedRoom.equals(joinedRoom) && preview.summarySnapshot() != null;
        ModeRoomData summary = previewing ? preview.summarySnapshot()
                : joinedRoom == null ? null : lobby.rooms().get(joinedRoom);
        boolean joined = !previewing && joinedRoom != null && (summary != null || joinedRoom.equals(live.roomKey()));
        RoomId roomId = summary == null && joined ? decode(joinedRoom) : null;
        String gameType = summary != null ? summary.gameType : roomId != null ? roomId.gameType() : "";

        int panelRight = panelX + panelWidth;
        int panelBottom = panelY + panelHeight;
        int padding = Math.min(referenceScaled(10), panelWidth / 6);
        int contentX = panelX + padding;
        int contentWidth = Math.max(1, panelWidth - padding * 2);
        int actionTop = Math.max(panelY, Math.min(panelBottom, actionTopY));
        int insetY = Math.min(referenceScaled(8), (actionTop - panelY) / 8);
        int viewportBottom = actionTop - insetY;

        Component score = previewing || joined ? ModeRoomTextFormatter.roomScore(gameType, summary,
                joined && joinedRoom.equals(live.roomKey()) ? live.teamScores() : Map.of()) : Component.empty();
        int naturalScoreWidth = mc.font.width(score);
        float scoreScale = naturalScoreWidth == 0 ? 0f : Math.min(SCORE_SCALE,
                contentWidth * 0.48f / (naturalScoreWidth * GuiTextHelper.referenceScale()));
        int scoreWidth = (int) Math.ceil(naturalScoreWidth * GuiTextHelper.referenceScale() * scoreScale);
        int titleWidth = scoreWidth == 0 ? contentWidth : Math.max(1, contentWidth - scoreWidth - referenceScaled(8));
        TextLayout header = new TextLayout(mc.font, titleWidth);
        if (joined) {
            int accent = gameType.isBlank() ? SECONDARY : ModePreviewPanel.accentColor(gameType) | 0xFF000000;
            header.add(Component.translatable("screen.codpattern.room.joined_heading"), 1f, accent);
            header.gap(4);
        }
        TextLayout body = new TextLayout(mc.font, contentWidth);
        ModeRoomRosterRenderer roster = null;
        if (summary == null && !joined) {
            String hint = lobby.isLoading() && !lobby.hasLoaded() ? "screen.codpattern.room.loading"
                    : lobby.isEmpty() ? "screen.codpattern.room.no_rooms" : "screen.codpattern.room.select_hint";
            body.add(Component.translatable(hint), 1f, SECONDARY);
        } else {
            String name = summary != null ? summary.mapName : roomId != null ? roomId.mapName() : joinedRoom;
            Component title = Component.literal(name).withStyle(ChatFormatting.BOLD);
            if (scoreWidth > 0) {
                header.addTitleWithScore(title, score, scoreScale, contentWidth - scoreWidth);
            } else {
                body.add(title, NAME_SCALE, PRIMARY);
            }
            if (summary != null) {
                body.gap(3);
                body.add(Component.translatable(GameModeRegistry.getOrDefault(gameType).displayNameKey())
                        .append("   ·   ").append(Component.translatable("screen.codpattern.room.players",
                                summary.playerCount, summary.maxPlayers)), 1f, SECONDARY);
            }
            body.gap(8);
            addMatchFacts(body, summary, live, previewing);
            long now = System.currentTimeMillis();
            if (joined && live.isStale(now)) {
                body.gap(4);
                body.add(Component.translatable("screen.codpattern.room.live_stale_hint",
                        Math.max(1, live.secondsSinceLatestSync(now))), 1f, WARNING);
            }
            if (summary != null && summary.hasCapability(ModeCapability.MATCH_END_TELEPORT)
                    && !summary.hasMatchEndTeleportPoint) {
                body.gap(4);
                body.add(Component.translatable("screen.codpattern.room.warning_no_end_tp"), 1f, WARNING);
                body.add(Component.translatable("screen.codpattern.room.warning_no_end_tp_hint"), 1f, SECONDARY);
            }
            body.gap(9);
            body.add(Component.translatable("screen.codpattern.room.roster_title").withStyle(ChatFormatting.BOLD),
                    1f, PRIMARY);
            body.gap(4);
            if (previewing && !preview.hasRosterSnapshot()) {
                body.add(Component.translatable(summary.playerCount > 0 ? "screen.codpattern.room.roster_loading"
                        : "screen.codpattern.room.no_players"), 1f, SECONDARY);
            } else {
                Map<String, List<PlayerInfo>> players = previewing ? preview.teamPlayers() : live.teamPlayers();
                roster = roster(contentWidth, gameType, players);
            }
        }
        TextLayout feedback = new TextLayout(mc.font, contentWidth);
        if (hasConfirmHint && confirmHintText != null && !confirmHintText.isBlank()) {
            feedback.add(Component.literal(confirmHintText), 1f, confirmHintColor);
        }
        if (hasRoomNotice && roomNoticeText != null && !roomNoticeText.isBlank()) {
            feedback.add(Component.literal(roomNoticeText), 1f, roomNoticeColor);
        }
        int feedbackHeight = feedback.height == 0 ? 0 : feedback.height + referenceScaled(5);
        int bodyHeight = body.height + (roster == null ? 0 : roster.height());
        graphics.fillGradient(panelX, panelY, panelRight, panelBottom,
                tint(0xB413191D, panelAlphaFactor), tint(0xCD0C1013, panelAlphaFactor));
        graphics.fill(panelX, panelY, panelRight, panelY + referenceScaled(1), tint(0x74CAD3D5, panelAlphaFactor));
        int viewportTop = panelY + insetY;
        if (viewportBottom <= viewportTop) {
            return 0;
        }
        // Keep the score in the corner while scrolling. Very short windows need the entire viewport for the body.
        boolean pinHeader = scoreWidth > 0 && viewportBottom - viewportTop >= header.height + referenceScaled(22);
        int scrollTop = pinHeader ? viewportTop + header.height : viewportTop;
        int inlineHeaderHeight = pinHeader ? 0 : header.height;
        boolean pinFeedback = feedbackHeight > 0 && feedbackHeight <= (viewportBottom - scrollTop) / 3;
        int bodyBottom = pinFeedback ? viewportBottom - feedbackHeight : viewportBottom;
        int inlineFeedbackHeight = pinFeedback ? 0 : feedbackHeight;
        int visibleHeight = bodyBottom - scrollTop;
        int totalHeight = inlineHeaderHeight + inlineFeedbackHeight + bodyHeight;
        int maxScroll = Math.max(0, totalHeight - visibleHeight);
        int scroll = Math.max(0, Math.min(scrollOffset, maxScroll));
        float alpha = Math.max(0f, Math.min(1f, panelAlphaFactor * contentAlphaFactor));
        if (alpha < 0.02f) {
            return maxScroll;
        }

        if (pinHeader) {
            header.draw(graphics, contentX, viewportTop, viewportTop, scrollTop, alpha);
        }
        int startY = scrollTop - scroll;
        graphics.enableScissor(contentX, scrollTop, contentX + contentWidth, bodyBottom);
        if (!pinHeader) {
            header.draw(graphics, contentX, startY, scrollTop, bodyBottom, alpha);
        }
        int feedbackY = startY + inlineHeaderHeight;
        if (!pinFeedback) {
            feedback.draw(graphics, contentX, feedbackY, scrollTop, bodyBottom, alpha);
        }
        int bodyY = feedbackY + inlineFeedbackHeight;
        body.draw(graphics, contentX, bodyY, scrollTop, bodyBottom, alpha);
        if (roster != null) {
            roster.render(graphics, mc, contentX, bodyY + body.height, scrollTop, bodyBottom, alpha);
        }
        graphics.disableScissor();

        if (pinFeedback) {
            graphics.fill(contentX, bodyBottom, contentX + contentWidth, bodyBottom + 1, tint(0x55BCC6C8, alpha));
            graphics.enableScissor(contentX, bodyBottom, contentX + contentWidth, viewportBottom);
            feedback.draw(graphics, contentX, bodyBottom + referenceScaled(5), bodyBottom, viewportBottom, alpha);
            graphics.disableScissor();
        }
        if (maxScroll > 0 && padding >= 2) {
            int trackX = panelRight - padding / 2;
            int thickness = Math.min(referenceScaled(2), padding / 2);
            int thumb = Math.min(visibleHeight, Math.max(referenceScaled(5),
                    Math.round(visibleHeight * (visibleHeight / (float) totalHeight))));
            int thumbY = scrollTop + Math.round((visibleHeight - thumb) * (scroll / (float) maxScroll));
            graphics.fill(trackX, scrollTop, trackX + thickness, bodyBottom, tint(0x800B0F12, alpha));
            graphics.fill(trackX, thumbY, trackX + thickness, thumbY + thumb, tint(0xCCDEE4E4, alpha));
        }
        return maxScroll;
    }

    private ModeRoomRosterRenderer roster(int width, String gameType, Map<String, List<PlayerInfo>> players) {
        // Live and preview states replace their roster maps when a snapshot or delta arrives.
        float scale = GuiTextHelper.referenceScale();
        if (cachedPlayers != players || cachedWidth != width || cachedScale != scale || !gameType.equals(cachedGameType)) {
            cachedRoster = new ModeRoomRosterRenderer(width, gameType, players);
            cachedPlayers = players;
            cachedGameType = gameType;
            cachedWidth = width;
            cachedScale = scale;
        }
        return cachedRoster;
    }

    private static void addMatchFacts(TextLayout body, ModeRoomData summary, JoinedRoomLiveState live,
                                      boolean previewing) {
        String phase = previewing ? summary.state : live.phase();
        int ticks = previewing ? summary.remainingTimeTicks : live.remainingTimeTicks();
        body.add(Component.translatable("screen.codpattern.room.current_phase",
                ModeRoomTextFormatter.phaseStatusText(phase, ticks)), 1f, SECONDARY);
        if (summary != null) {
            List<Component> facts = new ArrayList<>();
            for (RoomSummaryMetric metric : summary.metrics) {
                if (!ModeRoomTextFormatter.isTeamScore(metric)) {
                    facts.add(ModeRoomTextFormatter.metricText(metric));
                }
            }
            if (!facts.isEmpty()) {
                body.gap(3);
                body.addFacts(facts);
            }
        }
    }

    private static RoomId decode(String key) {
        try {
            return RoomId.decode(key);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private record Line(FormattedCharSequence text, int x, int y, int height, float scale, int color) {}
    private record Cell(int x, int y, int width, int height) {}

    private static final class TextLayout {
        private final Font font;
        private final int width;
        private final List<Line> lines = new ArrayList<>();
        private final List<Cell> cells = new ArrayList<>();
        private int height;

        private TextLayout(Font font, int width) {
            this.font = font;
            this.width = width;
        }

        private void gap(int units) {
            height += referenceScaled(units);
        }

        private void add(Component text, float scale, int color) {
            height = wrap(text, 0, height, width, scale, color);
        }

        private void addTitleWithScore(Component title, Component score, float scoreScale, int scoreX) {
            // The default font's CJK glyphs occupy eight pixels and its digits seven, inside a nine-pixel line box.
            float inkHeightDifference = (7f * scoreScale - 8f * NAME_SCALE) * GuiTextHelper.referenceScale();
            int centerOffset = Math.round(inkHeightDifference / 2f);
            int titleY = height + Math.max(0, centerOffset);
            int scoreY = height + Math.max(0, -centerOffset);
            int titleBottom = wrap(title, 0, titleY, width, NAME_SCALE, PRIMARY);
            int scoreHeight = GuiTextHelper.referenceLineHeight(font, scoreScale);
            lines.add(new Line(score.getVisualOrderText(), scoreX, scoreY, scoreHeight, scoreScale, PRIMARY));
            height = Math.max(titleBottom, scoreY + scoreHeight) + referenceScaled(3);
        }

        private int wrap(Component text, int x, int y, int space, float scale, int color) {
            int fontWidth = Math.max(1, (int) Math.floor(space / (scale * GuiTextHelper.referenceScale())));
            int lineHeight = GuiTextHelper.referenceLineHeight(font, scale);
            for (FormattedCharSequence line : font.split(text, fontWidth)) {
                lines.add(new Line(line, x, y, lineHeight, scale, color));
                y += lineHeight + referenceScaled(1);
            }
            return y;
        }

        private void addFacts(List<Component> facts) {
            int columns = facts.size() > 1 && width >= referenceScaled(228) ? 2 : 1;
            int gap = referenceScaled(3);
            int pad = referenceScaled(4);
            int cellWidth = (width - gap * (columns - 1)) / columns;
            for (int i = 0; i < facts.size(); i += columns) {
                int count = Math.min(columns, facts.size() - i);
                int top = height;
                for (int column = 0; column < count; column++) {
                    int x = column * (cellWidth + gap);
                    int end = wrap(facts.get(i + column), x + pad, top + pad, cellWidth - pad * 2, 1f, PRIMARY);
                    height = Math.max(height, end + pad);
                }
                for (int column = 0; column < count; column++) {
                    cells.add(new Cell(column * (cellWidth + gap), top, cellWidth, height - top));
                }
                height += gap;
            }
        }

        private void draw(GuiGraphics graphics, int x, int y, int clipTop, int clipBottom, float alpha) {
            for (Cell cell : cells) {
                int top = y + cell.y();
                if (top < clipBottom && top + cell.height() > clipTop) {
                    graphics.fill(x + cell.x(), top, x + cell.x() + cell.width(), top + cell.height(),
                            tint(0x392F3A3E, alpha));
                }
            }
            for (Line line : lines) {
                int top = y + line.y();
                if (top < clipBottom && top + line.height() > clipTop) {
                    GuiTextHelper.drawScaledString(graphics, font, line.text(), x + line.x(), top,
                            GuiTextHelper.referenceScale() * line.scale(), tint(line.color(), alpha), false);
                }
            }
        }
    }

    private static int tint(int color, float alpha) {
        return Math.round((color >>> 24) * alpha) << 24 | (color & 0xFFFFFF);
    }
}
