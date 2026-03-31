package com.cdp.codpattern.client.gui.overlay;

import com.cdp.codpattern.app.tdm.model.TdmTeamNames;
import com.cdp.codpattern.app.tdm.service.PhaseStateMachine;
import com.cdp.codpattern.client.ClientTdmState;
import com.cdp.codpattern.client.TdmCombatMarkerTracker;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import com.cdp.codpattern.client.state.KillFeedEntry;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import com.cdp.codpattern.compat.tacz.client.TaczClientApi;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class TdmHudOverlay implements IGuiOverlay {

    private static final int RESULT_PAGE_FADE_TICKS = 12;
    private static final int ROSTER_ROW_STAGGER_TICKS = 6;
    private static final int ROSTER_ROW_FADE_TICKS = 10;
    private static final int COMBAT_MARKER_SCREEN_MARGIN = 24;
    private static final int INVINCIBILITY_MARKER_SIZE = 8;
    private static final int INVINCIBILITY_MARKER_COLOR = 0xFFF6F6F6;
    private static final int INVINCIBILITY_MARKER_OUTLINE = 0xFFBFC7D0;
    private static final int KILL_FEED_ICON_BASE_SIZE = 14;
    private static final int KILL_FEED_TEXTURE_BASE_WIDTH = 28;
    private static final int KILL_FEED_TEXTURE_BASE_HEIGHT = 8;
    private static final int KILL_FEED_FALLBACK_BASE_WIDTH = 22;
    private static final int KILL_FEED_FALLBACK_BASE_HEIGHT = 9;
    private static final int KILL_FEED_MIN_ROW_HEIGHT = 14;
    private static final int KILL_FEED_START_OFFSET = 42;
    private static final int KILL_FEED_ROW_GAP = 1;
    private static final int KILL_FEED_BACKGROUND_LEFT_PADDING = 4;
    private static final int KILL_FEED_BACKGROUND_VERTICAL_PADDING = 1;
    private static final int KILL_FEED_TEXT_GAP = 5;
    private static final int KILL_FEED_BACKGROUND_SEGMENTS = 18;
    private static final float KILL_FEED_TEXT_SCALE_MULTIPLIER = 1.0f;
    private static final int DEATH_CAM_PANEL_WIDTH = 240;
    private static final int DEATH_CAM_PANEL_HEIGHT = 56;
    private static final int DEATH_CAM_PANEL_SIDE_MARGIN = 16;
    private static final int DEATH_CAM_PANEL_BOTTOM_MARGIN = 40;
    private static final int DEATH_CAM_PANEL_MIN_CENTER_OFFSET = 18;
    private static final int DEATH_CAM_PANEL_TEXT_PADDING = 12;
    private static final double ENEMY_BAR_HEAD_OFFSET = 0.62D;
    private static final double INVINCIBILITY_MARKER_HEAD_OFFSET = 0.92D;
    private static final Vec3 WORLD_UP = new Vec3(0.0D, 1.0D, 0.0D);

    public static final TdmHudOverlay INSTANCE = new TdmHudOverlay();

    private record ResultCandidate(String teamName, PlayerInfo player) {
    }

    private record SpotlightPair(ResultCandidate mvp, ResultCandidate svp) {
    }

    private record ScreenProjection(int x, int y, double depth) {
    }

    private record KillFeedRowLayout(int killerMaxWidth, int centerWidth, int victimMaxWidth, int firstGap, int secondGap) {
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        if (!shouldRenderHud()) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        int centerX = screenWidth / 2;

        renderBlackout(graphics, font, screenWidth, screenHeight);
        renderLeftScorePanel(graphics, font, screenWidth, screenHeight);
        renderKillFeed(graphics, font, screenWidth, screenHeight);
        renderPhaseAnnouncement(graphics, font, centerX, screenHeight);
        renderCountdownFocus(graphics, font, centerX, screenHeight);
        renderCombatMarkers(graphics, partialTick, screenWidth, screenHeight);
        renderEndgameSplash(graphics, font, centerX, screenWidth, screenHeight);
        renderDeathCamPanel(graphics, font, centerX, screenWidth, screenHeight);
    }

    private boolean shouldRenderHud() {
        if (ClientTdmState.isDead() || ClientTdmState.isBlackoutActive()) {
            return true;
        }
        if (ClientTdmState.announcementTicks() > 0) {
            return true;
        }
        return !("WAITING".equals(ClientTdmState.currentPhase())
                && ClientTdmState.remainingTimeTicks() <= 0
                && getKortacScore() == 0
                && getSpecgruScore() == 0);
    }

    private void renderBlackout(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        if (!ClientTdmState.isBlackoutActive()) {
            return;
        }
        float alpha = ClientTdmState.getBlackoutAlpha();
        int alphaInt = clamp((int) (alpha * 255.0f), 0, 255);
        if (alphaInt <= 0) {
            return;
        }

        graphics.fill(0, 0, screenWidth, screenHeight, alphaInt << 24);
        if (alpha > 0.55f) {
            String text = ClientTdmState.blackoutPhase() == ClientTdmState.BlackoutPhase.FADE_OUT
                    ? Component.translatable("hud.codpattern.tdm.blackout.ready").getString()
                    : Component.translatable("hud.codpattern.tdm.blackout.teleport").getString();
            drawCenteredString(graphics, font, text, screenWidth / 2, screenHeight / 2 - 8, 0xFFF5F5F5);
        }
    }

    private void renderLeftScorePanel(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        int x = 8;
        int y = screenHeight >= 500 ? 92 : 70;
        int rowWidth = 120;
        int rowHeight = 18;
        int rowGap = 3;
        int timerY = y + (rowHeight * 2) + rowGap + 4;
        int phaseY = timerY + 10;
        if (x + rowWidth >= screenWidth || phaseY >= screenHeight) {
            return;
        }

        int kortacScore = getKortacScore();
        int specgruScore = getSpecgruScore();
        int maxScore = Math.max(1, Math.max(kortacScore, specgruScore));
        float pulseStrength = ClientTdmState.getScorePulseStrength();

        renderScoreRow(graphics, font, x, y, rowWidth, rowHeight, teamShort("kortac"),
                kortacScore, (float) kortacScore / maxScore, 0xFFE35A5A, pulseStrength);
        renderScoreRow(graphics, font, x, y + rowHeight + rowGap, rowWidth, rowHeight, teamShort("specgru"),
                specgruScore, (float) specgruScore / maxScore, 0xFF66A6FF, pulseStrength);

        graphics.drawString(font, buildTimerText(), x, timerY, 0xFFB08A3E, false);
        graphics.drawString(font, phaseShortText(ClientTdmState.currentPhase()), x, phaseY, 0xFF8F8F8F, false);

        int phaseGlow = (int) (ClientTdmState.getPhaseFlashStrength() * 95.0f);
        if (phaseGlow > 0) {
            graphics.fill(x, y, x + rowWidth, y + 2,
                    (clamp(phaseGlow, 0, 255) << 24) | (phaseAccentColor() & 0x00FFFFFF));
        }
    }

    private void renderScoreRow(GuiGraphics graphics, Font font, int x, int y, int width, int height, String label, int score,
            float ratio, int accent, float pulse) {
        graphics.fillGradient(x, y, x + width, y + height, withAlpha(accent, 28), withAlpha(accent, 10));
        graphics.fill(x, y, x + 2, y + height, withAlpha(accent, 180));

        String scoreText = String.valueOf(score);
        graphics.drawString(font, label, x + 6, y + 2, 0xFFF2F2F2, false);
        graphics.drawString(font, scoreText, x + width - font.width(scoreText) - 5, y + 2, 0xFFFFFFFF, false);

        int barX = x + 24;
        int barY = y + height - 4;
        int barW = Math.max(8, width - 34);
        graphics.fill(barX, barY, barX + barW, barY + 2, 0x22FFFFFF);
        int fill = (int) (barW * Math.max(0.0f, Math.min(1.0f, ratio)));
        if (fill > 0) {
            graphics.fill(barX, barY, barX + fill, barY + 2, accent);
        }

        int pulseAlpha = (int) (pulse * 60.0f);
        if (pulseAlpha > 0) {
            graphics.fill(x, y, x + width, y + height, (clamp(pulseAlpha, 0, 255) << 24) | 0x00FFD463);
        }
    }

    private void renderKillFeed(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        if (!isKillFeedPhase()) {
            return;
        }

        List<KillFeedEntry> entries = ClientTdmState.killFeedSnapshot();
        if (entries.isEmpty()) {
            return;
        }

        int x = 8;
        int scoreY = screenHeight >= 500 ? 92 : 70;
        int rowHeight = 18;
        int rowGap = 3;
        int timerY = scoreY + (rowHeight * 2) + rowGap + 4;
        int phaseY = timerY + 10;
        int contentHeight = Math.max(GuiTextHelper.referenceScaled(KILL_FEED_MIN_ROW_HEIGHT),
                Math.max(GuiTextHelper.referenceScaled(KILL_FEED_ICON_BASE_SIZE), killFeedLineHeight(font)));
        int verticalPadding = GuiTextHelper.referenceScaled(KILL_FEED_BACKGROUND_VERTICAL_PADDING);
        int startY = phaseY + font.lineHeight + GuiTextHelper.referenceScaled(KILL_FEED_START_OFFSET);
        int maxWidth = screenWidth - x - 8;
        int step = contentHeight + (verticalPadding * 2) + GuiTextHelper.referenceScaled(KILL_FEED_ROW_GAP);
        if (maxWidth <= GuiTextHelper.referenceScaled(64)) {
            return;
        }

        for (int i = 0; i < entries.size(); i++) {
            renderKillFeedRow(graphics, font, x, startY + (i * step), maxWidth, contentHeight, entries.get(i));
        }
    }

    private void renderKillFeedRow(GuiGraphics graphics, Font font, int x, int y, int width, int contentHeight,
            KillFeedEntry entry) {
        int alpha = clamp((int) (entry.alpha() * 255.0f), 0, 255);
        if (alpha <= 0) {
            return;
        }

        int textY = y + Math.max(0, (contentHeight - killFeedLineHeight(font)) / 2);
        int killerColor = resolveKillFeedTeamColor(entry.killerName(), 0xFFF3F3F3);
        int victimColor = resolveKillFeedTeamColor(entry.victimName(), 0xFFE7A5A5);
        KillFeedRowLayout layout = measureKillFeedRow(font, entry, width);

        if (entry.blunder()) {
            String blunderText = Component.translatable("hud.codpattern.tdm.kill_feed.blunder").getString();
            String fittedKiller = ellipsizeKillFeed(font, entry.killerName(), layout.killerMaxWidth());
            String fittedBlunder = ellipsizeKillFeed(font, blunderText, layout.centerWidth());
            int killerWidth = killFeedTextWidth(font, fittedKiller);
            int blunderWidth = killFeedTextWidth(font, fittedBlunder);
            int gap = killerWidth > 0 && blunderWidth > 0 ? layout.firstGap() : 0;
            int rowWidth = killerWidth + gap + blunderWidth;
            if (rowWidth <= 0) {
                return;
            }

            int backgroundLeft = x - GuiTextHelper.referenceScaled(KILL_FEED_BACKGROUND_LEFT_PADDING);
            int backgroundTop = y - GuiTextHelper.referenceScaled(KILL_FEED_BACKGROUND_VERTICAL_PADDING);
            int backgroundBottom = y + contentHeight + GuiTextHelper.referenceScaled(KILL_FEED_BACKGROUND_VERTICAL_PADDING);
            drawKillFeedBackground(graphics, backgroundLeft, backgroundTop, x + rowWidth, backgroundBottom,
                    0xFF0E131A, Math.max(42, alpha / 2));

            drawKillFeedString(graphics, font, fittedKiller, x, textY, withAlpha(killerColor, alpha), true);
            drawKillFeedString(graphics, font, fittedBlunder,
                    x + killerWidth + gap,
                    textY,
                    withAlpha(0xFFE39D63, alpha),
                    true);
            return;
        }

        String fittedKiller = ellipsizeKillFeed(font, entry.killerName(), layout.killerMaxWidth());
        String fittedVictim = ellipsizeKillFeed(font, entry.victimName(), layout.victimMaxWidth());
        int killerWidth = killFeedTextWidth(font, fittedKiller);
        int victimWidth = killFeedTextWidth(font, fittedVictim);
        int firstGap = killerWidth > 0 && layout.centerWidth() > 0 ? layout.firstGap() : 0;
        int secondGap = victimWidth > 0 && layout.centerWidth() > 0 ? layout.secondGap() : 0;
        int rowWidth = killerWidth + firstGap + layout.centerWidth() + secondGap + victimWidth;
        if (rowWidth <= 0) {
            return;
        }

        int backgroundLeft = x - GuiTextHelper.referenceScaled(KILL_FEED_BACKGROUND_LEFT_PADDING);
        int backgroundTop = y - GuiTextHelper.referenceScaled(KILL_FEED_BACKGROUND_VERTICAL_PADDING);
        int backgroundBottom = y + contentHeight + GuiTextHelper.referenceScaled(KILL_FEED_BACKGROUND_VERTICAL_PADDING);
        drawKillFeedBackground(graphics, backgroundLeft, backgroundTop, x + rowWidth, backgroundBottom,
                0xFF0E131A, Math.max(42, alpha / 2));

        int killerX = x;
        int iconX = killerX + killerWidth + firstGap;
        int victimX = iconX + layout.centerWidth() + secondGap;

        drawKillFeedString(graphics, font, fittedKiller, killerX, textY, withAlpha(killerColor, alpha), true);
        renderKillFeedWeapon(graphics, font, iconX, y, layout.centerWidth(), contentHeight, entry.weaponStack(), alpha);
        drawKillFeedString(graphics, font, fittedVictim, victimX, textY, withAlpha(victimColor, alpha), true);
    }

    private void renderKillFeedWeapon(GuiGraphics graphics, Font font, int x, int y, int width, int contentHeight,
            ItemStack weaponStack, int alpha) {
        if (weaponStack != null && !weaponStack.isEmpty()) {
            ResourceLocation hudTexture = TaczClientApi.getGunHudTexture(weaponStack);
            if (hudTexture != null) {
                float scale = GuiTextHelper.referenceScale();
                int actualWidth = GuiTextHelper.referenceScaled(KILL_FEED_TEXTURE_BASE_WIDTH);
                int actualHeight = GuiTextHelper.referenceScaled(KILL_FEED_TEXTURE_BASE_HEIGHT);
                int drawX = x + Math.max(0, (width - actualWidth) / 2);
                int drawY = y + Math.max(0, (contentHeight - actualHeight) / 2);
                RenderSystem.enableBlend();
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha / 255.0f);
                graphics.pose().pushPose();
                graphics.pose().translate(drawX, drawY, 0.0f);
                graphics.pose().scale(scale, scale, 1.0f);
                graphics.blit(hudTexture, 0, 0, 0, 0, KILL_FEED_TEXTURE_BASE_WIDTH, KILL_FEED_TEXTURE_BASE_HEIGHT,
                        KILL_FEED_TEXTURE_BASE_WIDTH, KILL_FEED_TEXTURE_BASE_HEIGHT);
                graphics.pose().popPose();
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                RenderSystem.disableBlend();
                return;
            }

            float scale = GuiTextHelper.referenceScale();
            int actualSize = GuiTextHelper.referenceScaled(KILL_FEED_ICON_BASE_SIZE);
            int drawX = x + Math.max(0, (width - actualSize) / 2);
            int drawY = y + Math.max(0, (contentHeight - actualSize) / 2);
            graphics.pose().pushPose();
            graphics.pose().translate(drawX, drawY, 0.0f);
            graphics.pose().scale(scale, scale, 1.0f);
            graphics.renderItem(weaponStack, 0, 0);
            graphics.pose().popPose();
            return;
        }

        renderKillFeedFallbackTag(graphics, font, x, y, width, contentHeight, alpha);
    }

    private void renderKillFeedFallbackTag(GuiGraphics graphics, Font font, int x, int y, int width, int contentHeight,
            int alpha) {
        String fallbackText = Component.translatable("hud.codpattern.tdm.kill_feed.fallback").getString();
        int fallbackWidth = Math.max(KILL_FEED_FALLBACK_BASE_WIDTH, font.width(fallbackText) + 6);
        int actualWidth = GuiTextHelper.referenceScaled(fallbackWidth);
        int actualHeight = GuiTextHelper.referenceScaled(KILL_FEED_FALLBACK_BASE_HEIGHT);
        int drawX = x + Math.max(0, (width - actualWidth) / 2);
        int drawY = y + Math.max(0, (contentHeight - actualHeight) / 2);

        graphics.fill(drawX, drawY, drawX + actualWidth, drawY + actualHeight,
                withAlpha(0xFF6A3F38, Math.max(60, alpha / 2)));
        drawKillFeedCenteredString(graphics, font, fallbackText, drawX + (actualWidth / 2.0f),
                drawY + Math.max(0, (actualHeight - killFeedLineHeight(font)) / 2.0f),
                withAlpha(0xFFF6EDE6, alpha), false);
    }

    private void drawKillFeedBackground(GuiGraphics graphics, int left, int top, int right, int bottom, int color,
            int leftAlpha) {
        int width = right - left;
        if (width <= 0 || bottom <= top) {
            return;
        }

        // Approximate a left-to-right alpha fade with narrow strips; this keeps the HUD code simple
        // while matching the requested 100 -> 0 transparency ramp.
        int segments = Math.max(1, Math.min(KILL_FEED_BACKGROUND_SEGMENTS, width));
        for (int i = 0; i < segments; i++) {
            int segmentLeft = left + (width * i) / segments;
            int segmentRight = left + (width * (i + 1)) / segments;
            if (segmentRight <= segmentLeft) {
                continue;
            }
            float t = (segments == 1) ? 0.0f : (float) i / (segments - 1);
            int segmentAlpha = clamp(Math.round(leftAlpha * (1.0f - t)), 0, 255);
            if (segmentAlpha <= 0) {
                continue;
            }
            graphics.fill(segmentLeft, top, segmentRight, bottom, withAlpha(color, segmentAlpha));
        }
    }

    private void renderPhaseAnnouncement(GuiGraphics graphics, Font font, int centerX, int screenHeight) {
        if ("ENDED".equals(ClientTdmState.currentPhase())) {
            return;
        }
        if (ClientTdmState.announcementTicks() <= 0 || ClientTdmState.announcementKey().isEmpty()) {
            return;
        }

        float alphaF = ClientTdmState.getAnnouncementAlpha();
        int alpha = clamp((int) (alphaF * 255.0f), 0, 255);
        if (alpha <= 0) {
            return;
        }

        String text = Component.translatable(ClientTdmState.announcementKey()).getString();
        int boxWidth = Math.max(156, font.width(text) + 22);
        int boxHeight = 20;
        int x = centerX - boxWidth / 2;
        int y = screenHeight / 5;

        graphics.fillGradient(x, y, x + boxWidth, y + boxHeight, ((alpha * 3 / 5) << 24) | 0x00101318,
                ((alpha * 4 / 5) << 24) | 0x001B212B);
        graphics.fill(x, y + boxHeight - 2, x + boxWidth, y + boxHeight,
                (alpha << 24) | (phaseAccentColor() & 0x00FFFFFF));
        drawCenteredString(graphics, font, text, centerX, y + 6, (alpha << 24) | 0x00F5F5F5);
    }

    private void renderCountdownFocus(GuiGraphics graphics, Font font, int centerX, int screenHeight) {
        if (!"COUNTDOWN".equals(ClientTdmState.currentPhase())) {
            return;
        }
        int secondsLeft = Math.max(1, (ClientTdmState.remainingTimeTicks() + 19) / 20);
        if (secondsLeft > 10) {
            return;
        }

        float scale = secondsLeft <= 3 ? 5.7f : 4.9f;
        int color = secondsLeft <= 3 ? 0xFFFF6A4E : 0xFFFFE28A;
        int numberY = screenHeight / 2 - 72;
        drawScaledCenteredString(graphics, font, String.valueOf(secondsLeft), centerX, numberY, color, scale);
        drawCenteredString(graphics, font, Component.translatable("hud.codpattern.tdm.countdown_hint").getString(),
                centerX, numberY + 86, 0xFFE4E4E4);
    }

    private void renderEndgameSplash(GuiGraphics graphics, Font font, int centerX, int screenWidth, int screenHeight) {
        if (!"ENDED".equals(ClientTdmState.currentPhase())) {
            return;
        }

        int pageTick = ClientTdmState.endSummaryPageTick();
        int pageDuration = Math.max(1, ClientTdmState.endSummaryPageDurationTicks());
        int pageIndex = ClientTdmState.endSummaryPageIndex();

        int alpha = clamp((int) (computePageAlpha(pageTick, pageDuration) * 255.0f), 0, 255);
        if (alpha <= 0) {
            return;
        }

        int accent = endAccentColor();
        renderEndBackdrop(graphics, screenWidth, screenHeight, alpha, accent);

        switch (pageIndex) {
            case 1 -> renderMvpSvpPage(graphics, font, centerX, screenWidth, screenHeight, alpha);
            case 2 -> renderTeamRosterPage(graphics, font, centerX, screenWidth, screenHeight, alpha, pageTick);
            default -> renderPrimaryResultPage(graphics, font, centerX, screenWidth, screenHeight, alpha, accent);
        }

        String pageText = Component.translatable("hud.codpattern.tdm.result.page_index", pageIndex + 1,
                PhaseStateMachine.END_SUMMARY_PAGE_COUNT).getString();
        drawCenteredString(graphics, font, pageText, centerX, screenHeight - 20, withAlpha(0xFFE4E4E4, alpha));
    }

    private void renderEndBackdrop(GuiGraphics graphics, int screenWidth, int screenHeight, int alpha, int accent) {
        graphics.fill(0, 0, screenWidth, screenHeight, (alpha * 3 / 7) << 24);
        graphics.fill(0, 0, screenWidth, 36, (alpha << 24) | (accent & 0x00FFFFFF));
        graphics.fill(0, screenHeight - 36, screenWidth, screenHeight, (alpha << 24) | (accent & 0x00FFFFFF));
    }

    private void renderPrimaryResultPage(GuiGraphics graphics, Font font, int centerX, int screenWidth, int screenHeight,
            int alpha, int accent) {
        int cardWidth = Math.min(500, screenWidth - 36);
        int cardHeight = 132;
        int x = centerX - cardWidth / 2;
        int y = screenHeight / 2 - cardHeight / 2;

        graphics.fillGradient(x, y, x + cardWidth, y + cardHeight, (alpha * 4 / 5 << 24) | 0x0010141C,
                (alpha * 4 / 5 << 24) | 0x001A202A);
        graphics.fill(x, y + cardHeight - 3, x + cardWidth, y + cardHeight, (alpha << 24) | (accent & 0x00FFFFFF));

        String title = buildEndResultTitle();
        String resultText = buildEndResultText();
        String scoreLine = Component.translatable("hud.codpattern.tdm.result.scoreline", getKortacScore(),
                getSpecgruScore()).getString();

        drawScaledCenteredString(graphics, font, title, centerX, y + 20, (alpha << 24) | 0x00FFFFFF, 2.05f);
        drawCenteredString(graphics, font, resultText, centerX, y + 66, (alpha << 24) | 0x00FFDDA0);
        drawCenteredString(graphics, font, scoreLine, centerX, y + 80, (alpha << 24) | 0x00E5E5E5);
        drawCenteredString(graphics, font, Component.translatable("hud.codpattern.tdm.result.callout").getString(),
                centerX, y + 99, (alpha << 24) | 0x00C8C8C8);
    }

    private void renderMvpSvpPage(GuiGraphics graphics, Font font, int centerX, int screenWidth, int screenHeight, int alpha) {
        int panelWidth = Math.min(760, screenWidth - 38);
        int panelHeight = Math.min(236, screenHeight - 88);
        int x = centerX - panelWidth / 2;
        int y = screenHeight / 2 - panelHeight / 2;

        graphics.fillGradient(x, y, x + panelWidth, y + panelHeight, withAlpha(0xFF0F1520, alpha), withAlpha(0xFF1A2231, alpha));
        graphics.fill(x, y + panelHeight - 3, x + panelWidth, y + panelHeight, withAlpha(endAccentColor(), alpha));

        drawCenteredString(graphics, font, Component.translatable("hud.codpattern.tdm.result.page.mvp_svp").getString(),
                centerX, y + 10, withAlpha(0xFFF3F3F3, alpha));

        SpotlightPair pair = resolveSpotlightPair(ClientTdmState.teamPlayersSnapshot());

        int gap = 12;
        int cardWidth = (panelWidth - 24 - gap) / 2;
        int cardHeight = panelHeight - 38;

        renderSpotlightCard(graphics, font, x + 10, y + 24, cardWidth, cardHeight,
                pair.mvp(), Component.translatable("hud.codpattern.tdm.result.mvp").getString(), alpha, 0);
        renderSpotlightCard(graphics, font, x + 10 + cardWidth + gap, y + 24, cardWidth, cardHeight,
                pair.svp(), Component.translatable("hud.codpattern.tdm.result.svp").getString(), alpha, 1);
    }

    private void renderSpotlightCard(GuiGraphics graphics, Font font, int x, int y, int width, int height,
            ResultCandidate candidate, String title, int alpha, int slotIndex) {
        int accentColor = resolveCandidateAccent(candidate);
        int accent = withAlpha(accentColor, alpha);
        graphics.fillGradient(x, y, x + width, y + height, withAlpha(0xFF121820, alpha), withAlpha(0xFF0C1117, alpha));
        graphics.fill(x, y, x + 2, y + height, accent);
        graphics.fill(x, y + height - 2, x + width, y + height, accent);
        graphics.fill(x, y + 18, x + width, y + 19, withAlpha(accentColor, Math.max(36, alpha / 3)));

        drawScaledCenteredString(graphics, font, title, x + width / 2, y + 8, withAlpha(0xFFF8F8F8, alpha), 1.25f);

        if (candidate == null || candidate.player() == null) {
            drawCenteredString(graphics, font, Component.translatable("hud.codpattern.tdm.result.no_player").getString(),
                    x + width / 2, y + height / 2 - 4, withAlpha(0xFFC6C6C6, alpha));
            return;
        }

        PlayerInfo player = candidate.player();
        int stageTop = y + 30;
        int stageBottom = y + height - 66;
        int stageCenterX = x + width / 2;
        int pedestalWidth = Math.max(72, width - 52);
        renderSpotlightCone(graphics, stageCenterX, y + 19, stageTop + 4, stageBottom - 6, accentColor, alpha, slotIndex);
        renderPedestal(graphics, stageCenterX, stageBottom - 12, pedestalWidth, accentColor, alpha);

        int modelScale = Math.max(30, Math.min(70, width / 3));
        float turn = slotIndex == 0 ? 14.0f : -14.0f;
        float pitch = -10.0f;
        int modelBaseline = stageBottom - 8;
        boolean modelRendered = alpha > 20
                && TdmPlayerModelRenderer.render(graphics, player, stageCenterX, modelBaseline, modelScale, turn, pitch);
        if (!modelRendered) {
            int avatarSize = 50;
            int avatarX = stageCenterX - avatarSize / 2;
            int avatarY = stageTop + 8;
            renderPlayerAvatar(graphics, player, avatarX, avatarY, avatarSize, alpha);
        }

        int textY = stageBottom + 3;
        drawCenteredString(graphics, font, player.name(), stageCenterX, textY, withAlpha(0xFFF2F2F2, alpha));
        String kdText = Component.translatable("hud.codpattern.tdm.result.kd_line",
                formatKd(player.kills(), player.deaths()),
                player.kills(),
                player.deaths()).getString();
        String streakText = Component.translatable("hud.codpattern.tdm.result.streak_line",
                player.maxKillStreak()).getString();
        drawCenteredString(graphics, font, kdText, stageCenterX, textY + 14, withAlpha(0xFFE6D79E, alpha));
        drawCenteredString(graphics, font, streakText, stageCenterX, textY + 26, withAlpha(0xFFBFD0FF, alpha));

        String teamText = Component.translatable("hud.codpattern.tdm.result.team_tag", teamNameLabel(candidate.teamName())).getString();
        drawCenteredString(graphics, font, teamText, stageCenterX, textY + 39, withAlpha(0xFFBBBBBB, alpha));
    }

    private void renderSpotlightCone(GuiGraphics graphics, int centerX, int lightY, int topY, int bottomY,
            int accentColor, int alpha, int slotIndex) {
        int sourceWidth = 16;
        graphics.fill(centerX - sourceWidth / 2, lightY, centerX + sourceWidth / 2, lightY + 3,
                withAlpha(0xFFFFFFFF, Math.max(20, alpha / 2)));

        int baseHalfWidth = 56 + slotIndex * 4;
        for (int i = 0; i < 5; i++) {
            int halfWidth = Math.max(10, baseHalfWidth - i * 10);
            int layerAlpha = Math.max(0, (alpha / 8) - i * 5);
            if (layerAlpha <= 0) {
                continue;
            }
            int layerTop = topY + i * 3;
            graphics.fillGradient(centerX - halfWidth, layerTop, centerX + halfWidth, bottomY,
                    withAlpha(accentColor, layerAlpha),
                    withAlpha(accentColor, 0));
        }
    }

    private void renderPedestal(GuiGraphics graphics, int centerX, int y, int width, int accentColor, int alpha) {
        int halfWidth = Math.max(34, width / 2);
        graphics.fillGradient(centerX - halfWidth, y, centerX + halfWidth, y + 8,
                withAlpha(0xFF263344, alpha),
                withAlpha(0xFF101720, alpha));
        graphics.fill(centerX - halfWidth, y + 8, centerX + halfWidth, y + 10,
                withAlpha(accentColor, Math.max(34, alpha / 2)));
        graphics.fill(centerX - halfWidth + 8, y + 2, centerX + halfWidth - 8, y + 4,
                withAlpha(0xFFF2F6FF, Math.max(20, alpha / 4)));
    }

    private void renderTeamRosterPage(GuiGraphics graphics, Font font, int centerX, int screenWidth, int screenHeight,
            int alpha, int pageTick) {
        Map<String, List<PlayerInfo>> teamPlayers = ClientTdmState.teamPlayersSnapshot();
        int panelWidth = Math.min(860, screenWidth - 30);
        int panelHeight = Math.min(280, screenHeight - 76);
        int x = centerX - panelWidth / 2;
        int y = screenHeight / 2 - panelHeight / 2;

        graphics.fillGradient(x, y, x + panelWidth, y + panelHeight, withAlpha(0xFF0E141D, alpha), withAlpha(0xFF141E2B, alpha));
        graphics.fill(x, y + panelHeight - 3, x + panelWidth, y + panelHeight, withAlpha(endAccentColor(), alpha));

        drawCenteredString(graphics, font, Component.translatable("hud.codpattern.tdm.result.page.roster").getString(),
                centerX, y + 10, withAlpha(0xFFF3F3F3, alpha));

        int contentX = x + 10;
        int contentY = y + 24;
        int contentWidth = panelWidth - 20;
        int contentHeight = panelHeight - 34;
        int gap = 10;
        int teamWidth = (contentWidth - gap) / 2;

        List<String> teamOrder = buildTeamOrder(teamPlayers);
        String leftTeam = teamOrder.size() > 0 ? teamOrder.get(0) : TdmTeamNames.KORTAC;
        String rightTeam = teamOrder.size() > 1 ? teamOrder.get(1) : TdmTeamNames.SPECGRU;

        renderTeamRosterColumn(graphics, font, contentX, contentY, teamWidth, contentHeight,
                leftTeam, teamPlayers.getOrDefault(leftTeam, List.of()), alpha, pageTick);
        renderTeamRosterColumn(graphics, font, contentX + teamWidth + gap, contentY, teamWidth, contentHeight,
                rightTeam, teamPlayers.getOrDefault(rightTeam, List.of()), alpha, pageTick);
    }

    private void renderTeamRosterColumn(GuiGraphics graphics, Font font, int x, int y, int width, int height,
            String teamName, List<PlayerInfo> players, int alpha, int pageTick) {
        int teamAccent = teamAccent(teamName);
        graphics.fillGradient(x, y, x + width, y + height, withAlpha(0xFF0E131A, alpha), withAlpha(0xFF101820, alpha));
        graphics.fill(x, y, x + width, y + 16, withAlpha(teamAccent, Math.min(200, alpha)));

        String teamTitle = Component.translatable("hud.codpattern.tdm.result.team_roster",
                teamNameLabel(teamName), players.size()).getString();
        graphics.drawString(font, teamTitle, x + 6, y + 4, withAlpha(0xFFF2F2F2, alpha), false);

        if (players.isEmpty()) {
            graphics.drawString(font, Component.translatable("hud.codpattern.tdm.result.no_player").getString(),
                    x + 8, y + 28, withAlpha(0xFFC0C0C0, alpha), false);
            return;
        }

        int rowY = y + 20;
        int rowHeight = 24;
        int rowGap = 3;
        int maxRows = Math.max(0, (height - 22) / (rowHeight + rowGap));

        for (int i = 0; i < Math.min(players.size(), maxRows); i++) {
            float progress = rowAppearProgress(pageTick, i);
            if (progress <= 0.0f) {
                continue;
            }
            PlayerInfo player = players.get(i);
            int rowAlpha = clamp((int) (alpha * progress), 0, 255);
            int yOffset = (int) ((1.0f - progress) * 6.0f);
            int top = rowY + i * (rowHeight + rowGap) + yOffset;

            renderRosterRow(graphics, font, x + 4, top, width - 8, rowHeight, player, teamAccent, rowAlpha);
        }
    }

    private void renderRosterRow(GuiGraphics graphics, Font font, int x, int y, int width, int height,
            PlayerInfo player, int teamAccent, int alpha) {
        graphics.fillGradient(x, y, x + width, y + height, withAlpha(0xFF141A24, alpha), withAlpha(0xFF10151E, alpha));
        graphics.fill(x, y, x + 2, y + height, withAlpha(teamAccent, alpha));

        int avatarSize = 16;
        int avatarX = x + 4;
        int avatarY = y + 4;
        renderPlayerAvatar(graphics, player, avatarX, avatarY, avatarSize, alpha);

        int textX = avatarX + avatarSize + 6;
        graphics.drawString(font, player.name(), textX, y + 4, withAlpha(0xFFF1F1F1, alpha), false);

        String kdText = Component.translatable("hud.codpattern.tdm.result.row_kd",
                formatKd(player.kills(), player.deaths()),
                player.kills(),
                player.deaths()).getString();
        graphics.drawString(font, kdText, textX, y + 14, withAlpha(0xFFD8C98E, alpha), false);

        String streakText = Component.translatable("hud.codpattern.tdm.result.row_streak", player.maxKillStreak()).getString();
        int streakX = x + width - font.width(streakText) - 6;
        graphics.drawString(font, streakText, streakX, y + 10, withAlpha(0xFFBFD0FF, alpha), false);
    }

    private void renderPlayerAvatar(GuiGraphics graphics, PlayerInfo player, int x, int y, int size, int alpha) {
        int safeAlpha = clamp(alpha, 0, 255);
        graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, withAlpha(0xFF1A1A1A, safeAlpha));

        if (player == null || player.uuid() == null) {
            renderAvatarFallback(graphics, player == null ? "?" : player.name(), x, y, size, safeAlpha);
            return;
        }

        ResourceLocation skin = Minecraft.getInstance().getSkinManager()
                .getInsecureSkinLocation(new GameProfile(player.uuid(), player.name()));

        if (safeAlpha < 255) {
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, safeAlpha / 255.0f);
        }
        // Use vanilla face renderer to avoid showing flattened skin regions.
        PlayerFaceRenderer.draw(graphics, skin, x, y, size);
        if (safeAlpha < 255) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.disableBlend();
        }
    }

    private void renderAvatarFallback(GuiGraphics graphics, String name, int x, int y, int size, int alpha) {
        graphics.fill(x, y, x + size, y + size, withAlpha(0xFF33465A, alpha));
        String text = (name == null || name.isBlank())
                ? "?"
                : name.substring(0, 1).toUpperCase(Locale.ROOT);
        Font font = Minecraft.getInstance().font;
        int textX = x + (size - font.width(text)) / 2;
        int textY = y + (size - 8) / 2;
        graphics.drawString(font, text, textX, textY, withAlpha(0xFFF2F2F2, alpha), false);
    }

    private SpotlightPair resolveSpotlightPair(Map<String, List<PlayerInfo>> teamPlayers) {
        List<ResultCandidate> allPlayers = flattenPlayers(teamPlayers);
        if (allPlayers.isEmpty()) {
            return new SpotlightPair(null, null);
        }

        Comparator<ResultCandidate> comparator = Comparator
                .comparingDouble((ResultCandidate candidate) -> kdValue(candidate.player())).reversed()
                .thenComparing(Comparator.comparingInt((ResultCandidate candidate) -> candidate.player().kills()).reversed())
                .thenComparing(
                        Comparator.comparingInt((ResultCandidate candidate) -> candidate.player().maxKillStreak()).reversed())
                .thenComparingInt(candidate -> candidate.player().deaths())
                .thenComparing(candidate -> candidate.player().name(), String.CASE_INSENSITIVE_ORDER);

        List<ResultCandidate> sortedAll = new ArrayList<>(allPlayers);
        sortedAll.sort(comparator);

        String winnerTeam = resolveWinnerTeam();
        if ("TIE".equals(winnerTeam)) {
            ResultCandidate mvp = sortedAll.get(0);
            ResultCandidate svp = null;
            for (int i = 1; i < sortedAll.size(); i++) {
                ResultCandidate candidate = sortedAll.get(i);
                if (!candidate.teamName().equalsIgnoreCase(mvp.teamName())) {
                    svp = candidate;
                    break;
                }
            }
            if (svp == null && sortedAll.size() > 1) {
                svp = sortedAll.get(1);
            }
            return new SpotlightPair(mvp, svp);
        }

        ResultCandidate mvp = sortedAll.stream()
                .filter(candidate -> candidate.teamName().equalsIgnoreCase(winnerTeam))
                .findFirst()
                .orElse(sortedAll.get(0));

        ResultCandidate svp = sortedAll.stream()
                .filter(candidate -> !candidate.teamName().equalsIgnoreCase(winnerTeam))
                .findFirst()
                .orElse(sortedAll.size() > 1 ? sortedAll.get(1) : null);

        return new SpotlightPair(mvp, svp);
    }

    private List<ResultCandidate> flattenPlayers(Map<String, List<PlayerInfo>> teamPlayers) {
        List<ResultCandidate> candidates = new ArrayList<>();
        for (Map.Entry<String, List<PlayerInfo>> entry : teamPlayers.entrySet()) {
            String team = entry.getKey();
            for (PlayerInfo player : entry.getValue()) {
                if (player != null) {
                    candidates.add(new ResultCandidate(team, player));
                }
            }
        }
        return candidates;
    }

    private List<String> buildTeamOrder(Map<String, List<PlayerInfo>> teamPlayers) {
        List<String> order = new ArrayList<>();
        if (teamPlayers.containsKey(TdmTeamNames.KORTAC)) {
            order.add(TdmTeamNames.KORTAC);
        }
        if (teamPlayers.containsKey(TdmTeamNames.SPECGRU)) {
            order.add(TdmTeamNames.SPECGRU);
        }
        for (String team : teamPlayers.keySet()) {
            if (!order.contains(team)) {
                order.add(team);
            }
        }
        if (order.isEmpty()) {
            order.add(TdmTeamNames.KORTAC);
            order.add(TdmTeamNames.SPECGRU);
        } else if (order.size() == 1) {
            order.add(order.get(0).equalsIgnoreCase(TdmTeamNames.KORTAC) ? TdmTeamNames.SPECGRU : TdmTeamNames.KORTAC);
        }
        return order;
    }

    private float rowAppearProgress(int pageTick, int rowIndex) {
        int startTick = rowIndex * ROSTER_ROW_STAGGER_TICKS;
        int elapsed = pageTick - startTick;
        if (elapsed <= 0) {
            return 0.0f;
        }
        float t = Math.min(1.0f, (float) elapsed / Math.max(1, ROSTER_ROW_FADE_TICKS));
        return smoothstep(t);
    }

    private float computePageAlpha(int pageTick, int pageDuration) {
        int fadeTicks = Math.min(RESULT_PAGE_FADE_TICKS, pageDuration / 3);
        if (fadeTicks <= 0) {
            return 1.0f;
        }
        if (pageTick < fadeTicks) {
            return smoothstep((float) pageTick / fadeTicks);
        }
        int tail = pageDuration - pageTick;
        if (tail < fadeTicks) {
            return smoothstep((float) tail / fadeTicks);
        }
        return 1.0f;
    }

    private float kdValue(PlayerInfo player) {
        if (player == null) {
            return 0.0f;
        }
        if (player.deaths() <= 0) {
            return player.kills();
        }
        return (float) player.kills() / player.deaths();
    }

    private String formatKd(int kills, int deaths) {
        if (kills <= 0 && deaths <= 0) {
            return "0.00";
        }
        if (deaths <= 0) {
            return String.format(Locale.ROOT, "%d.00", kills);
        }
        return String.format(Locale.ROOT, "%.2f", (double) kills / deaths);
    }

    private String resolveWinnerTeam() {
        if (getKortacScore() > getSpecgruScore()) {
            return TdmTeamNames.KORTAC;
        }
        if (getSpecgruScore() > getKortacScore()) {
            return TdmTeamNames.SPECGRU;
        }
        return "TIE";
    }

    private int resolveCandidateAccent(ResultCandidate candidate) {
        if (candidate == null) {
            return 0xFFB6B6B6;
        }
        return teamAccent(candidate.teamName());
    }

    private int teamAccent(String teamName) {
        if (TdmTeamNames.KORTAC.equalsIgnoreCase(teamName)) {
            return 0xFFE35A5A;
        }
        if (TdmTeamNames.SPECGRU.equalsIgnoreCase(teamName)) {
            return 0xFF66A6FF;
        }
        return 0xFFB8C5D4;
    }

    private String teamNameLabel(String teamName) {
        if (teamName == null || teamName.isBlank()) {
            return "-";
        }
        String key = "hud.codpattern.tdm.team." + teamName.toLowerCase(Locale.ROOT);
        String text = Component.translatable(key).getString();
        if (key.equals(text)) {
            return teamName.toUpperCase(Locale.ROOT);
        }
        return text;
    }

    private void renderCombatMarkers(GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        String phase = ClientTdmState.currentPhase();
        boolean warmup = "WARMUP".equals(phase);
        boolean playing = "PLAYING".equals(phase);
        if (!warmup && !playing) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer localPlayer = minecraft.player;
        ClientLevel level = minecraft.level;
        if (localPlayer == null || level == null) {
            return;
        }

        TdmCombatMarkerTracker.TeamVisionSnapshot snapshot = TdmCombatMarkerTracker.INSTANCE.snapshot();
        if (!snapshot.hasLocalTeam()
                || snapshot.localPlayerId() == null
                || !snapshot.localPlayerId().equals(localPlayer.getUUID())) {
            return;
        }
        TdmCombatMarkerTracker markerTracker = TdmCombatMarkerTracker.INSTANCE;

        for (Map.Entry<UUID, String> entry : snapshot.teamByPlayer().entrySet()) {
            UUID playerId = entry.getKey();
            if (playerId == null || playerId.equals(localPlayer.getUUID()) || !snapshot.isLiving(playerId)) {
                continue;
            }

            Player tracked = level.getPlayerByUUID(playerId);
            if (tracked == null || !tracked.isAlive() || tracked.isRemoved()) {
                continue;
            }

            if (playing
                    && snapshot.isInvincible(playerId)
                    && localPlayer.hasLineOfSight(tracked)) {
                ScreenProjection invincibleProjection = projectWorldToScreen(
                        minecraft,
                        partialTick,
                        interpolatePlayerHeadPos(tracked, partialTick, INVINCIBILITY_MARKER_HEAD_OFFSET),
                        screenWidth,
                        screenHeight);
                if (invincibleProjection != null) {
                    drawInvincibilityMarker(graphics, invincibleProjection.x(), invincibleProjection.y(), partialTick);
                }
            }

            if (playing
                    && snapshot.isEnemy(playerId)
                    && markerTracker.shouldRenderEnemyHealthBar(playerId)) {
                ScreenProjection barProjection = projectWorldToScreen(
                        minecraft,
                        partialTick,
                        interpolatePlayerHeadPos(tracked, partialTick, ENEMY_BAR_HEAD_OFFSET),
                        screenWidth,
                        screenHeight);
                if (barProjection != null) {
                    drawEnemyHealthBar(graphics, localPlayer, tracked, barProjection, screenWidth, screenHeight);
                }
            }
        }
    }

    private void drawInvincibilityMarker(GuiGraphics graphics, int centerX, int centerY, float partialTick) {
        int half = INVINCIBILITY_MARKER_SIZE / 2;
        int left = centerX - half;
        int top = centerY - half;
        int right = left + INVINCIBILITY_MARKER_SIZE;
        int bottom = top + INVINCIBILITY_MARKER_SIZE;

        float wave = 0.5f + 0.5f * Mth.sin((System.currentTimeMillis() / 120.0f) + partialTick);
        int pulseAlpha = 145 + (int) (wave * 65.0f);
        int fillAlpha = 170 + (int) (wave * 50.0f);

        graphics.fill(left - 2, top - 2, right + 2, bottom + 2, withAlpha(INVINCIBILITY_MARKER_OUTLINE, pulseAlpha));
        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, withAlpha(0xFF000000, 110));
        graphics.fill(left, top, right, bottom, withAlpha(INVINCIBILITY_MARKER_COLOR, fillAlpha));
    }

    private void drawEnemyHealthBar(GuiGraphics graphics, LocalPlayer localPlayer, Player enemy, ScreenProjection projection,
            int screenWidth, int screenHeight) {
        float maxHealth = Math.max(1.0f, enemy.getMaxHealth());
        float healthRatio = Mth.clamp(enemy.getHealth() / maxHealth, 0.0f, 1.0f);

        float distance = localPlayer.distanceTo(enemy);
        float distanceScale = Mth.clamp(1.25f - (distance / 80.0f), 0.6f, 1.25f);
        int barWidth = Math.max(22, Math.round((34.0f * 4.0f / 3.0f) * distanceScale));
        int barHeight = Math.max(1, Math.round((3.0f * 0.5f) * distanceScale));

        int left = projection.x() - barWidth / 2;
        int top = projection.y() - 4;
        int right = left + barWidth;
        int bottom = top + barHeight;
        if (right < 0 || left > screenWidth || bottom < 0 || top > screenHeight) {
            return;
        }

        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, withAlpha(0xFF000000, 180));
        graphics.fill(left, top, right, bottom, withAlpha(0xFF2B0E0E, 155));

        int fillWidth = Math.round(barWidth * healthRatio);
        if (fillWidth > 0) {
            graphics.fill(left, top, left + fillWidth, bottom, withAlpha(0xFFFF3A3A, 230));
        }
    }

    private ScreenProjection projectWorldToScreen(Minecraft minecraft,
            float partialTick,
            Vec3 worldPos,
            int screenWidth,
            int screenHeight) {
        if (minecraft.gameRenderer == null || minecraft.gameRenderer.getMainCamera() == null
                || !minecraft.gameRenderer.getMainCamera().isInitialized()) {
            return null;
        }

        Entity cameraEntity = minecraft.getCameraEntity();
        if (cameraEntity == null) {
            cameraEntity = minecraft.player;
        }
        if (cameraEntity == null) {
            return null;
        }

        Vec3 cameraPos = minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 relative = worldPos.subtract(cameraPos);

        float pitch = cameraEntity.getViewXRot(partialTick);
        float yaw = cameraEntity.getViewYRot(partialTick);
        Vec3 forward = Vec3.directionFromRotation(pitch, yaw).normalize();

        Vec3 right = forward.cross(WORLD_UP);
        if (right.lengthSqr() <= 1.0E-6) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(forward).normalize();

        double depth = relative.dot(forward);
        if (depth <= 0.05D) {
            return null;
        }

        double horizontal = relative.dot(right);
        double vertical = relative.dot(up);

        double fovDegrees = minecraft.options.fov().get();
        double focalLength = screenHeight / (2.0D * Math.tan(Math.toRadians(fovDegrees) / 2.0D));

        int screenX = (int) Math.round((screenWidth * 0.5D) + (horizontal * focalLength / depth));
        int screenY = (int) Math.round((screenHeight * 0.5D) - (vertical * focalLength / depth));

        if (screenX < -COMBAT_MARKER_SCREEN_MARGIN || screenX > screenWidth + COMBAT_MARKER_SCREEN_MARGIN
                || screenY < -COMBAT_MARKER_SCREEN_MARGIN || screenY > screenHeight + COMBAT_MARKER_SCREEN_MARGIN) {
            return null;
        }

        return new ScreenProjection(screenX, screenY, depth);
    }

    private Vec3 interpolatePlayerHeadPos(Player player, float partialTick, double headOffset) {
        double x = Mth.lerp(partialTick, player.xo, player.getX());
        double y = Mth.lerp(partialTick, player.yo, player.getY()) + player.getBbHeight() + headOffset;
        double z = Mth.lerp(partialTick, player.zo, player.getZ());
        return new Vec3(x, y, z);
    }

    private void renderDeathCamPanel(GuiGraphics graphics, Font font, int centerX, int screenWidth, int screenHeight) {
        if (!ClientTdmState.isDead()) {
            return;
        }
        int panelWidth = Math.max(96, Math.min(DEATH_CAM_PANEL_WIDTH, screenWidth - (DEATH_CAM_PANEL_SIDE_MARGIN * 2)));
        int panelHeight = DEATH_CAM_PANEL_HEIGHT;
        int x = centerX - panelWidth / 2;
        int minY = (screenHeight / 2) + DEATH_CAM_PANEL_MIN_CENTER_OFFSET;
        int preferredY = (screenHeight * 2 / 3) - (panelHeight / 2);
        int maxY = Math.max(8, screenHeight - panelHeight - DEATH_CAM_PANEL_BOTTOM_MARGIN);
        int y = maxY < minY ? maxY : Math.min(maxY, Math.max(minY, preferredY));
        int textMaxWidth = Math.max(24, panelWidth - (DEATH_CAM_PANEL_TEXT_PADDING * 2));

        graphics.fillGradient(x, y, x + panelWidth, y + panelHeight, 0xCC2B0E0E, 0xCC120A0A);
        graphics.fill(x, y + panelHeight - 2, x + panelWidth, y + panelHeight, 0xFFFF5A5A);

        GuiTextHelper.drawCenteredEllipsizedString(
                graphics,
                font,
                Component.translatable("hud.codpattern.tdm.death.title").getString(),
                centerX,
                y + 7,
                textMaxWidth,
                0xFFFFC4C4,
                true);
        GuiTextHelper.drawCenteredEllipsizedString(
                graphics,
                font,
                Component.translatable("hud.codpattern.tdm.death.killer", ClientTdmState.killerName()).getString(),
                centerX,
                y + 20,
                textMaxWidth,
                0xFFF1F1F1,
                true);
        GuiTextHelper.drawCenteredEllipsizedString(
                graphics,
                font,
                Component.translatable("hud.codpattern.tdm.death.respawn",
                        String.format(Locale.ROOT, "%.1f", ClientTdmState.deathCamTicks() / 20.0f)).getString(),
                centerX,
                y + 34,
                textMaxWidth,
                0xFFD8D8D8,
                true);
    }

    private String buildTimerText() {
        return switch (ClientTdmState.currentPhase()) {
            case "PLAYING", "COUNTDOWN", "WARMUP" -> formatTime(ClientTdmState.remainingTimeTicks());
            case "ENDED" -> "00:00";
            default -> "--:--";
        };
    }

    private String buildEndResultTitle() {
        if (getKortacScore() > getSpecgruScore()) {
            return Component.translatable("hud.codpattern.tdm.result.kortac_win_title").getString();
        }
        if (getSpecgruScore() > getKortacScore()) {
            return Component.translatable("hud.codpattern.tdm.result.specgru_win_title").getString();
        }
        return Component.translatable("hud.codpattern.tdm.result.draw_title").getString();
    }

    private String buildEndResultText() {
        if (getKortacScore() > getSpecgruScore()) {
            return Component.translatable("hud.codpattern.tdm.announce.kortac_win", getKortacScore(),
                    getSpecgruScore()).getString();
        }
        if (getSpecgruScore() > getKortacScore()) {
            return Component.translatable("hud.codpattern.tdm.announce.specgru_win", getSpecgruScore(),
                    getKortacScore()).getString();
        }
        return Component.translatable("hud.codpattern.tdm.announce.draw", getKortacScore(),
                getSpecgruScore()).getString();
    }

    private String phaseShortText(String phase) {
        String key = "hud.codpattern.tdm.phase_short." + phase.toLowerCase(Locale.ROOT);
        String text = Component.translatable(key).getString();
        if (key.equals(text)) {
            return phaseText(phase);
        }
        return text;
    }

    private String phaseText(String phase) {
        return Component.translatable("hud.codpattern.tdm.phase." + phase.toLowerCase(Locale.ROOT)).getString();
    }

    private String teamShort(String teamKey) {
        String key = "hud.codpattern.tdm.team." + teamKey + "_short";
        String text = Component.translatable(key).getString();
        if (key.equals(text)) {
            return teamKey.substring(0, 1).toUpperCase(Locale.ROOT);
        }
        return text;
    }

    private boolean isKillFeedPhase() {
        return "WARMUP".equals(ClientTdmState.currentPhase()) || "PLAYING".equals(ClientTdmState.currentPhase());
    }

    private int resolveKillFeedTeamColor(String playerName, int fallbackColor) {
        if (playerName == null || playerName.isBlank()) {
            return fallbackColor;
        }

        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null && playerName.equals(localPlayer.getGameProfile().getName())) {
            return 0xFFFFD54A;
        }

        for (Map.Entry<String, List<PlayerInfo>> entry : ClientTdmState.teamPlayersSnapshot().entrySet()) {
            for (PlayerInfo player : entry.getValue()) {
                if (!playerName.equals(player.name())) {
                    continue;
                }
                return switch (entry.getKey().toLowerCase(Locale.ROOT)) {
                    case TdmTeamNames.KORTAC -> 0xFFE35A5A;
                    case TdmTeamNames.SPECGRU -> 0xFF66A6FF;
                    default -> fallbackColor;
                };
            }
        }
        return fallbackColor;
    }

    private float killFeedTextScale() {
        return GuiTextHelper.referenceScale() * KILL_FEED_TEXT_SCALE_MULTIPLIER;
    }

    private int killFeedLineHeight(Font font) {
        return Math.max(1, Math.round(font.lineHeight * killFeedTextScale()));
    }

    private int killFeedTextWidth(Font font, String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.round(font.width(text) * killFeedTextScale());
    }

    private String ellipsizeKillFeed(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        float scale = killFeedTextScale();
        if (scale <= 0.0f) {
            return text == null ? "" : text;
        }
        return GuiTextHelper.ellipsize(font, text, Math.max(1, (int) Math.floor(maxWidth / scale)));
    }

    private KillFeedRowLayout measureKillFeedRow(Font font, KillFeedEntry entry, int maxWidth) {
        return entry.blunder()
                ? measureKillFeedBlunderRow(font, entry, maxWidth)
                : measureKillFeedStandardRow(font, entry, maxWidth);
    }

    private KillFeedRowLayout measureKillFeedBlunderRow(Font font, KillFeedEntry entry, int maxWidth) {
        int gap = GuiTextHelper.referenceScaled(KILL_FEED_TEXT_GAP);
        int killerWidth = killFeedTextWidth(font, entry.killerName());
        int blunderWidth = killFeedTextWidth(font, Component.translatable("hud.codpattern.tdm.kill_feed.blunder").getString());
        if (killerWidth + gap + blunderWidth <= maxWidth) {
            return new KillFeedRowLayout(killerWidth, blunderWidth, 0, gap, 0);
        }

        int[] widths = allocateKillFeedTextWidths(Math.max(0, maxWidth - gap), killerWidth, blunderWidth);
        return new KillFeedRowLayout(widths[0], widths[1], 0, gap, 0);
    }

    private KillFeedRowLayout measureKillFeedStandardRow(Font font, KillFeedEntry entry, int maxWidth) {
        int gap = GuiTextHelper.referenceScaled(KILL_FEED_TEXT_GAP);
        int killerWidth = killFeedTextWidth(font, entry.killerName());
        int victimWidth = killFeedTextWidth(font, entry.victimName());
        int centerWidth = killFeedWeaponWidth(font, entry.weaponStack());
        if (killerWidth + gap + centerWidth + gap + victimWidth <= maxWidth) {
            return new KillFeedRowLayout(killerWidth, centerWidth, victimWidth, gap, gap);
        }

        int[] widths = allocateKillFeedTextWidths(Math.max(0, maxWidth - centerWidth - (gap * 2)), killerWidth, victimWidth);
        return new KillFeedRowLayout(widths[0], centerWidth, widths[1], gap, gap);
    }

    private int[] allocateKillFeedTextWidths(int availableWidth, int firstDesiredWidth, int secondDesiredWidth) {
        if (availableWidth <= 0) {
            return new int[] { 0, 0 };
        }
        if (firstDesiredWidth + secondDesiredWidth <= availableWidth) {
            return new int[] { firstDesiredWidth, secondDesiredWidth };
        }

        int minReadableWidth = Math.min(GuiTextHelper.referenceScaled(24), availableWidth / 2);
        int firstWidth = Math.min(firstDesiredWidth, minReadableWidth);
        int secondWidth = Math.min(secondDesiredWidth, minReadableWidth);
        int remainingWidth = Math.max(0, availableWidth - firstWidth - secondWidth);

        int firstNeed = Math.max(0, firstDesiredWidth - firstWidth);
        int secondNeed = Math.max(0, secondDesiredWidth - secondWidth);
        int totalNeed = firstNeed + secondNeed;
        if (remainingWidth > 0 && totalNeed > 0) {
            int firstExtra = Math.min(firstNeed, Math.round(remainingWidth * (firstNeed / (float) totalNeed)));
            firstWidth += firstExtra;
            remainingWidth -= firstExtra;

            int secondExtra = Math.min(secondNeed, remainingWidth);
            secondWidth += secondExtra;
            remainingWidth -= secondExtra;

            if (remainingWidth > 0) {
                int firstRemainder = Math.min(firstDesiredWidth - firstWidth, remainingWidth);
                firstWidth += firstRemainder;
                remainingWidth -= firstRemainder;
            }
            if (remainingWidth > 0) {
                secondWidth += Math.min(secondDesiredWidth - secondWidth, remainingWidth);
            }
        }

        return new int[] { firstWidth, secondWidth };
    }

    private int killFeedWeaponWidth(Font font, ItemStack weaponStack) {
        if (weaponStack != null && !weaponStack.isEmpty()) {
            return TaczClientApi.getGunHudTexture(weaponStack) != null
                    ? GuiTextHelper.referenceScaled(KILL_FEED_TEXTURE_BASE_WIDTH)
                    : GuiTextHelper.referenceScaled(KILL_FEED_ICON_BASE_SIZE);
        }
        String fallbackText = Component.translatable("hud.codpattern.tdm.kill_feed.fallback").getString();
        int fallbackWidth = Math.max(KILL_FEED_FALLBACK_BASE_WIDTH, font.width(fallbackText) + 6);
        return GuiTextHelper.referenceScaled(fallbackWidth);
    }

    private void drawKillFeedString(GuiGraphics graphics, Font font, String text, float x, float y, int color, boolean shadow) {
        if (text == null || text.isEmpty()) {
            return;
        }
        float scale = killFeedTextScale();
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(font, text, 0, 0, color, shadow);
        graphics.pose().popPose();
    }

    private void drawKillFeedCenteredString(GuiGraphics graphics, Font font, String text, float centerX, float y, int color,
            boolean shadow) {
        drawKillFeedString(graphics, font, text, centerX - killFeedTextWidth(font, text) / 2.0f, y, color, shadow);
    }

    private int phaseAccentColor() {
        return switch (ClientTdmState.currentPhase()) {
            case "COUNTDOWN" -> 0xFFF0C75E;
            case "WARMUP" -> 0xFFF5A24D;
            case "PLAYING" -> 0xFF7BFF9A;
            case "ENDED" -> endAccentColor();
            default -> 0xFFBDBDBD;
        };
    }

    private int endAccentColor() {
        if (getKortacScore() > getSpecgruScore()) {
            return 0xFFE35A5A;
        }
        if (getSpecgruScore() > getKortacScore()) {
            return 0xFF66A6FF;
        }
        return 0xFFFFD700;
    }

    private int getKortacScore() {
        return ClientTdmState.getTeamScore("kortac", ClientTdmState.team1Score());
    }

    private int getSpecgruScore() {
        return ClientTdmState.getTeamScore("specgru", ClientTdmState.team2Score());
    }

    private int withAlpha(int color, int alpha) {
        return (clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    private void drawCenteredString(GuiGraphics graphics, Font font, String text, int centerX, int y, int color) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, true);
    }

    private void drawScaledCenteredString(GuiGraphics graphics, Font font, String text, int centerX, int y, int color,
            float scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, y, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(font, text, -font.width(text) / 2, 0, color, true);
        graphics.pose().popPose();
    }

    private int formatTimeSeconds(int ticks) {
        return Math.max(0, ticks / 20);
    }

    private String formatTime(int ticks) {
        int secondsTotal = formatTimeSeconds(ticks);
        int minutes = secondsTotal / 60;
        int seconds = secondsTotal % 60;
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private float smoothstep(float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        return t * t * (3.0f - 2.0f * t);
    }
}
