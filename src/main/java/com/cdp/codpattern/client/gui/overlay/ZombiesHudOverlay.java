package com.cdp.codpattern.client.gui.overlay;

import com.cdp.codpattern.app.match.model.ModeObjectState;
import com.cdp.codpattern.app.zombies.service.ZombiesWeaponItemStackService;
import com.cdp.codpattern.app.zombies.sync.ZombiesObjectStateKeys;
import com.cdp.codpattern.client.ClientMatchState;
import com.cdp.codpattern.client.ClientModeObjectState;
import com.cdp.codpattern.client.zombies.ClientZombiesState;
import com.cdp.codpattern.client.zombies.ZombiesRarityDisplay;
import com.cdp.codpattern.common.block.CodPatternBlockRegister;
import com.cdp.codpattern.compat.tacz.client.TaczClientApi;
import com.tacz.guns.client.input.InteractKey;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ZombiesHudOverlay implements IGuiOverlay {
    public static final ZombiesHudOverlay INSTANCE = new ZombiesHudOverlay();

    private static final int TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int TEXT_SECONDARY = 0xFFC9D1D9;
    private static final int TEXT_ACCENT = 0xFFFFD166;
    private static final int TEXT_WAVE_DARK_RED = 0xFF7D1414;
    private static final int TEXT_ZOMBIES_DARK_YELLOW = 0xFFC28B18;
    private static final int TEXT_DANGER = 0xFFFF6B6B;
    private static final int TEXT_OK = 0xFF86EFAC;
    private static final int PROMPT_BG = 0x99000000;
    private static final int PROMPT_DISABLED = 0xFF9CA3AF;
    private static final String PAYLOAD_AREA_FROM_X = "areaFromX";
    private static final String PAYLOAD_AREA_FROM_Y = "areaFromY";
    private static final String PAYLOAD_AREA_FROM_Z = "areaFromZ";
    private static final String PAYLOAD_AREA_TO_X = "areaToX";
    private static final String PAYLOAD_AREA_TO_Y = "areaToY";
    private static final String PAYLOAD_AREA_TO_Z = "areaToZ";
    private static final int PLAYER_STATUS_HEALTH_COLOR = 0xFFE53935;
    private static final int PLAYER_STATUS_ARMOR_COLOR = 0xFF4DA3FF;
    private static final float WAVE_NUMBER_SCALE = 7.5F;
    private static final float INTERMISSION_WAVE_NUMBER_SCALE = 8.0F;
    private static final long INTERMISSION_WAVE_FADE_IN_MS = 1000L;
    private static final long INTERMISSION_WAVE_HOLD_MS = 3500L;
    private static final long INTERMISSION_WAVE_FADE_OUT_MS = 500L;
    private static final long INTERMISSION_WAVE_TOTAL_MS =
            INTERMISSION_WAVE_FADE_IN_MS + INTERMISSION_WAVE_HOLD_MS + INTERMISSION_WAVE_FADE_OUT_MS;
    private static final int PLAYER_STATUS_LEFT = 14;
    private static final int PLAYER_STATUS_RIGHT_MARGIN = 8;
    private static final int PLAYER_STATUS_BOTTOM_MARGIN = 28;
    private static final int PLAYER_STATUS_AVATAR_SIZE = 28;
    private static final int PLAYER_STATUS_AVATAR_GAP = 8;
    private static final int PLAYER_STATUS_BAR_WIDTH = 210;
    private static final int PLAYER_STATUS_BAR_MIN_WIDTH = 48;
    private static final int PLAYER_STATUS_BAR_HEIGHT = 4;
    private static final int PLAYER_STATUS_LINE_GAP = 1;
    private static final int TEAMMATE_AVATAR_SIZE = 20;
    private static final int TEAMMATE_AVATAR_GAP = 6;
    private static final int TEAMMATE_BAR_WIDTH = 150;
    private static final int TEAMMATE_BAR_HEIGHT = 2;
    private static final int TEAMMATE_POINTS_GAP = 8;
    private static final int TEAMMATE_ROW_GAP = 5;
    private static final int TEAMMATE_STATUS_BOTTOM_GAP = 8;
    private static final int TEAMMATE_MIN_RIGHT_MARGIN = 8;
    private static final int LEADERBOARD_LEFT = 8;
    private static final int LEADERBOARD_ROW_WIDTH = 156;
    private static final int LEADERBOARD_ROW_HEIGHT = 18;
    private static final int LEADERBOARD_ROW_GAP = 3;
    private static final int RESULT_PAGE_COUNT = 2;
    private static final long RESULT_PAGE_DURATION_MS = 5000L;
    private static final long RESULT_PAGE_FADE_MS = 550L;
    private static final int HELD_RARITY_RIGHT_MARGIN = 16;
    private static final int HELD_RARITY_BOTTOM_MARGIN = 42;
    private static final int HELD_UPGRADE_LEVEL_BOTTOM_MARGIN = 28;
    private static int intermissionWaveNumber = Integer.MIN_VALUE;
    private static long intermissionWaveStartedAtMs;
    private static String resultPhaseKey = "";
    private static long resultPhaseStartedAtMs;

    private ZombiesHudOverlay() {
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        if (!ClientZombiesState.shouldRenderHud()) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        TdmHudOverlay.INSTANCE.renderSharedPlayerScreenEffects(graphics, partialTick, screenWidth, screenHeight);
        String phase = ClientZombiesState.phaseKey();
        if (isResultPhase(phase)) {
            clearIntermissionWaveAnnouncement();
            renderZombiesResultOverlay(graphics, font, screenWidth / 2, screenWidth, screenHeight, phase);
            return;
        }
        clearResultPageTracking();
        if ("INTERMISSION".equals(phase)) {
            renderIntermissionWaveAnnouncement(graphics, font, screenWidth, screenHeight);
        } else {
            clearIntermissionWaveAnnouncement();
            renderTopStats(graphics, font, screenWidth);
        }
        renderScoreLeaderboard(graphics, font, screenWidth, screenHeight);
        renderRoomTeammateStatus(graphics, font, screenWidth, screenHeight);
        renderPhaseNotice(graphics, font, screenWidth, screenHeight);
        renderInteractionPrompt(graphics, font, screenWidth, screenHeight);
        renderPlayerStatus(graphics, font, screenWidth, screenHeight);
        renderHeldWeaponRarity(graphics, font, screenWidth, screenHeight);
        renderHeldWeaponUpgradeLevel(graphics, font, screenWidth, screenHeight);
    }

    private static void renderTopStats(GuiGraphics graphics, Font font, int screenWidth) {
        int right = screenWidth - 16;
        int top = 12;

        String wave = Integer.toString(Math.max(0, ClientZombiesState.wave()));
        graphics.pose().pushPose();
        graphics.pose().translate(right, top, 0);
        graphics.pose().scale(WAVE_NUMBER_SCALE, WAVE_NUMBER_SCALE, 1.0F);
        graphics.drawString(font, wave, -font.width(wave), 0, TEXT_WAVE_DARK_RED, true);
        graphics.pose().popPose();

        String label = "剩余";
        String value = "：" + Math.max(0, ClientZombiesState.zombiesLeft());
        int y = top + Math.round(font.lineHeight * WAVE_NUMBER_SCALE) + 4;
        int x = right - font.width(label) - font.width(value);
        graphics.drawString(font, label, x, y, TEXT_PRIMARY, true);
        graphics.drawString(font, value, x + font.width(label), y, TEXT_ZOMBIES_DARK_YELLOW, true);
    }

    private static void renderScoreLeaderboard(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        List<ClientZombiesState.ResultRow> rows = ClientZombiesState.leaderboardRows();
        if (rows.isEmpty()) {
            return;
        }
        int x = LEADERBOARD_LEFT;
        int y = screenHeight >= 500 ? 92 : 70;
        int width = LEADERBOARD_ROW_WIDTH;
        if (x + width >= screenWidth || y >= screenHeight - 40) {
            return;
        }

        String title = Component.translatable("hud.codpattern.zombies.leaderboard.title").getString();
        graphics.drawString(font, fit(font, title, width), x, y, TEXT_ACCENT, true);
        int rowY = y + font.lineHeight + 4;
        int maxRows = Math.min(rows.size(), Math.max(0, (screenHeight - rowY - 8) / (LEADERBOARD_ROW_HEIGHT + LEADERBOARD_ROW_GAP)));
        for (int i = 0; i < maxRows; i++) {
            renderScoreLeaderboardRow(graphics, font, x, rowY + i * (LEADERBOARD_ROW_HEIGHT + LEADERBOARD_ROW_GAP),
                    width, LEADERBOARD_ROW_HEIGHT, i, rows.get(i));
        }
    }

    private static void renderScoreLeaderboardRow(
            GuiGraphics graphics,
            Font font,
            int x,
            int y,
            int width,
            int height,
            int index,
            ClientZombiesState.ResultRow row
    ) {
        int accent = row.self() ? TEXT_ACCENT : (row.alive() ? TEXT_OK : TEXT_SECONDARY);
        graphics.fillGradient(x, y, x + width, y + height, withAlpha(0xFF111820, 160), withAlpha(0xFF080D12, 112));
        graphics.fill(x, y, x + 2, y + height, withAlpha(accent, 210));

        String rank = Integer.toString(index + 1);
        graphics.drawString(font, rank, x + 5, y + 5, withAlpha(TEXT_SECONDARY, 230), false);

        String score = Integer.toString(Math.max(0, row.totalEarnedPoints()));
        int scoreWidth = font.width(score);
        graphics.drawString(font, score, x + width - scoreWidth - 6, y + 5, TEXT_ACCENT, false);

        int nameX = x + 19;
        int nameWidth = Math.max(0, width - 28 - scoreWidth);
        graphics.drawString(font, fit(font, safeName(row.name()), nameWidth), nameX, y + 5, accent, false);
    }

    private static void renderIntermissionWaveAnnouncement(
            GuiGraphics graphics,
            Font font,
            int screenWidth,
            int screenHeight
    ) {
        int waveNumber = Math.max(0, ClientZombiesState.wave());
        long now = System.currentTimeMillis();
        if (intermissionWaveNumber != waveNumber || intermissionWaveStartedAtMs <= 0L) {
            intermissionWaveNumber = waveNumber;
            intermissionWaveStartedAtMs = now - estimatedIntermissionElapsedMs();
        }

        long elapsed = Math.max(0L, now - intermissionWaveStartedAtMs);
        int alpha = intermissionWaveAlpha(elapsed);
        if (alpha <= 0) {
            return;
        }

        String text = Integer.toString(waveNumber);
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 4;
        float scale = INTERMISSION_WAVE_NUMBER_SCALE;
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, -font.width(text) / 2, -font.lineHeight / 2,
                withAlpha(TEXT_WAVE_DARK_RED, alpha), true);
        graphics.pose().popPose();
    }

    private static void clearIntermissionWaveAnnouncement() {
        intermissionWaveNumber = Integer.MIN_VALUE;
        intermissionWaveStartedAtMs = 0L;
    }

    private static int intermissionWaveAlpha(long elapsedMs) {
        if (elapsedMs < INTERMISSION_WAVE_FADE_IN_MS) {
            return Math.round(255.0F * (elapsedMs / (float) INTERMISSION_WAVE_FADE_IN_MS));
        }
        long holdEnd = INTERMISSION_WAVE_FADE_IN_MS + INTERMISSION_WAVE_HOLD_MS;
        if (elapsedMs < holdEnd) {
            return 255;
        }
        if (elapsedMs < INTERMISSION_WAVE_TOTAL_MS) {
            long fadeElapsed = elapsedMs - holdEnd;
            return Math.round(255.0F * (1.0F - fadeElapsed / (float) INTERMISSION_WAVE_FADE_OUT_MS));
        }
        return 0;
    }

    private static long estimatedIntermissionElapsedMs() {
        long remainingMs = Math.max(0, ClientZombiesState.remainingTimeTicks()) * 50L;
        return Math.max(0L, INTERMISSION_WAVE_TOTAL_MS - Math.min(INTERMISSION_WAVE_TOTAL_MS, remainingMs));
    }

    private static void renderRoomTeammateStatus(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        List<ClientZombiesState.SurvivorStatus> teammates = ClientZombiesState.roomTeammates();
        if (teammates.isEmpty()) {
            return;
        }

        int avatarX = PLAYER_STATUS_LEFT;
        int barX = avatarX + TEAMMATE_AVATAR_SIZE + TEAMMATE_AVATAR_GAP;
        int requiredWidth = barX + TEAMMATE_BAR_WIDTH + TEAMMATE_POINTS_GAP + 28 + TEAMMATE_MIN_RIGHT_MARGIN;
        if (screenWidth < requiredWidth) {
            return;
        }

        int localAvatarY = screenHeight - PLAYER_STATUS_BOTTOM_MARGIN - PLAYER_STATUS_AVATAR_SIZE - 6;
        int rowHeight = Math.max(TEAMMATE_AVATAR_SIZE, TEAMMATE_BAR_HEIGHT + 3 + font.lineHeight) + TEAMMATE_ROW_GAP;
        int listBottomY = localAvatarY - TEAMMATE_STATUS_BOTTOM_GAP;
        int rowY = listBottomY - teammates.size() * rowHeight;
        if (rowY < 2) {
            return;
        }

        for (ClientZombiesState.SurvivorStatus teammate : teammates) {
            renderRoomTeammateRow(graphics, font, teammate, avatarX, barX, rowY);
            rowY += rowHeight;
        }
    }

    private static void renderRoomTeammateRow(
            GuiGraphics graphics,
            Font font,
            ClientZombiesState.SurvivorStatus teammate,
            int avatarX,
            int barX,
            int rowTop
    ) {
        int barY = rowTop;
        int idY = barY + TEAMMATE_BAR_HEIGHT + 3;
        int pointsX = barX + TEAMMATE_BAR_WIDTH + TEAMMATE_POINTS_GAP;
        int pointsY = barY;

        renderSurvivorAvatar(graphics, teammate.playerId(), teammate.name(), avatarX, rowTop, TEAMMATE_AVATAR_SIZE);

        double maxHealth = Math.max(1.0D, teammate.maxHealth());
        double health = Math.max(0.0D, teammate.health());
        float ratio = Mth.clamp((float) (health / maxHealth), 0.0F, 1.0F);
        int filledWidth = Math.round(TEAMMATE_BAR_WIDTH * ratio);
        if (health > 0.0D && filledWidth <= 0) {
            filledWidth = 1;
        }

        graphics.fill(barX - 1, barY - 1, barX + TEAMMATE_BAR_WIDTH + 1, barY + TEAMMATE_BAR_HEIGHT + 1, 0xDDFFFFFF);
        graphics.fill(barX, barY, barX + TEAMMATE_BAR_WIDTH, barY + TEAMMATE_BAR_HEIGHT, 0xFF14171A);
        if (filledWidth > 0) {
            graphics.fill(barX, barY, barX + filledWidth, barY + TEAMMATE_BAR_HEIGHT, PLAYER_STATUS_HEALTH_COLOR);
        }

        String armor = Integer.toString(Math.max(0, teammate.armorLevel()));
        int armorWidth = font.width(armor);
        int idWidth = Math.max(0, TEAMMATE_BAR_WIDTH - armorWidth - 4);
        String name = fit(font, safeName(teammate.name()), idWidth);
        graphics.drawString(font, name, barX, idY, survivorColor(teammate), true);
        graphics.drawString(font, armor, barX + TEAMMATE_BAR_WIDTH - armorWidth, idY, PLAYER_STATUS_ARMOR_COLOR, true);

        String points = Integer.toString(Math.max(0, teammate.points()));
        graphics.drawString(font, points, pointsX, pointsY, TEXT_SECONDARY, true);
    }

    private static void renderSurvivorAvatar(
            GuiGraphics graphics,
            java.util.UUID playerId,
            String name,
            int x,
            int y,
            int size
    ) {
        graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xCC000000);
        if (playerId == null || name == null || name.isBlank()) {
            renderAvatarFallback(graphics, name, x, y, size);
            return;
        }
        ResourceLocation skin = Minecraft.getInstance().getSkinManager()
                .getInsecureSkinLocation(new GameProfile(playerId, name));
        PlayerFaceRenderer.draw(graphics, skin, x, y, size);
    }

    private static void renderAvatarFallback(GuiGraphics graphics, String name, int x, int y, int size) {
        graphics.fill(x, y, x + size, y + size, 0xFF33465A);
        String text = (name == null || name.isBlank())
                ? "?"
                : name.substring(0, 1).toUpperCase(Locale.ROOT);
        graphics.drawString(
                Minecraft.getInstance().font,
                text,
                x + size / 2 - Minecraft.getInstance().font.width(text) / 2,
                y + size / 2 - Minecraft.getInstance().font.lineHeight / 2,
                TEXT_PRIMARY,
                false);
    }

    private static void renderPhaseNotice(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        String phase = ClientZombiesState.phaseKey();
        if (phase == null || phase.isBlank() || "WAVE_ACTIVE".equals(phase) || "INTERMISSION".equals(phase)) {
            return;
        }

        String key = "hud.codpattern.zombies.phase." + phase.toLowerCase(Locale.ROOT);
        String text = ClientZombiesState.remainingTimeTicks() > 0
                ? Component.translatable(key, secondsLeft()).getString()
                : Component.translatable(key).getString();
        if (text.isBlank() || text.equals(key)) {
            return;
        }

        int x = screenWidth / 2 - font.width(text) / 2;
        int y = Math.max(58, screenHeight / 3);
        graphics.fill(x - 8, y - 6, x + font.width(text) + 8, y + font.lineHeight + 6, 0x77000000);
        graphics.drawString(font, text, x, y, phaseColor(phase), true);
    }

    private static void renderZombiesResultOverlay(
            GuiGraphics graphics,
            Font font,
            int centerX,
            int screenWidth,
            int screenHeight,
            String phase
    ) {
        long pageTick = resultPageTickMs(phase);
        int pageIndex = resultPageIndex(pageTick);
        int alpha = Mth.clamp(Math.round(computeResultPageAlpha(pageTick) * 255.0F), 0, 255);
        if (alpha <= 0) {
            return;
        }

        int accent = resultAccentColor(phase);
        renderResultBackdrop(graphics, screenWidth, screenHeight, alpha, accent);
        if (pageIndex == 0) {
            renderResultSurvivalPage(graphics, font, centerX, screenWidth, screenHeight, alpha, accent, phase);
        } else {
            renderResultSettlementPage(graphics, font, centerX, screenWidth, screenHeight, alpha, accent, pageTick);
        }

        String pageText = Component.translatable(
                "hud.codpattern.zombies.result.page_index",
                pageIndex + 1,
                RESULT_PAGE_COUNT).getString();
        drawCenteredString(graphics, font, pageText, centerX, screenHeight - 20, withAlpha(0xFFE4E4E4, alpha));
    }

    private static void renderResultBackdrop(GuiGraphics graphics, int screenWidth, int screenHeight, int alpha, int accent) {
        graphics.fill(0, 0, screenWidth, screenHeight, (alpha * 3 / 7) << 24);
        graphics.fill(0, 0, screenWidth, 36, withAlpha(accent, alpha));
        graphics.fill(0, screenHeight - 36, screenWidth, screenHeight, withAlpha(accent, alpha));
    }

    private static void renderResultSurvivalPage(
            GuiGraphics graphics,
            Font font,
            int centerX,
            int screenWidth,
            int screenHeight,
            int alpha,
            int accent,
            String phase
    ) {
        List<ClientZombiesState.ResultRow> rows = ClientZombiesState.leaderboardRows();
        int panelWidth = Math.min(700, screenWidth - 36);
        int panelHeight = Math.min(254, screenHeight - 84);
        int x = centerX - panelWidth / 2;
        int y = screenHeight / 2 - panelHeight / 2;

        graphics.fillGradient(x, y, x + panelWidth, y + panelHeight, withAlpha(0xFF0F1520, alpha), withAlpha(0xFF151D2A, alpha));
        graphics.fill(x, y + panelHeight - 3, x + panelWidth, y + panelHeight, withAlpha(accent, alpha));

        String title = Component.translatable("hud.codpattern.zombies.result.title." + phase.toLowerCase(Locale.ROOT)).getString();
        drawScaledCenteredString(graphics, font, title, centerX, y + 20, withAlpha(TEXT_PRIMARY, alpha), 1.8F);

        long alive = rows.stream().filter(ClientZombiesState.ResultRow::alive).count();
        String summary = Component.translatable(
                "hud.codpattern.zombies.result.survival.summary",
                Math.max(0, ClientZombiesState.wave()),
                alive,
                rows.size()).getString();
        drawCenteredString(graphics, font, summary, centerX, y + 56, withAlpha(TEXT_SECONDARY, alpha));
        drawCenteredString(graphics, font, Component.translatable("hud.codpattern.zombies.result.page.survival").getString(),
                centerX, y + 76, withAlpha(TEXT_ACCENT, alpha));

        int rowWidth = Math.min(312, panelWidth - 28);
        int columns = panelWidth >= 590 ? 2 : 1;
        int gap = 12;
        int columnWidth = columns == 2 ? (panelWidth - 28 - gap) / 2 : rowWidth;
        int startX = columns == 2 ? x + 14 : centerX - columnWidth / 2;
        int startY = y + 98;
        int rowHeight = 28;
        int rowGap = 5;
        int maxRowsPerColumn = Math.max(1, (panelHeight - 112) / (rowHeight + rowGap));
        int maxRows = Math.min(rows.size(), maxRowsPerColumn * columns);
        for (int i = 0; i < maxRows; i++) {
            int column = columns == 1 ? 0 : i / maxRowsPerColumn;
            int rowIndex = columns == 1 ? i : i % maxRowsPerColumn;
            int rowX = startX + column * (columnWidth + gap);
            int rowY = startY + rowIndex * (rowHeight + rowGap);
            renderResultSurvivorRow(graphics, font, rowX, rowY, columnWidth, rowHeight, rows.get(i), alpha);
        }
    }

    private static void renderResultSurvivorRow(
            GuiGraphics graphics,
            Font font,
            int x,
            int y,
            int width,
            int height,
            ClientZombiesState.ResultRow row,
            int alpha
    ) {
        int accent = row.alive() ? TEXT_OK : TEXT_DANGER;
        graphics.fillGradient(x, y, x + width, y + height, withAlpha(0xFF111820, alpha), withAlpha(0xFF0A0F15, alpha));
        graphics.fill(x, y, x + 2, y + height, withAlpha(accent, alpha));

        renderSurvivorAvatar(graphics, row.playerId(), row.name(), x + 5, y + 5, 18);
        int textX = x + 29;
        String status = resultStatusText(row);
        graphics.drawString(font, fit(font, safeName(row.name()), Math.max(0, width - 104)), textX, y + 4,
                withAlpha(TEXT_PRIMARY, alpha), false);
        graphics.drawString(font, fit(font, status, Math.max(0, width - 104)), textX, y + 15,
                withAlpha(accent, alpha), false);
        String score = Integer.toString(Math.max(0, row.totalEarnedPoints()));
        graphics.drawString(font, score, x + width - font.width(score) - 6, y + 10, withAlpha(TEXT_ACCENT, alpha), false);
    }

    private static void renderResultSettlementPage(
            GuiGraphics graphics,
            Font font,
            int centerX,
            int screenWidth,
            int screenHeight,
            int alpha,
            int accent,
            long pageTick
    ) {
        List<ClientZombiesState.ResultRow> rows = ClientZombiesState.leaderboardRows();
        int panelWidth = Math.min(820, screenWidth - 30);
        int panelHeight = Math.min(282, screenHeight - 76);
        int x = centerX - panelWidth / 2;
        int y = screenHeight / 2 - panelHeight / 2;

        graphics.fillGradient(x, y, x + panelWidth, y + panelHeight, withAlpha(0xFF0E141D, alpha), withAlpha(0xFF141E2B, alpha));
        graphics.fill(x, y + panelHeight - 3, x + panelWidth, y + panelHeight, withAlpha(accent, alpha));
        drawCenteredString(graphics, font, Component.translatable("hud.codpattern.zombies.result.page.settlement").getString(),
                centerX, y + 10, withAlpha(TEXT_PRIMARY, alpha));

        int contentX = x + 12;
        int contentY = y + 29;
        int contentWidth = panelWidth - 24;
        renderResultTableHeader(graphics, font, contentX, contentY, contentWidth, alpha);

        int rowY = contentY + 17;
        int rowHeight = 24;
        int rowGap = 3;
        int maxRows = Math.min(rows.size(), Math.max(0, (panelHeight - 50) / (rowHeight + rowGap)));
        for (int i = 0; i < maxRows; i++) {
            float progress = resultRowAppearProgress(pageTick, i);
            if (progress <= 0.0F) {
                continue;
            }
            int rowAlpha = Mth.clamp(Math.round(alpha * progress), 0, 255);
            int yOffset = Math.round((1.0F - progress) * 6.0F);
            renderResultSettlementRow(graphics, font, contentX, rowY + i * (rowHeight + rowGap) + yOffset,
                    contentWidth, rowHeight, i, rows.get(i), rowAlpha);
        }
    }

    private static void renderResultTableHeader(GuiGraphics graphics, Font font, int x, int y, int width, int alpha) {
        graphics.drawString(font, Component.translatable("hud.codpattern.zombies.result.column.player").getString(),
                x + 28, y, withAlpha(TEXT_SECONDARY, alpha), false);
        drawRightAligned(graphics, font, Component.translatable("hud.codpattern.zombies.result.column.score").getString(),
                x + width - 162, y, withAlpha(TEXT_SECONDARY, alpha));
        drawRightAligned(graphics, font, Component.translatable("hud.codpattern.zombies.result.column.kills").getString(),
                x + width - 104, y, withAlpha(TEXT_SECONDARY, alpha));
        drawRightAligned(graphics, font, Component.translatable("hud.codpattern.zombies.result.column.barriers").getString(),
                x + width - 55, y, withAlpha(TEXT_SECONDARY, alpha));
        drawRightAligned(graphics, font, Component.translatable("hud.codpattern.zombies.result.column.deaths").getString(),
                x + width - 8, y, withAlpha(TEXT_SECONDARY, alpha));
    }

    private static void renderResultSettlementRow(
            GuiGraphics graphics,
            Font font,
            int x,
            int y,
            int width,
            int height,
            int index,
            ClientZombiesState.ResultRow row,
            int alpha
    ) {
        int accent = row.self() ? TEXT_ACCENT : (row.alive() ? TEXT_OK : TEXT_SECONDARY);
        graphics.fillGradient(x, y, x + width, y + height, withAlpha(0xFF141A24, alpha), withAlpha(0xFF10151E, alpha));
        graphics.fill(x, y, x + 2, y + height, withAlpha(accent, alpha));

        String rank = Integer.toString(index + 1);
        graphics.drawString(font, rank, x + 6, y + 8, withAlpha(TEXT_SECONDARY, alpha), false);
        renderSurvivorAvatar(graphics, row.playerId(), row.name(), x + 24, y + 4, 16);
        graphics.drawString(font, fit(font, safeName(row.name()), Math.max(0, width - 230)), x + 46, y + 4,
                withAlpha(TEXT_PRIMARY, alpha), false);
        graphics.drawString(font, fit(font, resultStatusText(row), Math.max(0, width - 230)), x + 46, y + 14,
                withAlpha(accent, alpha), false);

        drawRightAligned(graphics, font, Integer.toString(Math.max(0, row.totalEarnedPoints())),
                x + width - 162, y + 8, withAlpha(TEXT_ACCENT, alpha));
        drawRightAligned(graphics, font, Integer.toString(Math.max(0, row.kills())),
                x + width - 104, y + 8, withAlpha(TEXT_PRIMARY, alpha));
        drawRightAligned(graphics, font, Integer.toString(Math.max(0, row.barriersOpened())),
                x + width - 55, y + 8, withAlpha(TEXT_PRIMARY, alpha));
        drawRightAligned(graphics, font, Integer.toString(Math.max(0, row.deaths())),
                x + width - 8, y + 8, withAlpha(TEXT_PRIMARY, alpha));
    }

    private static void renderInteractionPrompt(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        Optional<InteractionPromptLine> prompt = currentInteractionPrompt();
        if (prompt.isEmpty()) {
            return;
        }

        InteractionPromptLine line = prompt.get();
        Minecraft minecraft = Minecraft.getInstance();
        String keyLabel = line.taczInteractKey() ? taczInteractKeyLabel(minecraft) : useKeyLabel(minecraft);
        int maxPromptWidth = Math.max(40, screenWidth - 40);
        String actionText = fit(font, line.text(), maxPromptWidth);
        String keyText = fit(font, line.interactable() ? "[" + keyLabel + "] 交互" : "不可交互", maxPromptWidth);
        int width = Math.max(font.width(actionText), font.width(keyText));
        int x = screenWidth / 2 - width / 2;
        int targetCenterY = (screenHeight / 2 + screenHeight) / 2;
        int totalHeight = font.lineHeight * 2 + 3;
        int y = targetCenterY - totalHeight / 2;
        graphics.fill(x - 7, y - 4, x + width + 7, y + totalHeight + 4, PROMPT_BG);
        graphics.drawString(font, actionText, x, y, line.color(), true);
        graphics.drawString(font, keyText, x, y + font.lineHeight + 3,
                line.interactable() ? TEXT_ACCENT : PROMPT_DISABLED, true);
    }

    private static Optional<InteractionPromptLine> currentInteractionPrompt() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null
                || !(minecraft.hitResult instanceof BlockHitResult blockHit)
                || blockHit.getType() != HitResult.Type.BLOCK) {
            return Optional.empty();
        }

        String roomKey = ClientMatchState.roomContextName();
        if (roomKey == null || roomKey.isBlank()) {
            return Optional.empty();
        }
        BlockPos pos = blockHit.getBlockPos();
        for (ModeObjectState state : ClientModeObjectState.roomStates(roomKey).values()) {
            if (state == null) {
                continue;
            }
            CompoundTag payload = state.payload();
            String type = payload.getString(ZombiesObjectStateKeys.PAYLOAD_TYPE);
            if (!isPromptObjectType(type) || !matchesPromptTarget(minecraft, pos, state, payload, type)) {
                continue;
            }
            return promptForState(type, payload, player.getMainHandItem(), roomKey);
        }
        return Optional.empty();
    }

    private static Optional<InteractionPromptLine> promptForState(
            String type,
            CompoundTag payload,
            ItemStack mainHand,
            String roomKey
    ) {
        boolean taczInteractKey = TaczClientApi.isGun(mainHand) && isTaczInteractKeyPromptObjectType(type);
        return switch (type) {
            case "barrier" -> barrierPrompt(payload, taczInteractKey);
            case "weapon_wall" -> weaponWallPrompt(payload, taczInteractKey);
            case "ammo_box" -> ammoBoxPrompt(payload, mainHand, roomKey, taczInteractKey);
            case "armor_station" -> armorStationPrompt(payload, taczInteractKey);
            case "soda_machine" -> sodaMachinePrompt(payload, taczInteractKey);
            case "ultimate_machine" -> ultimateMachinePrompt(payload, mainHand, roomKey, taczInteractKey);
            default -> Optional.empty();
        };
    }

    private static Optional<InteractionPromptLine> weaponWallPrompt(CompoundTag payload, boolean taczInteractKey) {
        String gunId = payload.getString("gunId");
        String rarityId = payload.getString("rarityId");
        if (gunId.isBlank()) {
            return Optional.of(disabledPrompt("墙枪暂无可购买武器"));
        }
        String label = "购买 " + displayRarityGun(rarityId, gunId)
                + " - " + Math.max(0, payload.getInt(ZombiesObjectStateKeys.PAYLOAD_COST)) + "点";
        return Optional.of(payload.getBoolean(ZombiesObjectStateKeys.PAYLOAD_ENABLED)
                ? activePrompt(label, taczInteractKey)
                : disabledPrompt(label));
    }

    private static Optional<InteractionPromptLine> ammoBoxPrompt(
            CompoundTag payload,
            ItemStack mainHand,
            String roomKey,
            boolean taczInteractKey
    ) {
        if (isEmpty(mainHand) || !TaczClientApi.isGun(mainHand)) {
            return Optional.of(disabledPrompt("主手持僵尸枪械补弹"));
        }
        Optional<HeldWeaponPromptData> weapon = heldWeaponPromptData(mainHand, roomKey);
        if (weapon.isEmpty()) {
            return Optional.of(disabledPrompt("当前武器无法补弹"));
        }
        HeldWeaponPromptData data = weapon.get();
        if (data.maxReserveAmmo() > 0 && data.reserveAmmo() >= data.maxReserveAmmo()) {
            return Optional.of(disabledPrompt(data.gunId() + " 等级 " + data.weaponLevel() + " 弹药已满"));
        }
        int cost = ammoCost(payload, data.weaponLevel());
        if (cost < 0) {
            return Optional.of(disabledPrompt(data.gunId() + " 等级 " + data.weaponLevel() + " 无补弹价格"));
        }
        return Optional.of(activePrompt("补满 " + data.gunId() + " 等级 " + data.weaponLevel() + " - " + cost + "点",
                taczInteractKey));
    }

    private static Optional<InteractionPromptLine> armorStationPrompt(CompoundTag payload, boolean taczInteractKey) {
        int armorLevel = Math.max(0, payload.getInt("armorLevel"));
        String label = "购买 " + armorLevel + "级护甲 - "
                + Math.max(0, payload.getInt(ZombiesObjectStateKeys.PAYLOAD_COST)) + "点";
        if (ClientZombiesState.armorLevel() >= armorLevel && armorLevel > 0) {
            return Optional.of(disabledPrompt("已拥有 " + armorLevel + "级或更高护甲"));
        }
        return Optional.of(payload.getBoolean(ZombiesObjectStateKeys.PAYLOAD_ENABLED)
                ? activePrompt(label, taczInteractKey)
                : disabledPrompt(label));
    }

    private static Optional<InteractionPromptLine> sodaMachinePrompt(CompoundTag payload, boolean taczInteractKey) {
        String buffId = payload.getString("buffId").trim();
        String displayBuff = buffId.isBlank() ? "汽水效果" : buffId;
        if (!buffId.isBlank() && ClientZombiesState.buffEnabled(buffId)) {
            return Optional.of(disabledPrompt("已拥有 " + displayBuff));
        }
        String label = "购买 " + displayBuff + " - "
                + Math.max(0, payload.getInt(ZombiesObjectStateKeys.PAYLOAD_COST)) + "点";
        if (payload.getBoolean("requiresPower") && !payload.getBoolean("powerOn")) {
            return Optional.of(powerRequiredPrompt(label));
        }
        return Optional.of(payload.getBoolean(ZombiesObjectStateKeys.PAYLOAD_ENABLED)
                ? activePrompt(label, taczInteractKey)
                : disabledPrompt(label));
    }

    private static Optional<InteractionPromptLine> ultimateMachinePrompt(
            CompoundTag payload,
            ItemStack mainHand,
            String roomKey,
            boolean taczInteractKey
    ) {
        if (isEmpty(mainHand) || !TaczClientApi.isGun(mainHand)) {
            return Optional.of(disabledPrompt("主手持僵尸枪械强化"));
        }
        Optional<HeldWeaponPromptData> weapon = heldWeaponPromptData(mainHand, roomKey);
        if (weapon.isEmpty()) {
            return Optional.of(disabledPrompt("当前武器无法强化"));
        }
        HeldWeaponPromptData data = weapon.get();
        int maxUpgradeLevel = Math.max(0, payload.getInt("maxUpgradeLevel"));
        if (maxUpgradeLevel > 0 && data.upgradeLevel() >= maxUpgradeLevel) {
            return Optional.of(disabledPrompt(data.gunId() + " 已达最高强化"));
        }
        String label = "强化 " + data.gunId() + " +" + (data.upgradeLevel() + 1)
                + " - " + Math.max(0, payload.getInt(ZombiesObjectStateKeys.PAYLOAD_COST)) + "点";
        if (payload.getBoolean("requiresPower") && !payload.getBoolean("powerOn")) {
            return Optional.of(powerRequiredPrompt(label));
        }
        return Optional.of(payload.getBoolean(ZombiesObjectStateKeys.PAYLOAD_ENABLED)
                ? activePrompt(label, taczInteractKey)
                : disabledPrompt(label));
    }

    private static Optional<InteractionPromptLine> barrierPrompt(CompoundTag payload, boolean taczInteractKey) {
        int group = Math.max(0, payload.getInt("group"));
        String target = group > 0 ? "屏障组 " + group : "屏障";
        if (payload.getBoolean("cleared") || !payload.getBoolean(ZombiesObjectStateKeys.PAYLOAD_ENABLED)) {
            return Optional.of(disabledPrompt(target + " 已开启"));
        }
        return Optional.of(activePrompt("开启 " + target + " - "
                + Math.max(0, payload.getInt(ZombiesObjectStateKeys.PAYLOAD_COST)) + "点", taczInteractKey));
    }

    private static boolean isPromptObjectType(String type) {
        return "barrier".equals(type)
                || "weapon_wall".equals(type)
                || "ammo_box".equals(type)
                || "armor_station".equals(type)
                || "soda_machine".equals(type)
                || "ultimate_machine".equals(type);
    }

    private static boolean isTaczInteractKeyPromptObjectType(String type) {
        return "barrier".equals(type)
                || "weapon_wall".equals(type)
                || "ammo_box".equals(type)
                || "armor_station".equals(type)
                || "soda_machine".equals(type)
                || "ultimate_machine".equals(type);
    }

    private static boolean matchesPromptTarget(
            Minecraft minecraft,
            BlockPos pos,
            ModeObjectState state,
            CompoundTag payload,
            String type
    ) {
        if ("barrier".equals(type)) {
            return matchesBarrierPromptTarget(minecraft, pos, state, payload);
        }
        return pos.equals(state.position()) && matchesExpectedPromptBlock(minecraft, pos, type);
    }

    private static boolean matchesBarrierPromptTarget(
            Minecraft minecraft,
            BlockPos pos,
            ModeObjectState state,
            CompoundTag payload
    ) {
        if (pos.equals(state.position())) {
            return true;
        }
        return minecraft.level != null
                && minecraft.level.getBlockState(pos).is(CodPatternBlockRegister.ZOMBIES_PLAYER_BARRIER.get())
                && barrierContains(payload, pos);
    }

    private static boolean barrierContains(CompoundTag payload, BlockPos pos) {
        if (payload == null || pos == null
                || !payload.contains(PAYLOAD_AREA_FROM_X, Tag.TAG_INT)
                || !payload.contains(PAYLOAD_AREA_FROM_Y, Tag.TAG_INT)
                || !payload.contains(PAYLOAD_AREA_FROM_Z, Tag.TAG_INT)
                || !payload.contains(PAYLOAD_AREA_TO_X, Tag.TAG_INT)
                || !payload.contains(PAYLOAD_AREA_TO_Y, Tag.TAG_INT)
                || !payload.contains(PAYLOAD_AREA_TO_Z, Tag.TAG_INT)) {
            return false;
        }
        int minX = Math.min(payload.getInt(PAYLOAD_AREA_FROM_X), payload.getInt(PAYLOAD_AREA_TO_X));
        int maxX = Math.max(payload.getInt(PAYLOAD_AREA_FROM_X), payload.getInt(PAYLOAD_AREA_TO_X));
        int minY = Math.min(payload.getInt(PAYLOAD_AREA_FROM_Y), payload.getInt(PAYLOAD_AREA_TO_Y));
        int maxY = Math.max(payload.getInt(PAYLOAD_AREA_FROM_Y), payload.getInt(PAYLOAD_AREA_TO_Y));
        int minZ = Math.min(payload.getInt(PAYLOAD_AREA_FROM_Z), payload.getInt(PAYLOAD_AREA_TO_Z));
        int maxZ = Math.max(payload.getInt(PAYLOAD_AREA_FROM_Z), payload.getInt(PAYLOAD_AREA_TO_Z));
        return pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    private static boolean matchesExpectedPromptBlock(Minecraft minecraft, BlockPos pos, String type) {
        Block expected = expectedPromptBlock(type);
        return expected != null && minecraft.level != null && minecraft.level.getBlockState(pos).is(expected);
    }

    private static Block expectedPromptBlock(String type) {
        return switch (type) {
            case "weapon_wall" -> CodPatternBlockRegister.ZOMBIES_WEAPON_WALL_BOX.get();
            case "ammo_box" -> CodPatternBlockRegister.ZOMBIES_AMMO_BOX.get();
            case "armor_station" -> CodPatternBlockRegister.ZOMBIES_ARMOR_STATION_BOX.get();
            case "soda_machine" -> CodPatternBlockRegister.ZOMBIES_SODA_MACHINE_BOX.get();
            case "ultimate_machine" -> CodPatternBlockRegister.ZOMBIES_ULTIMATE_MACHINE_BOX.get();
            default -> null;
        };
    }

    private static Optional<HeldWeaponPromptData> heldWeaponPromptData(ItemStack stack, String roomKey) {
        if (stack == null || stack.isEmpty() || stack.getTag() == null) {
            return Optional.empty();
        }
        CompoundTag tag = stack.getTag();
        String taggedRoom = tag.contains(ZombiesWeaponItemStackService.TAG_ROOM_ID, Tag.TAG_STRING)
                ? tag.getString(ZombiesWeaponItemStackService.TAG_ROOM_ID).trim()
                : "";
        if (taggedRoom.isBlank() || !taggedRoom.equals(roomKey)) {
            return Optional.empty();
        }
        String gunId = tag.contains(ZombiesWeaponItemStackService.TAG_GUN_ID, Tag.TAG_STRING)
                ? tag.getString(ZombiesWeaponItemStackService.TAG_GUN_ID).trim()
                : "";
        int weaponLevel = positiveIntTag(tag, ZombiesWeaponItemStackService.TAG_WEAPON_LEVEL);
        if (gunId.isBlank() || weaponLevel <= 0) {
            return Optional.empty();
        }
        int reserveAmmo = positiveIntTag(tag, ZombiesWeaponItemStackService.TAG_RESERVE_AMMO);
        int maxReserveAmmo = positiveIntTag(tag, ZombiesWeaponItemStackService.TAG_MAX_RESERVE_AMMO);
        if (TaczClientApi.isGun(stack)) {
            int liveMaxReserveAmmo = Math.max(0, TaczClientApi.resolveMaxReserveAmmo(stack));
            if (liveMaxReserveAmmo > 0) {
                reserveAmmo = Math.max(0, Math.min(TaczClientApi.resolveReserveAmmo(stack), liveMaxReserveAmmo));
                maxReserveAmmo = liveMaxReserveAmmo;
            }
        }
        return Optional.of(new HeldWeaponPromptData(
                gunId,
                weaponLevel,
                positiveIntTag(tag, ZombiesWeaponItemStackService.TAG_UPGRADE_LEVEL),
                reserveAmmo,
                maxReserveAmmo));
    }

    private static void renderHeldWeaponRarity(
            GuiGraphics graphics,
            Font font,
            int screenWidth,
            int screenHeight
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        String roomKey = ClientMatchState.roomContextName();
        if (player == null || roomKey == null || roomKey.isBlank()) {
            return;
        }

        Optional<ZombiesRarityDisplay.Entry> rarity = heldWeaponRarity(player.getMainHandItem(), roomKey);
        if (rarity.isEmpty()) {
            return;
        }

        ZombiesRarityDisplay.Entry display = rarity.get();
        String text = display.label();
        int textWidth = font.width(text);
        int x = screenWidth - HELD_RARITY_RIGHT_MARGIN - textWidth;
        int y = screenHeight - HELD_RARITY_BOTTOM_MARGIN - font.lineHeight;
        if (x < 2 || y < 2) {
            return;
        }

        graphics.fillGradient(
                x - 9,
                y - 6,
                x + textWidth + 9,
                y + font.lineHeight + 6,
                withAlpha(display.color(), 22),
                withAlpha(display.color(), 112));
        graphics.drawString(font, text, x, y, display.color(), true);
    }

    private static Optional<ZombiesRarityDisplay.Entry> heldWeaponRarity(ItemStack stack, String roomKey) {
        if (stack == null || stack.isEmpty() || stack.getTag() == null) {
            return Optional.empty();
        }
        CompoundTag tag = stack.getTag();
        String taggedRoom = tag.contains(ZombiesWeaponItemStackService.TAG_ROOM_ID, Tag.TAG_STRING)
                ? tag.getString(ZombiesWeaponItemStackService.TAG_ROOM_ID).trim()
                : "";
        if (taggedRoom.isBlank() || !taggedRoom.equals(roomKey)) {
            return Optional.empty();
        }
        String gunId = tag.contains(ZombiesWeaponItemStackService.TAG_GUN_ID, Tag.TAG_STRING)
                ? tag.getString(ZombiesWeaponItemStackService.TAG_GUN_ID).trim()
                : "";
        String rarityId = tag.contains(ZombiesWeaponItemStackService.TAG_RARITY_ID, Tag.TAG_STRING)
                ? tag.getString(ZombiesWeaponItemStackService.TAG_RARITY_ID).trim()
                : "";
        if (gunId.isBlank() || rarityId.isBlank()) {
            return Optional.empty();
        }
        return ZombiesRarityDisplay.fromRarityId(rarityId);
    }

    private static void renderHeldWeaponUpgradeLevel(
            GuiGraphics graphics,
            Font font,
            int screenWidth,
            int screenHeight
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        String roomKey = ClientMatchState.roomContextName();
        if (player == null || roomKey == null || roomKey.isBlank()) {
            return;
        }

        int upgradeLevel = heldWeaponUpgradeLevel(player.getMainHandItem(), roomKey);
        if (upgradeLevel <= 0) {
            return;
        }

        String text = Integer.toString(upgradeLevel);
        int textWidth = font.width(text);
        int x = screenWidth - HELD_RARITY_RIGHT_MARGIN - textWidth;
        int y = screenHeight - HELD_UPGRADE_LEVEL_BOTTOM_MARGIN - font.lineHeight;
        if (x < 2 || y < 2) {
            return;
        }
        graphics.drawString(font, text, x, y, TEXT_PRIMARY, true);
    }

    private static int heldWeaponUpgradeLevel(ItemStack stack, String roomKey) {
        if (stack == null || stack.isEmpty() || stack.getTag() == null) {
            return 0;
        }
        CompoundTag tag = stack.getTag();
        String taggedRoom = tag.contains(ZombiesWeaponItemStackService.TAG_ROOM_ID, Tag.TAG_STRING)
                ? tag.getString(ZombiesWeaponItemStackService.TAG_ROOM_ID).trim()
                : "";
        if (taggedRoom.isBlank() || !taggedRoom.equals(roomKey)) {
            return 0;
        }
        String gunId = tag.contains(ZombiesWeaponItemStackService.TAG_GUN_ID, Tag.TAG_STRING)
                ? tag.getString(ZombiesWeaponItemStackService.TAG_GUN_ID).trim()
                : "";
        if (gunId.isBlank()) {
            return 0;
        }
        return positiveIntTag(tag, ZombiesWeaponItemStackService.TAG_UPGRADE_LEVEL);
    }

    private static int ammoCost(CompoundTag payload, int weaponLevel) {
        if (payload == null || !payload.contains("pricesByWeaponLevel", Tag.TAG_COMPOUND)) {
            return -1;
        }
        CompoundTag prices = payload.getCompound("pricesByWeaponLevel");
        String key = Integer.toString(Math.max(0, weaponLevel));
        return prices.contains(key, Tag.TAG_INT) ? Math.max(0, prices.getInt(key)) : -1;
    }

    private static int positiveIntTag(CompoundTag tag, String key) {
        return tag != null && tag.contains(key, Tag.TAG_INT) ? Math.max(0, tag.getInt(key)) : 0;
    }

    private static String displayRarityGun(String rarityId, String gunId) {
        String rarity = rarityId == null || rarityId.isBlank() ? "" : rarityId.trim() + " ";
        return rarity + gunId;
    }

    private static String useKeyLabel(Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null) {
            return "使用";
        }
        KeyMapping key = minecraft.options.keyUse;
        return key == null ? "使用" : key.getTranslatedKeyMessage().getString();
    }

    private static String taczInteractKeyLabel(Minecraft minecraft) {
        KeyMapping key = InteractKey.INTERACT_KEY;
        return key == null ? useKeyLabel(minecraft) : key.getTranslatedKeyMessage().getString();
    }

    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.isEmpty();
    }

    private static InteractionPromptLine activePrompt(String text) {
        return activePrompt(text, false);
    }

    private static InteractionPromptLine activePrompt(String text, boolean taczInteractKey) {
        return new InteractionPromptLine(text, true, taczInteractKey, TEXT_PRIMARY);
    }

    private static InteractionPromptLine disabledPrompt(String text) {
        return new InteractionPromptLine(text, false, false, PROMPT_DISABLED);
    }

    private static InteractionPromptLine powerRequiredPrompt(String text) {
        return new InteractionPromptLine(text + "（需要电源）", false, false, TEXT_DANGER);
    }

    private static void renderPlayerStatus(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        int avatarX = PLAYER_STATUS_LEFT;
        int avatarY = screenHeight - PLAYER_STATUS_BOTTOM_MARGIN - PLAYER_STATUS_AVATAR_SIZE - 6;
        if (avatarY < 2) {
            return;
        }

        int barX = avatarX + PLAYER_STATUS_AVATAR_SIZE + PLAYER_STATUS_AVATAR_GAP;
        int maxBarWidth = screenWidth - barX - PLAYER_STATUS_RIGHT_MARGIN;
        if (maxBarWidth < PLAYER_STATUS_BAR_MIN_WIDTH) {
            return;
        }
        int barWidth = Math.min(PLAYER_STATUS_BAR_WIDTH, maxBarWidth);

        int idY = avatarY - 1;
        int pointsY = idY + font.lineHeight + PLAYER_STATUS_LINE_GAP;
        int barY = pointsY + font.lineHeight + 3;
        int armorY = barY + PLAYER_STATUS_BAR_HEIGHT + 3;

        renderLocalAvatar(graphics, player, avatarX, avatarY, PLAYER_STATUS_AVATAR_SIZE);

        String playerId = player.getGameProfile().getName();
        String fittedPlayerId = fit(font, playerId, barWidth);
        graphics.drawString(font, fittedPlayerId, barX, idY, TEXT_PRIMARY, true);

        String points = Component.translatable("hud.codpattern.zombies.points", ClientZombiesState.points()).getString();
        graphics.drawString(font, fit(font, points, barWidth), barX, pointsY, TEXT_ACCENT, true);

        float maxHealth = Math.max(1.0F, player.getMaxHealth());
        float healthRatio = Mth.clamp(player.getHealth() / maxHealth, 0.0F, 1.0F);
        int filledWidth = Math.round(barWidth * healthRatio);
        if (player.getHealth() > 0.0F && filledWidth <= 0) {
            filledWidth = 1;
        }
        graphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + PLAYER_STATUS_BAR_HEIGHT + 1, 0xDDFFFFFF);
        graphics.fill(barX, barY, barX + barWidth, barY + PLAYER_STATUS_BAR_HEIGHT, 0xFF14171A);
        if (filledWidth > 0) {
            graphics.fill(barX, barY, barX + filledWidth, barY + PLAYER_STATUS_BAR_HEIGHT, PLAYER_STATUS_HEALTH_COLOR);
        }

        String armorDots = armorDots(ClientZombiesState.armorLevel());
        if (!armorDots.isBlank()) {
            graphics.drawString(font, armorDots, barX + barWidth - font.width(armorDots), armorY, PLAYER_STATUS_ARMOR_COLOR, true);
        }
    }

    private static void renderLocalAvatar(GuiGraphics graphics, LocalPlayer player, int x, int y, int size) {
        graphics.fill(x - 2, y - 2, x + size + 2, y + size + 2, 0xFFFFFFFF);
        graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xCC000000);
        ResourceLocation skin = player.getSkinTextureLocation();
        PlayerFaceRenderer.draw(graphics, skin, x, y, size);
    }

    private static String armorDots(int armorLevel) {
        int count = Math.max(0, armorLevel) * 2;
        if (count <= 0) {
            return "";
        }
        return "●".repeat(count);
    }

    private static int secondsLeft() {
        return Math.max(1, (ClientZombiesState.remainingTimeTicks() + 19) / 20);
    }

    private static boolean isResultPhase(String phase) {
        return "VICTORY".equals(phase) || "FAILED".equals(phase);
    }

    private static void clearResultPageTracking() {
        resultPhaseKey = "";
        resultPhaseStartedAtMs = 0L;
    }

    private static long resultPageTickMs(String phase) {
        long now = System.currentTimeMillis();
        String safePhase = phase == null ? "" : phase;
        if (!safePhase.equals(resultPhaseKey) || resultPhaseStartedAtMs <= 0L) {
            resultPhaseKey = safePhase;
            resultPhaseStartedAtMs = now;
        }
        return Math.max(0L, now - resultPhaseStartedAtMs);
    }

    private static int resultPageIndex(long pageTickMs) {
        long page = Math.max(0L, pageTickMs) / Math.max(1L, RESULT_PAGE_DURATION_MS);
        return (int) (page % RESULT_PAGE_COUNT);
    }

    private static float computeResultPageAlpha(long pageTickMs) {
        long pagePosition = Math.floorMod(Math.max(0L, pageTickMs), Math.max(1L, RESULT_PAGE_DURATION_MS));
        float fadeIn = RESULT_PAGE_FADE_MS <= 0L ? 1.0F : pagePosition / (float) RESULT_PAGE_FADE_MS;
        float fadeOut = RESULT_PAGE_FADE_MS <= 0L
                ? 1.0F
                : (RESULT_PAGE_DURATION_MS - pagePosition) / (float) RESULT_PAGE_FADE_MS;
        return Mth.clamp(Math.min(1.0F, Math.min(fadeIn, fadeOut)), 0.0F, 1.0F);
    }

    private static float resultRowAppearProgress(long pageTickMs, int index) {
        long pagePosition = Math.floorMod(Math.max(0L, pageTickMs), Math.max(1L, RESULT_PAGE_DURATION_MS));
        float progress = (pagePosition - 250L - index * 60L) / 260.0F;
        return Mth.clamp(progress, 0.0F, 1.0F);
    }

    private static int resultAccentColor(String phase) {
        return "VICTORY".equals(phase) ? 0xFF235A34 : 0xFF5D1D22;
    }

    private static String resultStatusText(ClientZombiesState.ResultRow row) {
        if (row == null) {
            return Component.translatable("hud.codpattern.zombies.result.status.dead").getString();
        }
        if ("LEFT".equals(row.connectionState())) {
            return Component.translatable("hud.codpattern.zombies.result.status.left").getString();
        }
        if ("OFFLINE".equals(row.connectionState())) {
            return Component.translatable("hud.codpattern.zombies.result.status.offline").getString();
        }
        if (row.alive()) {
            return Component.translatable("hud.codpattern.zombies.result.status.survived").getString();
        }
        return Component.translatable("hud.codpattern.zombies.result.status.dead").getString();
    }

    private static int survivorColor(ClientZombiesState.SurvivorStatus survivor) {
        String lifeState = survivor.lifeState() == null ? "" : survivor.lifeState();
        String connectionState = survivor.connectionState() == null ? "" : survivor.connectionState();
        if ("LEFT".equals(connectionState) || "DEAD_SPECTATING".equals(lifeState)) {
            return TEXT_DANGER;
        }
        if ("OFFLINE".equals(connectionState)) {
            return 0xFFFFA94D;
        }
        return TEXT_OK;
    }

    private static int phaseColor(String phase) {
        return switch (phase) {
            case "FAILED" -> TEXT_DANGER;
            case "VICTORY" -> TEXT_OK;
            default -> TEXT_ACCENT;
        };
    }

    private static int withAlpha(int color, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    private static void drawCenteredString(GuiGraphics graphics, Font font, String text, int centerX, int y, int color) {
        String safe = text == null ? "" : text;
        graphics.drawString(font, safe, centerX - font.width(safe) / 2, y, color, false);
    }

    private static void drawScaledCenteredString(
            GuiGraphics graphics,
            Font font,
            String text,
            int centerX,
            int y,
            int color,
            float scale
    ) {
        String safe = text == null ? "" : text;
        float safeScale = Math.max(0.1F, scale);
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, y, 0);
        graphics.pose().scale(safeScale, safeScale, 1.0F);
        graphics.drawString(font, safe, -font.width(safe) / 2, 0, color, false);
        graphics.pose().popPose();
    }

    private static void drawRightAligned(GuiGraphics graphics, Font font, String text, int rightX, int y, int color) {
        String safe = text == null ? "" : text;
        graphics.drawString(font, safe, rightX - font.width(safe), y, color, false);
    }

    private static String safeName(String name) {
        return name == null || name.isBlank() ? "Player" : name;
    }

    private static String fit(Font font, String text, int width) {
        String safe = text == null ? "" : text;
        if (font.width(safe) <= width) {
            return safe;
        }
        String ellipsis = "...";
        int maxWidth = Math.max(0, width - font.width(ellipsis));
        while (!safe.isEmpty() && font.width(safe) > maxWidth) {
            safe = safe.substring(0, safe.length() - 1);
        }
        return safe + ellipsis;
    }

    private record InteractionPromptLine(String text, boolean interactable, boolean taczInteractKey, int color) {
        private InteractionPromptLine {
            text = text == null ? "" : text;
        }
    }

    private record HeldWeaponPromptData(
            String gunId,
            int weaponLevel,
            int upgradeLevel,
            int reserveAmmo,
            int maxReserveAmmo
    ) {
    }

}
