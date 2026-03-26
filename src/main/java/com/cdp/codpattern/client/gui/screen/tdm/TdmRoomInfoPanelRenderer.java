package com.cdp.codpattern.client.gui.screen.tdm;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class TdmRoomInfoPanelRenderer {
    private static final int BASE_FRAME_INSET = 5;
    private static final int BASE_CONTENT_PADDING = 10;
    private static final int BASE_HEADER_TOP_PADDING = 4;
    private static final int BASE_HEADER_BLOCK_HEIGHT = 26;
    private static final int BASE_ACTIONS_LABEL_GAP = 10;
    private static final int BASE_DIVIDER_GAP = 4;
    private static final int BASE_INFO_BLOCK_GAP = 8;
    private static final int BASE_BOTTOM_HINT_PADDING = 18;
    private static final int BASE_PREVIEW_CARD_HEIGHT = 46;

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
            boolean leavePending,
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
        int overviewY = headerY + GuiTextHelper.referenceScaled(BASE_HEADER_BLOCK_HEIGHT);
        int overviewHeight = GuiTextHelper.referenceScaled(42);
        int actionsLabelY = overviewY + overviewHeight + GuiTextHelper.referenceScaled(BASE_ACTIONS_LABEL_GAP);
        int dividerY = actionsLabelY + referenceLineHeight + GuiTextHelper.referenceScaled(BASE_DIVIDER_GAP);

        TdmRoomData currentRoomSummary = joinedRoom == null ? null : lobbySummaryState.rooms().get(joinedRoom);
        TdmRoomData selectedPreview = selectedRoomPreviewState.summarySnapshot();
        boolean showSelectedPreview = selectedPreview != null
                && selectedRoom != null
                && !selectedRoom.isBlank()
                && (joinedRoom == null || !selectedRoom.equals(joinedRoom));

        renderHeader(
                graphics,
                mc,
                contentX,
                headerY,
                lobbySummaryState,
                currentRoomSummary,
                joinedRoomLiveState,
                selectedPreview,
                joinedRoom,
                selectedRoom,
                nowMs,
                contentFactor);
        renderPrimaryCard(
                graphics,
                mc,
                contentX,
                overviewY,
                contentWidth,
                currentRoomSummary,
                joinedRoomLiveState,
                selectedPreview,
                joinedRoom,
                contentFactor);

        GuiTextHelper.drawReferenceString(
                graphics,
                mc.font,
                Component.translatable("screen.codpattern.tdm_room.room_actions"),
                contentX,
                actionsLabelY,
                scaleAlpha(CodTheme.TEXT_SECONDARY, contentFactor),
                false);
        int dividerEndX = contentX + Math.max(GuiTextHelper.referenceScaled(24),
                Math.min(GuiTextHelper.referenceScaled(220), contentWidth - GuiTextHelper.referenceScaled(8)));
        graphics.fill(contentX, dividerY, dividerEndX, dividerY + 1, scaleAlpha(CodTheme.DIVIDER, contentFactor));

        int infoY = Math.max(
                dividerY + GuiTextHelper.referenceScaled(BASE_INFO_BLOCK_GAP + 6),
                infoActionBottomY + GuiTextHelper.referenceScaled(BASE_INFO_BLOCK_GAP));

        if (showSelectedPreview) {
            renderPreviewCard(
                    graphics,
                    mc,
                    contentX,
                    infoY,
                    contentWidth,
                    selectedPreview,
                    contentFactor);
            infoY += GuiTextHelper.referenceScaled(BASE_PREVIEW_CARD_HEIGHT + BASE_INFO_BLOCK_GAP);
        }

        if (joinedRoom != null && joinedRoomLiveState.hasLiveRoom()) {
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    Component.translatable(
                            "screen.codpattern.tdm_room.current_score",
                            TdmRoomTextFormatter.teamScoreText(joinedRoomLiveState.teamScores())),
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
                                    joinedRoomLiveState.phase(),
                                    joinedRoomLiveState.remainingTimeTicks())),
                    contentX,
                    infoY + referenceLineHeight + GuiTextHelper.referenceScaled(3),
                    scaleAlpha(0xFFB0B0B0, contentFactor),
                    false);
            infoY += referenceLineHeight * 2 + GuiTextHelper.referenceScaled(BASE_INFO_BLOCK_GAP);
            if (joinedRoomLiveState.isStale(nowMs)) {
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

        TdmRoomData warningRoom = showSelectedPreview ? selectedPreview : currentRoomSummary;
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

        int hintLines = (leavePending ? 1 : 0) + (hasRoomNotice ? 1 : 0);
        int rosterTop = Math.max(
                infoActionBottomY + GuiTextHelper.referenceScaled(BASE_INFO_BLOCK_GAP + 8),
                infoY + GuiTextHelper.referenceScaled(BASE_INFO_BLOCK_GAP + 2));
        int hintPadding = GuiTextHelper.referenceScaled(BASE_BOTTOM_HINT_PADDING);
        int rosterBottom = panelY + panelHeight - hintPadding
                - (hintLines * (referenceLineHeight + GuiTextHelper.referenceScaled(3)));
        if (joinedRoom != null && rosterTop < rosterBottom) {
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    Component.translatable("screen.codpattern.tdm_room.roster_title"),
                    contentX,
                    rosterTop - referenceLineHeight - GuiTextHelper.referenceScaled(3),
                    scaleAlpha(CodTheme.TEXT_SECONDARY, contentFactor),
                    false);
            TdmRoomRosterRenderer.render(
                    graphics,
                    mc,
                    contentX,
                    contentWidth,
                    rosterTop,
                    rosterBottom,
                    joinedRoomLiveState.teamPlayers(),
                    contentFactor,
                    nowMs);
        }

        int hintY = panelY + panelHeight - hintPadding;
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
            int headerY,
            LobbySummaryState lobbySummaryState,
            TdmRoomData currentRoomSummary,
            JoinedRoomLiveState joinedRoomLiveState,
            TdmRoomData selectedPreview,
            String joinedRoom,
            String selectedRoom,
            long nowMs,
            float alphaFactor) {
        int secondaryY = headerY + GuiTextHelper.referenceLineHeight(mc.font) + GuiTextHelper.referenceScaled(4);
        if (joinedRoom != null && currentRoomSummary != null) {
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    Component.translatable("screen.codpattern.tdm_room.current_room", currentRoomSummary.mapName),
                    x,
                    headerY,
                    scaleAlpha(CodTheme.TEXT_PRIMARY, alphaFactor),
                    false);
            Component secondary = joinedRoomLiveState.isStale(nowMs)
                    ? Component.translatable(
                            "screen.codpattern.tdm_room.live_stale_hint",
                            Math.max(1, joinedRoomLiveState.secondsSinceLatestSync(nowMs)))
                    : Component.translatable("screen.codpattern.tdm_room.current_room_live_hint");
            int secondaryColor = joinedRoomLiveState.isStale(nowMs) ? CodTheme.TEXT_DANGER : CodTheme.TEXT_SECONDARY;
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    secondary,
                    x,
                    secondaryY,
                    scaleAlpha(secondaryColor, alphaFactor),
                    false);
            return;
        }

        if (selectedPreview != null && selectedRoom != null && !selectedRoom.isBlank()) {
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    Component.translatable("screen.codpattern.tdm_room.selected_room", selectedPreview.mapName),
                    x,
                    headerY,
                    scaleAlpha(CodTheme.TEXT_PRIMARY, alphaFactor),
                    false);
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    Component.translatable("screen.codpattern.tdm_room.selected_room_preview_hint"),
                    x,
                    secondaryY,
                    scaleAlpha(CodTheme.TEXT_SECONDARY, alphaFactor),
                    false);
            return;
        }

        Component fallback = lobbySummaryState.isLoading() && !lobbySummaryState.hasLoaded()
                ? Component.translatable("screen.codpattern.tdm_room.loading")
                : Component.translatable("screen.codpattern.tdm_room.select_hint");
        GuiTextHelper.drawReferenceString(
                graphics,
                mc.font,
                fallback,
                x,
                headerY,
                scaleAlpha(CodTheme.TEXT_SECONDARY, alphaFactor),
                false);
    }

    private static void renderPrimaryCard(
            GuiGraphics graphics,
            Minecraft mc,
            int x,
            int y,
            int width,
            TdmRoomData currentRoomSummary,
            JoinedRoomLiveState joinedRoomLiveState,
            TdmRoomData selectedPreview,
            String joinedRoom,
            float alphaFactor) {
        if (joinedRoom != null && currentRoomSummary != null) {
            renderCard(
                    graphics,
                    mc,
                    x,
                    y,
                    width,
                    Component.translatable("screen.codpattern.tdm_room.current_room_live_title").getString(),
                    currentRoomSummary.mapName,
                    TdmRoomTextFormatter.teamScoreText(joinedRoomLiveState.teamScores())
                            + "  "
                            + TdmRoomTextFormatter.phaseStatusText(
                                    joinedRoomLiveState.phase(),
                                    joinedRoomLiveState.remainingTimeTicks()),
                    "",
                    CodTheme.HOVER_BORDER,
                    alphaFactor);
            return;
        }

        if (selectedPreview != null) {
            renderCard(
                    graphics,
                    mc,
                    x,
                    y,
                    width,
                    Component.translatable("screen.codpattern.tdm_room.selected_room_preview_title").getString(),
                    selectedPreview.mapName,
                    Component.translatable(
                            "screen.codpattern.tdm_room.players",
                            selectedPreview.playerCount,
                            selectedPreview.maxPlayers).getString()
                            + "  "
                            + TdmRoomTextFormatter.phaseStatusText(
                                    selectedPreview.state,
                                    selectedPreview.remainingTimeTicks),
                    "",
                    CodTheme.SELECTED_BORDER,
                    alphaFactor);
            return;
        }

        renderCard(
                graphics,
                mc,
                x,
                y,
                width,
                Component.translatable("screen.codpattern.tdm_room.overview").getString(),
                Component.translatable("screen.codpattern.tdm_room.select_hint").getString(),
                "",
                "",
                CodTheme.SELECTED_BORDER,
                alphaFactor);
    }

    private static void renderPreviewCard(
            GuiGraphics graphics,
            Minecraft mc,
            int x,
            int y,
            int width,
            TdmRoomData preview,
            float alphaFactor) {
        renderCard(
                graphics,
                mc,
                x,
                y,
                width,
                Component.translatable("screen.codpattern.tdm_room.selected_room_preview_title").getString(),
                preview.mapName,
                Component.translatable(GameModeRegistry.getOrDefault(preview.gameType).displayNameKey()).getString()
                        + "  "
                        + preview.playerCount
                        + "/"
                        + preview.maxPlayers
                        + "  "
                        + TdmRoomTextFormatter.teamSplitText(preview.teamPlayerCounts)
                        + "  "
                        + TdmRoomTextFormatter.phaseStatusText(preview.state, preview.remainingTimeTicks),
                "",
                CodTheme.SELECTED_BORDER,
                alphaFactor);
    }

    private static void renderCard(
            GuiGraphics graphics,
            Minecraft mc,
            int x,
            int y,
            int width,
            String title,
            String line1,
            String line2,
            String line3,
            int accentColor,
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
                scaleAlpha(withAlpha(accentColor, 170), alphaFactor));

        int textX = x + GuiTextHelper.referenceScaled(6);
        GuiTextHelper.drawReferenceString(
                graphics,
                mc.font,
                title,
                textX,
                y + GuiTextHelper.referenceScaled(4),
                scaleAlpha(CodTheme.TEXT_SECONDARY, alphaFactor),
                false);
        GuiTextHelper.drawReferenceEllipsizedString(
                graphics,
                mc.font,
                line1,
                textX,
                y + GuiTextHelper.referenceScaled(4) + lineHeight + GuiTextHelper.referenceScaled(3),
                width - GuiTextHelper.referenceScaled(12),
                scaleAlpha(CodTheme.TEXT_PRIMARY, alphaFactor),
                false);
        if (line2 != null && !line2.isBlank()) {
            GuiTextHelper.drawReferenceEllipsizedString(
                    graphics,
                    mc.font,
                    line2,
                    textX,
                    y + GuiTextHelper.referenceScaled(4) + (lineHeight + GuiTextHelper.referenceScaled(3)) * 2,
                    width - GuiTextHelper.referenceScaled(12),
                    scaleAlpha(0xFFB8C7D8, alphaFactor),
                    false);
        } else if (line3 != null && !line3.isBlank()) {
            GuiTextHelper.drawReferenceEllipsizedString(
                    graphics,
                    mc.font,
                    line3,
                    textX,
                    y + GuiTextHelper.referenceScaled(4) + (lineHeight + GuiTextHelper.referenceScaled(3)) * 2,
                    width - GuiTextHelper.referenceScaled(12),
                    scaleAlpha(0xFFB8C7D8, alphaFactor),
                    false);
        }
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
