package com.cdp.codpattern.client.gui.overlay;

import com.cdp.codpattern.client.ClientTdmState;
import com.cdp.codpattern.client.gui.CodTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.Locale;

public class TdmHudOverlay implements IGuiOverlay {

    public static final TdmHudOverlay INSTANCE = new TdmHudOverlay();

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        if (!shouldRenderHud()) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        int centerX = screenWidth / 2;

        renderBlackout(graphics, font, screenWidth, screenHeight);
        renderLeftScorePanel(graphics, font, screenWidth, screenHeight);
        renderPhaseAnnouncement(graphics, font, centerX, screenHeight);
        renderCountdownFocus(graphics, font, centerX, screenHeight);
        renderEndgameSplash(graphics, font, centerX, screenWidth, screenHeight);
        renderDeathCamPanel(graphics, font, centerX, screenHeight);
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
        int y = screenHeight >= 500 ? 92 : 70; // 放在小地图下方
        int panelWidth = 128;
        int panelHeight = 52;
        if (x + panelWidth >= screenWidth) {
            return;
        }

        graphics.fillGradient(x, y, x + panelWidth, y + panelHeight, 0xD010131A, 0xDD07090E);
        graphics.fill(x, y, x + panelWidth, y + 1, CodTheme.BORDER_SUBTLE);
        graphics.fill(x, y + panelHeight - 1, x + panelWidth, y + panelHeight, CodTheme.BORDER_SUBTLE);
        graphics.fill(x, y, x + 1, y + panelHeight, CodTheme.BORDER_SUBTLE);
        graphics.fill(x + panelWidth - 1, y, x + panelWidth, y + panelHeight, CodTheme.BORDER_SUBTLE);

        int timerWidth = 38;
        int contentX = x + 4;
        int contentY = y + 4;
        int rowWidth = panelWidth - timerWidth - 10;
        int rowHeight = 18;
        int kortacScore = getKortacScore();
        int specgruScore = getSpecgruScore();
        int maxScore = Math.max(1, Math.max(kortacScore, specgruScore));
        float pulseStrength = ClientTdmState.getScorePulseStrength();

        renderScoreRow(graphics, font, contentX, contentY, rowWidth, rowHeight, teamShort("kortac"),
                kortacScore, (float) kortacScore / maxScore, 0xFFE35A5A, pulseStrength);
        renderScoreRow(graphics, font, contentX, contentY + rowHeight + 2, rowWidth, rowHeight, teamShort("specgru"),
                specgruScore, (float) specgruScore / maxScore, 0xFF66A6FF, pulseStrength);

        int timerX = x + panelWidth - timerWidth - 4;
        int timerY = y + 4;
        int timerHeight = panelHeight - 8;
        graphics.fillGradient(timerX, timerY, timerX + timerWidth, timerY + timerHeight, 0xCD111111, 0xCD202020);
        graphics.fill(timerX, timerY + timerHeight - 1, timerX + timerWidth, timerY + timerHeight, 0x60FFFFFF);

        drawCenteredString(graphics, font, buildTimerText(), timerX + timerWidth / 2, timerY + 8, 0xFFFFDE7A);
        drawCenteredString(graphics, font, phaseShortText(ClientTdmState.currentPhase()), timerX + timerWidth / 2,
                timerY + 22, 0xFFD6D6D6);

        int phaseGlow = (int) (ClientTdmState.getPhaseFlashStrength() * 95.0f);
        if (phaseGlow > 0) {
            graphics.fill(x, y, x + panelWidth, y + 2,
                    (clamp(phaseGlow, 0, 255) << 24) | (phaseAccentColor() & 0x00FFFFFF));
        }
    }

    private void renderScoreRow(GuiGraphics graphics, Font font, int x, int y, int width, int height, String label, int score,
            float ratio, int accent, float pulse) {
        graphics.fillGradient(x, y, x + width, y + height, withAlpha(accent, 20), 0xAA0E1118);
        graphics.fill(x, y, x + 2, y + height, accent);

        String scoreText = String.valueOf(score);
        graphics.drawString(font, label, x + 6, y + 2, 0xFFF2F2F2, false);
        graphics.drawString(font, scoreText, x + width - font.width(scoreText) - 5, y + 2, 0xFFFFFFFF, false);

        int barX = x + 24;
        int barY = y + height - 4;
        int barW = Math.max(8, width - 34);
        graphics.fill(barX, barY, barX + barW, barY + 2, 0x66262626);
        int fill = (int) (barW * Math.max(0.0f, Math.min(1.0f, ratio)));
        if (fill > 0) {
            graphics.fill(barX, barY, barX + fill, barY + 2, accent);
        }

        int pulseAlpha = (int) (pulse * 60.0f);
        if (pulseAlpha > 0) {
            graphics.fill(x, y, x + width, y + height, (clamp(pulseAlpha, 0, 255) << 24) | 0x00FFD463);
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

        float alphaF = ClientTdmState.announcementTicks() > 0
                ? Math.max(0.35f, ClientTdmState.getAnnouncementAlpha())
                : 0.35f;
        int alpha = clamp((int) (alphaF * 255.0f), 0, 255);
        int accent = endAccentColor();

        graphics.fill(0, 0, screenWidth, screenHeight, (alpha * 3 / 7) << 24);
        graphics.fill(0, 0, screenWidth, 36, (alpha << 24) | (accent & 0x00FFFFFF));
        graphics.fill(0, screenHeight - 36, screenWidth, screenHeight, (alpha << 24) | (accent & 0x00FFFFFF));

        int cardWidth = Math.min(460, screenWidth - 36);
        int cardHeight = 112;
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
                centerX, y + 94, (alpha << 24) | 0x00C8C8C8);
    }

    private void renderDeathCamPanel(GuiGraphics graphics, Font font, int centerX, int screenHeight) {
        if (!ClientTdmState.isDead()) {
            return;
        }
        int panelWidth = 240;
        int panelHeight = 56;
        int x = centerX - panelWidth / 2;
        int y = screenHeight / 2 - 30;

        graphics.fillGradient(x, y, x + panelWidth, y + panelHeight, 0xCC2B0E0E, 0xCC120A0A);
        graphics.fill(x, y + panelHeight - 2, x + panelWidth, y + panelHeight, 0xFFFF5A5A);

        drawCenteredString(graphics, font, Component.translatable("hud.codpattern.tdm.death.title").getString(), centerX,
                y + 7, 0xFFFFC4C4);
        drawCenteredString(graphics, font,
                Component.translatable("hud.codpattern.tdm.death.killer", ClientTdmState.killerName()).getString(), centerX,
                y + 20, 0xFFF1F1F1);
        drawCenteredString(graphics, font,
                Component.translatable("hud.codpattern.tdm.death.respawn", String.format(Locale.ROOT, "%.1f",
                        ClientTdmState.deathCamTicks() / 20.0f))
                        .getString(),
                centerX, y + 34, 0xFFD8D8D8);
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
}
