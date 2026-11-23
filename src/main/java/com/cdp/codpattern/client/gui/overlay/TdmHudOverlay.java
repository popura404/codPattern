package com.cdp.codpattern.client.gui.overlay;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.tdm.model.TdmTeamNames;
import com.cdp.codpattern.client.ClientMatchState;
import com.cdp.codpattern.client.ClientTdmState;
import com.cdp.codpattern.client.TdmCombatMarkerTracker;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import com.cdp.codpattern.client.input.ThrowableKeyMappings;
import com.cdp.codpattern.client.state.KillFeedEntry;
import com.cdp.codpattern.client.state.TdmSettlementState;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterClientCache;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfig;
import com.cdp.codpattern.core.throwable.ThrowableInventoryService;
import com.cdp.codpattern.core.throwable.ThrowableInventoryState;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import com.cdp.codpattern.compat.tacz.client.TaczClientApi;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class TdmHudOverlay implements IGuiOverlay {

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
    private static final double INVINCIBILITY_MARKER_HEAD_OFFSET = 0.92D;
    private static final int THROWABLE_HUD_ICON_SIZE = 16;
    private static final int THROWABLE_HUD_SLOT_GAP = 14;
    private static final int THROWABLE_HUD_TEXT_GAP = 2;
    private static final int THROWABLE_HUD_BOTTOM_MARGIN = 10;
    private static final int THROWABLE_HUD_RIGHT_RESERVED_WIDTH = 116;
    private static final int THROWABLE_HUD_RIGHT_GAP = 10;
    private static final int COUNTDOWN_ACTION_BAR_Y_OFFSET = 68;
    private static final int WARMUP_MAP_MARGIN_LEFT = 20;
    private static final int WARMUP_MAP_INFO_BOTTOM_OFFSET = 62;
    private static final int WARMUP_MAP_INFO_LINE_GAP = 28;
    private static final float WARMUP_MAP_NAME_SCALE = 2.0f;
    private static final int PLAYER_STATUS_MARGIN_LEFT = 8;
    private static final int PLAYER_STATUS_MARGIN_RIGHT = 8;
    private static final int PLAYER_STATUS_BOTTOM_MARGIN = 16;
    private static final int PLAYER_STATUS_BAR_WIDTH = 148;
    private static final int PLAYER_STATUS_BAR_MIN_WIDTH = 72;
    private static final int PLAYER_STATUS_BAR_HEIGHT = 2;
    private static final int PLAYER_STATUS_TEXT_GAP = 3;
    private static final int PLAYER_STATUS_HEALTH_COLOR = 0xFFE53935;
    private static final int DAMAGE_VIGNETTE_MAX_ALPHA = 78;
    private static final int DAMAGE_VIGNETTE_DURATION_MULTIPLIER = 2;
    private static final int DAMAGE_VIGNETTE_EDGE_THICKNESS = 50;
    private static final int DAMAGE_VIGNETTE_EDGE_SEGMENTS = 16;
    private static final int DAMAGE_VIGNETTE_COLOR = 0xFFE02020;
    private static final float LOW_HEALTH_BREATH_THRESHOLD = 0.35f;
    private static final int LOW_HEALTH_BREATH_MIN_ALPHA = 30;
    private static final int LOW_HEALTH_BREATH_MAX_ALPHA = 112;
    private static final int LOW_HEALTH_BREATH_EDGE_THICKNESS = 92;
    private static final float LOW_HEALTH_BREATH_PERIOD_TICKS = 18.0f;
    private static final int LOW_HEALTH_BREATH_COLOR = 0xFFD91414;
    private static final int LOW_HEALTH_DARKEN_MAX_ALPHA = 128;
    public static final TdmHudOverlay INSTANCE = new TdmHudOverlay();

    private UUID damageVignettePlayerId;
    private int damageVignetteTicksRemaining = 0;
    private int damageVignetteTotalTicks = 0;
    private int lastDamageVignettePlayerTick = -1;
    private int lastObservedHurtTime = 0;

    private record ScreenProjection(int x, int y, double depth) {
    }

    private record KillFeedRowLayout(int killerMaxWidth, int centerWidth, int victimMaxWidth, int firstGap, int secondGap) {
    }

    private record RoomDisplayInfo(String gameType, String mapName) {
    }

    public static boolean shouldReplaceVanillaPlayerHud() {
        if (!hasCurrentTdmRoomContext()) {
            return false;
        }
        if (ClientTdmState.isDead()) {
            return true;
        }
        return isMatchHudPhase(ClientTdmState.currentPhase());
    }

    public static boolean isShowingSettlement() {
        return ClientMatchState.settlement() != null;
    }

    private static boolean hasCurrentTdmRoomContext() {
        try {
            return ClientTdmState.hasRoomContext() && BuiltInGameModes.isTeamMatchRoom(ClientTdmState.roomContextName());
        } catch (ExceptionInInitializerError | NoClassDefFoundError ignored) {
            return false;
        }
    }

    public void renderSharedPlayerScreenEffects(GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        renderLowHealthBreath(graphics, partialTick, screenWidth, screenHeight, true);
        renderDamageVignette(graphics, partialTick, screenWidth, screenHeight, true);
    }

    public void renderSharedPlayerStatusHud(GuiGraphics graphics, Font font, int screenWidth, int screenHeight,
            String statusLabel, int statusColor) {
        renderPlayerStatusHud(graphics, font, screenWidth, screenHeight, true, statusLabel, statusColor);
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        if (!shouldRenderHud()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        TdmSettlementState settlement = ClientMatchState.settlement();
        if (settlement != null) {
            UUID localId = minecraft.player == null ? null : minecraft.player.getUUID();
            TdmSettlementRenderer.render(graphics, font, screenWidth, screenHeight,
                    settlement.elapsedSeconds(System.nanoTime(), minecraft.isPaused()),
                    settlement.kortacScore(), settlement.specgruScore(), settlement.teamOf(localId), localId,
                    settlement.rosters(), resolveModeName(resolveRoomDisplayInfo().gameType()));
            return;
        }
        renderCountdownOverlay(graphics, font, screenWidth, screenHeight);
        if (ClientTdmState.isBlackoutActive()) {
            return;
        }
        int centerX = screenWidth / 2;

        renderLowHealthBreath(graphics, partialTick, screenWidth, screenHeight);
        renderDamageVignette(graphics, partialTick, screenWidth, screenHeight);
        renderLeftScorePanel(graphics, font, screenWidth, screenHeight);
        renderKillFeed(graphics, font, screenWidth, screenHeight);
        renderPhaseAnnouncement(graphics, font, centerX, screenHeight);
        renderThrowableSlots(graphics, font, screenWidth, screenHeight);
        renderPlayerStatusHud(graphics, font, screenWidth, screenHeight);
        renderCombatMarkers(graphics, partialTick, screenWidth, screenHeight);
        renderDeathCamPanel(graphics, font, centerX, screenWidth, screenHeight);
    }

    private static boolean isMatchHudPhase(String phase) {
        return "COUNTDOWN".equals(phase)
                || "WARMUP".equals(phase)
                || "PLAYING".equals(phase)
                || "ENDED".equals(phase);
    }

    private boolean shouldRenderPlayerStatusHud() {
        if (!shouldReplaceVanillaPlayerHud()) {
            return false;
        }
        return !"ENDED".equals(ClientTdmState.currentPhase());
    }

    private boolean shouldRenderHud() {
        if (!hasCurrentTdmRoomContext()) {
            return false;
        }
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

    private void renderCountdownOverlay(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        String phase = ClientTdmState.currentPhase();
        if (!"COUNTDOWN".equals(phase) && !"WARMUP".equals(phase)) {
            return;
        }

        int blackoutAlpha = clamp((int) (ClientTdmState.getBlackoutAlpha() * 255.0f), 0, 255);
        if (blackoutAlpha > 0) {
            graphics.fill(0, 0, screenWidth, screenHeight, blackoutAlpha << 24);
        }

        if ("COUNTDOWN".equals(phase)) {
            renderActionbarCountdown(graphics, font, screenWidth, screenHeight);
            return;
        }

        renderWarmupCountdown(graphics, font, screenWidth, screenHeight);
        renderWarmupMapInfo(graphics, font, screenWidth, screenHeight);
    }

    private void renderActionbarCountdown(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        int secondsLeft = Math.max(1, (ClientTdmState.remainingTimeTicks() + 19) / 20);
        String message = Component.translatable("hud.codpattern.tdm.actionbar.countdown", secondsLeft).getString();
        if (message.isBlank()) {
            return;
        }

        int messageWidth = font.width(message);
        int centerX = screenWidth / 2;
        int y = screenHeight - COUNTDOWN_ACTION_BAR_Y_OFFSET;
        graphics.fill(
                centerX - messageWidth / 2 - 4,
                y - 4,
                centerX + messageWidth / 2 + 4,
                y + font.lineHeight + 4,
                0x55000000);
        graphics.drawString(font, message, centerX - messageWidth / 2, y, 0xFFFFFFFF, true);
    }

    private void renderWarmupCountdown(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        int secondsLeft = Math.max(1, (ClientTdmState.remainingTimeTicks() + 19) / 20);
        String text = String.valueOf(secondsLeft);
        float scale = 4.9f;
        int y = screenHeight / 2 - Math.round((font.lineHeight * scale) / 2.0f);

        graphics.pose().pushPose();
        graphics.pose().translate(screenWidth / 2.0f, y, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(font, text, -font.width(text) / 2, 0, 0xFFFFE28A, true);
        graphics.pose().popPose();
    }

    private void renderWarmupMapInfo(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        int infoAlpha = clamp((int) (ClientTdmState.getBlackoutInfoAlpha() * 255.0f), 0, 255);
        if (infoAlpha <= 0) {
            return;
        }

        RoomDisplayInfo displayInfo = resolveRoomDisplayInfo();
        String modeName = resolveModeName(displayInfo.gameType());
        String mapName = displayInfo.mapName();
        String worldTime = formatWorldTime(minecraft.level);
        int baseY = screenHeight - WARMUP_MAP_INFO_BOTTOM_OFFSET - WARMUP_MAP_INFO_LINE_GAP;

        renderWarmupScaledMapInfoLine(graphics, font, modeName, screenWidth, baseY,
                withAlpha(0xFFFFFFFF, infoAlpha));
        renderWarmupScaledMapInfoLine(graphics, font, mapName, screenWidth, baseY + WARMUP_MAP_INFO_LINE_GAP,
                withAlpha(0xFFFFFFFF, infoAlpha));
        graphics.drawString(font, worldTime, WARMUP_MAP_MARGIN_LEFT, baseY + WARMUP_MAP_INFO_LINE_GAP * 2,
                withAlpha(0xFFEAEAEA, infoAlpha), true);
    }

    private void renderWarmupScaledMapInfoLine(GuiGraphics graphics, Font font, String text, int screenWidth, int y,
            int color) {
        String fittedText = GuiTextHelper.ellipsize(
                font,
                text == null ? "" : text,
                Math.max(1, (int) ((screenWidth - WARMUP_MAP_MARGIN_LEFT - 20) / WARMUP_MAP_NAME_SCALE)));

        if (fittedText.isEmpty()) {
            return;
        }

        graphics.pose().pushPose();
        graphics.pose().translate(WARMUP_MAP_MARGIN_LEFT, y, 0.0f);
        graphics.pose().scale(WARMUP_MAP_NAME_SCALE, WARMUP_MAP_NAME_SCALE, 1.0f);
        graphics.drawString(font, fittedText, 0, 0, color, true);
        graphics.pose().popPose();
    }

    private RoomDisplayInfo resolveRoomDisplayInfo() {
        String roomContext = ClientTdmState.syncedMapName();
        if (roomContext == null || roomContext.isBlank()) {
            roomContext = ClientTdmState.roomContextName();
        }
        if (roomContext == null || roomContext.isBlank()) {
            return new RoomDisplayInfo("", "");
        }

        try {
            RoomId roomId = RoomId.decode(roomContext);
            return new RoomDisplayInfo(roomId.gameType(), roomId.mapName());
        } catch (IllegalArgumentException ignored) {
            return new RoomDisplayInfo("", roomContext);
        }
    }

    private String resolveModeName(String gameType) {
        String normalized = gameType == null ? "" : gameType.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "";
        }
        String displayNameKey = "tdm".equals(normalized)
                ? "mode.codpattern.teamdeathmatch"
                : GameModeRegistry.getOrDefault(normalized).displayNameKey();
        return Component.translatable(displayNameKey).getString();
    }

    private String formatWorldTime(Level level) {
        if (level == null) {
            return "--:--";
        }

        long dayTicks = Math.floorMod(level.getDayTime() + 6000L, 24000L);
        int hours = (int) (dayTicks / 1000L);
        int minutes = (int) ((dayTicks % 1000L) * 60L / 1000L);
        return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
    }

    private void renderLeftScorePanel(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        int x = 8;
        int y = screenHeight >= 500 ? 92 : 70;
        int rowWidth = 120;
        int rowHeight = 18;
        int rowGap = 3;
        int timerY = y + (rowHeight * 2) + rowGap + 4;
        String timerText = buildTimerText();
        int phaseY = timerText.isBlank() ? timerY : timerY + 10;
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

        if (!timerText.isBlank()) {
            graphics.drawString(font, timerText, x, timerY, 0xFFB08A3E, false);
        }
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

    private void renderLowHealthBreath(GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        renderLowHealthBreath(graphics, partialTick, screenWidth, screenHeight, shouldReplaceVanillaPlayerHud());
    }

    private void renderLowHealthBreath(GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight,
            boolean enabled) {
        if (!enabled) {
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.getHealth() <= 0.0f) {
            return;
        }

        float maxHealth = Math.max(1.0f, player.getMaxHealth());
        float healthRatio = Mth.clamp(player.getHealth() / maxHealth, 0.0f, 1.0f);
        if (healthRatio > LOW_HEALTH_BREATH_THRESHOLD) {
            return;
        }

        float severity = 1.0f - (healthRatio / LOW_HEALTH_BREATH_THRESHOLD);
        float wave = 0.5f + 0.5f * Mth.sin(((player.tickCount + partialTick) / LOW_HEALTH_BREATH_PERIOD_TICKS)
                * Mth.TWO_PI);
        float pulse = 0.45f + (0.55f * wave);
        int peakAlpha = Math.round(Mth.lerp(severity, LOW_HEALTH_BREATH_MIN_ALPHA, LOW_HEALTH_BREATH_MAX_ALPHA));
        int alpha = clamp(Math.round(peakAlpha * pulse), 0, LOW_HEALTH_BREATH_MAX_ALPHA);
        int darkenAlpha = clamp(Math.round(LOW_HEALTH_DARKEN_MAX_ALPHA * severity * pulse),
                0,
                LOW_HEALTH_DARKEN_MAX_ALPHA);
        if (darkenAlpha > 0) {
            graphics.fill(0, 0, screenWidth, screenHeight, darkenAlpha << 24);
        }
        drawScreenEdgeVignette(graphics, screenWidth, screenHeight, LOW_HEALTH_BREATH_EDGE_THICKNESS,
                alpha, LOW_HEALTH_BREATH_COLOR);
    }

    private void renderDamageVignette(GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        renderDamageVignette(graphics, partialTick, screenWidth, screenHeight, shouldReplaceVanillaPlayerHud());
    }

    private void renderDamageVignette(GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight,
            boolean enabled) {
        if (!enabled) {
            resetDamageVignetteState();
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            resetDamageVignetteState();
            return;
        }

        updateDamageVignetteState(player);
        if (damageVignetteTicksRemaining <= 0 || damageVignetteTotalTicks <= 0) {
            return;
        }

        float remaining = Mth.clamp(
                (damageVignetteTicksRemaining - partialTick) / (float) damageVignetteTotalTicks,
                0.0f,
                1.0f);
        int alpha = clamp(Math.round(DAMAGE_VIGNETTE_MAX_ALPHA * remaining), 0, DAMAGE_VIGNETTE_MAX_ALPHA);
        if (alpha <= 0) {
            return;
        }

        drawScreenEdgeVignette(graphics, screenWidth, screenHeight, DAMAGE_VIGNETTE_EDGE_THICKNESS,
                alpha, DAMAGE_VIGNETTE_COLOR);
    }

    private void updateDamageVignetteState(LocalPlayer player) {
        UUID playerId = player.getUUID();
        if (!playerId.equals(damageVignettePlayerId)) {
            resetDamageVignetteState();
            damageVignettePlayerId = playerId;
        }

        int playerTick = player.tickCount;
        if (lastDamageVignettePlayerTick < 0) {
            lastDamageVignettePlayerTick = playerTick;
        } else if (playerTick > lastDamageVignettePlayerTick) {
            int elapsedTicks = playerTick - lastDamageVignettePlayerTick;
            damageVignetteTicksRemaining = Math.max(0, damageVignetteTicksRemaining - elapsedTicks);
            lastDamageVignettePlayerTick = playerTick;
        } else if (playerTick < lastDamageVignettePlayerTick) {
            resetDamageVignetteState();
            damageVignettePlayerId = playerId;
            lastDamageVignettePlayerTick = playerTick;
        }

        int hurtTime = Math.max(0, player.hurtTime);
        if (hurtTime > 0 && (lastObservedHurtTime <= 0 || hurtTime > lastObservedHurtTime)) {
            int totalTicks = Math.max(1, player.hurtDuration * DAMAGE_VIGNETTE_DURATION_MULTIPLIER);
            damageVignetteTotalTicks = totalTicks;
            damageVignetteTicksRemaining = totalTicks;
        }
        lastObservedHurtTime = hurtTime;
    }

    private void resetDamageVignetteState() {
        damageVignettePlayerId = null;
        damageVignetteTicksRemaining = 0;
        damageVignetteTotalTicks = 0;
        lastDamageVignettePlayerTick = -1;
        lastObservedHurtTime = 0;
    }

    private void drawScreenEdgeVignette(GuiGraphics graphics, int screenWidth, int screenHeight,
            int thickness, int alpha, int color) {
        if (alpha <= 0 || screenWidth <= 0 || screenHeight <= 0) {
            return;
        }

        int horizontalThickness = Math.min(thickness, Math.max(0, screenWidth / 2));
        int verticalThickness = Math.min(thickness, Math.max(0, screenHeight / 2));
        int edgeColor = withAlpha(color, alpha);
        int transparentColor = withAlpha(color, 0);

        if (verticalThickness > 0) {
            graphics.fillGradient(0, 0, screenWidth, verticalThickness, edgeColor, transparentColor);
            graphics.fillGradient(0, screenHeight - verticalThickness, screenWidth, screenHeight,
                    transparentColor, edgeColor);
        }
        if (horizontalThickness > 0) {
            drawHorizontalVignetteEdge(graphics, 0, verticalThickness, horizontalThickness,
                    Math.max(verticalThickness, screenHeight - verticalThickness), alpha, true, color);
            drawHorizontalVignetteEdge(graphics, screenWidth - horizontalThickness, verticalThickness, screenWidth,
                    Math.max(verticalThickness, screenHeight - verticalThickness), alpha, false, color);
        }
    }

    private void drawHorizontalVignetteEdge(GuiGraphics graphics, int left, int top, int right, int bottom,
            int alpha, boolean fadeRight, int color) {
        int width = right - left;
        if (width <= 0 || bottom <= top) {
            return;
        }

        int segments = Math.max(1, Math.min(DAMAGE_VIGNETTE_EDGE_SEGMENTS, width));
        for (int i = 0; i < segments; i++) {
            int segmentLeft = left + (width * i) / segments;
            int segmentRight = left + (width * (i + 1)) / segments;
            if (segmentRight <= segmentLeft) {
                continue;
            }

            float edgeProgress = (segments == 1) ? 0.0f : (float) i / (segments - 1);
            float fade = fadeRight ? 1.0f - edgeProgress : edgeProgress;
            int segmentAlpha = clamp(Math.round(alpha * fade), 0, alpha);
            if (segmentAlpha <= 0) {
                continue;
            }
            graphics.fill(segmentLeft, top, segmentRight, bottom, withAlpha(color, segmentAlpha));
        }
    }

    private void renderPlayerStatusHud(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        if (!shouldRenderPlayerStatusHud()) {
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        String teamName = resolveLocalTeamName(player);
        renderPlayerStatusHud(graphics, font, screenWidth, screenHeight, player, teamNameLabel(teamName), teamAccent(teamName));
    }

    private void renderPlayerStatusHud(GuiGraphics graphics, Font font, int screenWidth, int screenHeight,
            boolean enabled, String statusLabel, int statusColor) {
        if (!enabled) {
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        renderPlayerStatusHud(graphics, font, screenWidth, screenHeight, player, statusLabel, statusColor);
    }

    private void renderPlayerStatusHud(GuiGraphics graphics, Font font, int screenWidth, int screenHeight,
            LocalPlayer player, String statusLabel, int statusColor) {
        int x = PLAYER_STATUS_MARGIN_LEFT;
        int maxWidth = screenWidth - x - PLAYER_STATUS_MARGIN_RIGHT;
        int barWidth = Math.min(PLAYER_STATUS_BAR_WIDTH, maxWidth);
        if (barWidth < PLAYER_STATUS_BAR_MIN_WIDTH) {
            return;
        }

        int textY = screenHeight - PLAYER_STATUS_BOTTOM_MARGIN - font.lineHeight;
        int barY = textY - PLAYER_STATUS_TEXT_GAP - PLAYER_STATUS_BAR_HEIGHT;
        if (barY < 0) {
            return;
        }

        float maxHealth = Math.max(1.0f, player.getMaxHealth());
        float healthRatio = Mth.clamp(player.getHealth() / maxHealth, 0.0f, 1.0f);
        int filledWidth = Math.round(barWidth * healthRatio);
        if (player.getHealth() > 0.0f && filledWidth <= 0) {
            filledWidth = 1;
        }

        graphics.fill(x - 1, barY - 1, x + barWidth + 1, barY + PLAYER_STATUS_BAR_HEIGHT + 1, 0x99000000);
        graphics.fill(x, barY, x + barWidth, barY + PLAYER_STATUS_BAR_HEIGHT, 0xFFFFFFFF);
        if (filledWidth > 0) {
            graphics.fill(x, barY, x + filledWidth, barY + PLAYER_STATUS_BAR_HEIGHT, PLAYER_STATUS_HEALTH_COLOR);
        }

        String teamText = normalizeStatusLabel(statusLabel);
        int teamColor = statusColor;
        int teamMaxWidth = Math.min(font.width(teamText), Math.max(18, barWidth / 2));
        String fittedTeam = GuiTextHelper.ellipsize(font, teamText, teamMaxWidth);
        int teamWidth = font.width(fittedTeam);

        String playerId = player.getGameProfile().getName();
        int idMaxWidth = Math.max(1, barWidth - teamWidth - 5);
        String fittedPlayerId = GuiTextHelper.ellipsize(font, playerId, idMaxWidth);

        graphics.drawString(font, fittedTeam, x, textY, teamColor, true);
        graphics.drawString(font, fittedPlayerId, x + teamWidth + 5, textY, 0xFFF4F4F4, true);
    }

    private String normalizeStatusLabel(String statusLabel) {
        return statusLabel == null || statusLabel.isBlank() ? "-" : statusLabel;
    }

    private String resolveLocalTeamName(LocalPlayer player) {
        UUID playerId = player.getUUID();
        for (Map.Entry<String, List<PlayerInfo>> entry : ClientTdmState.teamPlayersSnapshot().entrySet()) {
            for (PlayerInfo playerInfo : entry.getValue()) {
                if (playerInfo != null && playerId.equals(playerInfo.uuid())) {
                    return entry.getKey();
                }
            }
        }
        return "";
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
        String timerText = buildTimerText();
        int phaseY = timerText.isBlank() ? timerY : timerY + 10;
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

    private void renderThrowableSlots(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        if (!ClientTdmState.hasRoomContext()) {
            return;
        }

        WeaponFilterConfig config = WeaponFilterClientCache.get();
        if (config != null && !config.isThrowablesEnabled()) {
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        int activeSlot = ThrowableInventoryService.getActiveSlot(player);
        String[] keyTexts = new String[ThrowableInventoryState.SLOT_COUNT];
        int keyColumnWidth = 0;
        for (int i = 0; i < ThrowableInventoryState.SLOT_COUNT; i++) {
            keyTexts[i] = ThrowableKeyMappings.getBoundLabel(i).getString();
            keyColumnWidth = Math.max(keyColumnWidth, font.width(keyTexts[i]));
        }

        int slotWidth = keyColumnWidth + 4 + THROWABLE_HUD_ICON_SIZE;
        int totalWidth = (ThrowableInventoryState.SLOT_COUNT * slotWidth)
                + ((ThrowableInventoryState.SLOT_COUNT - 1) * THROWABLE_HUD_SLOT_GAP);
        int startX = screenWidth - THROWABLE_HUD_RIGHT_RESERVED_WIDTH - THROWABLE_HUD_RIGHT_GAP - totalWidth;
        int iconY = screenHeight - THROWABLE_HUD_BOTTOM_MARGIN - font.lineHeight - THROWABLE_HUD_TEXT_GAP
                - THROWABLE_HUD_ICON_SIZE;
        int keyY = iconY + ((THROWABLE_HUD_ICON_SIZE - font.lineHeight) / 2);
        int countY = iconY + THROWABLE_HUD_ICON_SIZE + THROWABLE_HUD_TEXT_GAP;

        for (int i = 0; i < ThrowableInventoryState.SLOT_COUNT; i++) {
            int slotX = startX + (i * (slotWidth + THROWABLE_HUD_SLOT_GAP));
            int iconX = slotX + keyColumnWidth + 4;
            boolean active = activeSlot == i;

            ItemStack stack = ThrowableInventoryService.getDisplayStack(player, i);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, iconX, iconY);
            }

            graphics.drawString(font, keyTexts[i], slotX + keyColumnWidth - font.width(keyTexts[i]), keyY,
                    active ? 0xFFF8D27B : 0xFFB8C0CA, false);

            String countText = String.valueOf(stack.getCount());
            graphics.drawString(font, countText,
                    iconX + ((THROWABLE_HUD_ICON_SIZE - font.width(countText)) / 2), countY,
                    stack.isEmpty() ? 0xFF6E7681 : (active ? 0xFFF2F4F7 : 0xFFD9DDE3), false);
        }
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

    private ScreenProjection projectWorldToScreen(Minecraft minecraft,
            float partialTick,
            Vec3 worldPos,
            int screenWidth,
            int screenHeight) {
        if (minecraft.gameRenderer == null) {
            return null;
        }
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (camera == null || !camera.isInitialized()) {
            return null;
        }

        Vec3 cameraPos = camera.getPosition();
        Vec3 relative = worldPos.subtract(cameraPos);

        Vec3 forward = toVec3(camera.getLookVector()).normalize();
        Vec3 up = toVec3(camera.getUpVector()).normalize();
        Vec3 right = toVec3(camera.getLeftVector()).scale(-1.0D).normalize();

        double depth = relative.dot(forward);
        if (depth <= 0.05D) {
            return null;
        }

        double horizontal = relative.dot(right);
        double vertical = relative.dot(up);

        Matrix4f projection = RenderSystem.getProjectionMatrix();
        double focalLengthX = screenWidth * projection.m00() * 0.5D;
        double focalLengthY = screenHeight * projection.m11() * 0.5D;

        int screenX = (int) Math.round((screenWidth * 0.5D) + (horizontal * focalLengthX / depth));
        int screenY = (int) Math.round((screenHeight * 0.5D) - (vertical * focalLengthY / depth));

        if (screenX < -COMBAT_MARKER_SCREEN_MARGIN || screenX > screenWidth + COMBAT_MARKER_SCREEN_MARGIN
                || screenY < -COMBAT_MARKER_SCREEN_MARGIN || screenY > screenHeight + COMBAT_MARKER_SCREEN_MARGIN) {
            return null;
        }

        return new ScreenProjection(screenX, screenY, depth);
    }

    private Vec3 interpolatePlayerHeadPos(Player player, float partialTick, double headOffset) {
        return player.getEyePosition(partialTick).add(0.0D, headOffset, 0.0D);
    }

    private Vec3 toVec3(Vector3f vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
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
            case "PLAYING" -> formatTime(ClientTdmState.remainingTimeTicks());
            default -> "";
        };
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
            default -> 0xFFBDBDBD;
        };
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

}
