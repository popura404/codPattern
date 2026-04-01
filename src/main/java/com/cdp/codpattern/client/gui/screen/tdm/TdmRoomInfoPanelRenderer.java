package com.cdp.codpattern.client.gui.screen.tdm;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

public final class TdmRoomInfoPanelRenderer {
    private static final int BASE_FRAME_INSET = 5;
    private static final int BASE_CONTENT_PADDING = 10;
    private static final int BASE_HEADER_TOP_PADDING = 4;
    private static final int BASE_HEADER_BLOCK_HEIGHT = 26;
    private static final int BASE_INFO_BLOCK_GAP = 8;
    private static final int BASE_BOTTOM_HINT_PADDING = 18;
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
        int referenceLineHeight = GuiTextHelper.referenceLineHeight(mc.font);
        int frameInset = GuiTextHelper.referenceScaled(BASE_FRAME_INSET);
        int contentPadding = GuiTextHelper.referenceScaled(BASE_CONTENT_PADDING);
        int frameLeft = panelX - frameInset;
        int frameTop = panelY - frameInset;
        int frameRight = panelX + panelWidth + frameInset;
        int frameBottom = panelY + panelHeight + frameInset;
        float contentFactor = Math.max(0.0f, Math.min(1.0f, panelAlphaFactor * contentAlphaFactor));
        long nowMs = System.currentTimeMillis();

        graphics.fillGradient(frameLeft, frameTop, frameRight, frameBottom,
                scaleAlpha(CodTheme.PANEL_BG, panelAlphaFactor),
                scaleAlpha(0xCC101010, panelAlphaFactor));
        graphics.fill(frameLeft, frameTop, frameRight, frameTop + 1, scaleAlpha(CodTheme.BORDER_SUBTLE, panelAlphaFactor));
        graphics.fill(frameLeft, frameBottom - 1, frameRight, frameBottom, scaleAlpha(CodTheme.BORDER_SUBTLE, panelAlphaFactor));
        graphics.fill(frameLeft, frameTop, frameLeft + 1, frameBottom, scaleAlpha(CodTheme.BORDER_SUBTLE, panelAlphaFactor));
        graphics.fill(frameRight - 1, frameTop, frameRight, frameBottom, scaleAlpha(CodTheme.BORDER_SUBTLE, panelAlphaFactor));

        int contentX = panelX + contentPadding;
        int contentWidth = Math.max(GuiTextHelper.referenceScaled(40), panelWidth - contentPadding * 2);
        int headerY = panelY + GuiTextHelper.referenceScaled(BASE_HEADER_TOP_PADDING);
        int headerBottom = headerY + GuiTextHelper.referenceScaled(BASE_HEADER_BLOCK_HEIGHT);
        int headerDividerY = headerBottom + GuiTextHelper.referenceScaled(2);

        TdmRoomData currentRoomSummary = joinedRoom == null ? null : lobbySummaryState.rooms().get(joinedRoom);
        TdmRoomData selectedPreview = selectedRoomPreviewState.summarySnapshot();
        boolean showSelectedPreview = selectedPreview != null
                && selectedRoom != null
                && !selectedRoom.isBlank()
                && (joinedRoom == null || !selectedRoom.equals(joinedRoom));
        TdmRoomData activeRoomSummary = showSelectedPreview ? selectedPreview : currentRoomSummary;

        renderHeader(
                graphics,
                mc,
                contentX,
                contentWidth,
                headerY,
                lobbySummaryState,
                activeRoomSummary,
                joinedRoomLiveState,
                joinedRoom,
                selectedRoom,
                showSelectedPreview,
                contentFactor);
        graphics.fill(contentX, headerDividerY, contentX + contentWidth, headerDividerY + 1,
                scaleAlpha(CodTheme.DIVIDER, contentFactor));

        int infoY = Math.max(
                headerDividerY + GuiTextHelper.referenceScaled(BASE_INFO_BLOCK_GAP),
                infoActionBottomY + GuiTextHelper.referenceScaled(BASE_INFO_BLOCK_GAP));

        if (activeRoomSummary != null) {
            Map<String, Integer> teamScores = showSelectedPreview
                    ? activeRoomSummary.teamScores
                    : joinedRoomLiveState.teamScores().isEmpty()
                    ? activeRoomSummary.teamScores
                    : joinedRoomLiveState.teamScores();
            String phase = showSelectedPreview ? activeRoomSummary.state : joinedRoomLiveState.phase();
            int remainingTimeTicks = showSelectedPreview
                    ? activeRoomSummary.remainingTimeTicks
                    : joinedRoomLiveState.remainingTimeTicks();
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    Component.translatable(
                            "screen.codpattern.tdm_room.current_score",
                            TdmRoomTextFormatter.teamScoreText(teamScores)),
                    contentX,
                    infoY,
                    scaleAlpha(0xFFE5E5E5, contentFactor),
                    false);
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    Component.translatable(
                            "screen.codpattern.tdm_room.current_phase",
                            TdmRoomTextFormatter.phaseStatusText(
                                    phase,
                                    remainingTimeTicks)),
                    contentX,
                    infoY + referenceLineHeight + GuiTextHelper.referenceScaled(3),
                    scaleAlpha(0xFFB0B0B0, contentFactor),
                    false);
            infoY += referenceLineHeight * 2 + GuiTextHelper.referenceScaled(BASE_INFO_BLOCK_GAP);
            if (!showSelectedPreview && joinedRoomLiveState.isStale(nowMs)) {
                GuiTextHelper.drawReferenceString(
                        graphics,
                        mc.font,
                        Component.translatable(
                                "screen.codpattern.tdm_room.live_stale_hint",
                                Math.max(1, joinedRoomLiveState.secondsSinceLatestSync(nowMs))),
                        contentX,
                        infoY,
                        scaleAlpha(CodTheme.TEXT_DANGER, contentFactor),
                        false);
                infoY += referenceLineHeight + GuiTextHelper.referenceScaled(BASE_INFO_BLOCK_GAP - 2);
            }
        } else if (lobbySummaryState.isLoading() && !lobbySummaryState.hasLoaded()) {
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    Component.translatable("screen.codpattern.tdm_room.loading"),
                    contentX,
                    infoY,
                    scaleAlpha(CodTheme.TEXT_SECONDARY, contentFactor),
                    false);
            infoY += referenceLineHeight + GuiTextHelper.referenceScaled(BASE_INFO_BLOCK_GAP - 2);
        }

        TdmRoomData warningRoom = activeRoomSummary;
        if (warningRoom != null && !warningRoom.hasMatchEndTeleportPoint) {
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
            infoY += referenceLineHeight * 2 + GuiTextHelper.referenceScaled(BASE_INFO_BLOCK_GAP);
        }

        int hintLines = (hasConfirmHint ? 1 : 0) + (hasRoomNotice ? 1 : 0);
        int rosterTop = Math.max(
                infoActionBottomY + GuiTextHelper.referenceScaled(BASE_INFO_BLOCK_GAP + 8),
                infoY + GuiTextHelper.referenceScaled(BASE_INFO_BLOCK_GAP + 2));
        int hintPadding = GuiTextHelper.referenceScaled(BASE_BOTTOM_HINT_PADDING);
        int rosterBottom = panelY + panelHeight - hintPadding
                - (hintLines * (referenceLineHeight + GuiTextHelper.referenceScaled(3)));
        if (activeRoomSummary != null && rosterTop < rosterBottom) {
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    Component.translatable("screen.codpattern.tdm_room.roster_title"),
                    contentX,
                    rosterTop - referenceLineHeight - GuiTextHelper.referenceScaled(3),
                    scaleAlpha(CodTheme.TEXT_SECONDARY, contentFactor),
                    false);
            if (showSelectedPreview && !selectedRoomPreviewState.hasRosterSnapshot()) {
                GuiTextHelper.drawReferenceString(
                        graphics,
                        mc.font,
                        activeRoomSummary.playerCount > 0
                                ? Component.translatable("screen.codpattern.tdm_room.roster_loading")
                                : Component.translatable("screen.codpattern.tdm_room.no_players"),
                        contentX,
                        rosterTop,
                        scaleAlpha(CodTheme.TEXT_DIM, contentFactor),
                        false);
            } else {
                Map<String, List<PlayerInfo>> teamPlayers = showSelectedPreview
                        ? selectedRoomPreviewState.teamPlayers()
                        : joinedRoomLiveState.teamPlayers();
                TdmRoomRosterRenderer.render(
                        graphics,
                        mc,
                        contentX,
                        contentWidth,
                        rosterTop,
                        rosterBottom,
                        teamPlayers,
                        contentFactor,
                        nowMs);
            }
        }

        int hintY = panelY + panelHeight - hintPadding;
        if (hasConfirmHint && confirmHintText != null && !confirmHintText.isBlank()) {
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    confirmHintText,
                    contentX,
                    hintY,
                    scaleAlpha(confirmHintColor, contentFactor),
                    false);
            hintY -= referenceLineHeight + GuiTextHelper.referenceScaled(3);
        }
        if (hasRoomNotice) {
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    roomNoticeText,
                    contentX,
                    hintY,
                    scaleAlpha(roomNoticeColor, contentFactor),
                    false);
        }
    }

    private static void renderHeader(
            GuiGraphics graphics,
            Minecraft mc,
            int x,
            int contentWidth,
            int headerY,
            LobbySummaryState lobbySummaryState,
            TdmRoomData activeRoomSummary,
            JoinedRoomLiveState joinedRoomLiveState,
            String joinedRoom,
            String selectedRoom,
            boolean showSelectedPreview,
            float alphaFactor) {
        int secondaryY = headerY + GuiTextHelper.referenceLineHeight(mc.font) + GuiTextHelper.referenceScaled(4);
        if (showSelectedPreview && activeRoomSummary != null) {
            GuiTextHelper.drawReferenceEllipsizedString(
                    graphics,
                    mc.font,
                    Component.translatable("screen.codpattern.tdm_room.selected_room", activeRoomSummary.mapName),
                    x,
                    headerY,
                    contentWidth,
                    scaleAlpha(CodTheme.TEXT_PRIMARY, alphaFactor),
                    false);
            GuiTextHelper.drawReferenceEllipsizedString(
                    graphics,
                    mc.font,
                    roomMetaText(activeRoomSummary),
                    x,
                    secondaryY,
                    contentWidth,
                    scaleAlpha(CodTheme.TEXT_SECONDARY, alphaFactor),
                    false);
            return;
        }

        if (joinedRoom != null && (!joinedRoom.isBlank())
                && (activeRoomSummary != null || joinedRoomLiveState.hasLiveRoom())) {
            GuiTextHelper.drawReferenceEllipsizedString(
                    graphics,
                    mc.font,
                    Component.translatable("screen.codpattern.tdm_room.current_room", roomLabel(joinedRoom, activeRoomSummary)),
                    x,
                    headerY,
                    contentWidth,
                    scaleAlpha(CodTheme.TEXT_PRIMARY, alphaFactor),
                    false);
            Component secondary = activeRoomSummary != null
                    ? roomMetaText(activeRoomSummary)
                    : Component.translatable(
                            "screen.codpattern.tdm_room.current_phase",
                            TdmRoomTextFormatter.phaseStatusText(
                                    joinedRoomLiveState.phase(),
                                    joinedRoomLiveState.remainingTimeTicks()));
            GuiTextHelper.drawReferenceEllipsizedString(
                    graphics,
                    mc.font,
                    secondary,
                    x,
                    secondaryY,
                    contentWidth,
                    scaleAlpha(CodTheme.TEXT_SECONDARY, alphaFactor),
                    false);
            return;
        }

        Component fallback = lobbySummaryState.isLoading() && !lobbySummaryState.hasLoaded()
                ? Component.translatable("screen.codpattern.tdm_room.loading")
                : Component.translatable("screen.codpattern.tdm_room.select_hint");
        GuiTextHelper.drawReferenceEllipsizedString(
                graphics,
                mc.font,
                fallback,
                x,
                headerY,
                contentWidth,
                scaleAlpha(CodTheme.TEXT_SECONDARY, alphaFactor),
                false);
    }

    private static Component roomMetaText(TdmRoomData roomSummary) {
        return Component.literal(
                Component.translatable(GameModeRegistry.getOrDefault(roomSummary.gameType).displayNameKey()).getString()
                        + "  "
                        + Component.translatable(
                                "screen.codpattern.tdm_room.players",
                                roomSummary.playerCount,
                                roomSummary.maxPlayers).getString());
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

    private static String roomLabel(String roomKey, TdmRoomData summary) {
        if (summary != null && summary.mapName != null && !summary.mapName.isBlank()) {
            return summary.mapName;
        }
        if (roomKey == null || roomKey.isBlank()) {
            return "";
        }
        try {
            return RoomId.decode(roomKey).mapName();
        } catch (IllegalArgumentException ignored) {
            return roomKey;
        }
    }
}
