package com.cdp.codpattern.client.gui.screen.tdm;

import com.cdp.codpattern.app.tdm.model.TdmTeamNames;
import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TdmRoomRosterRenderer {
    private TdmRoomRosterRenderer() {
    }

    public static void render(
            GuiGraphics graphics,
            Minecraft mc,
            int panelX,
            int panelWidth,
            int startY,
            int maxY,
            Map<String, List<PlayerInfo>> teamPlayers,
            float alphaFactor,
            long nowMs) {
        List<String> teamOrder = new ArrayList<>();
        teamOrder.add(TdmTeamNames.KORTAC);
        teamOrder.add(TdmTeamNames.SPECGRU);
        for (String key : teamPlayers.keySet()) {
            if (!teamOrder.contains(key)) {
                teamOrder.add(key);
            }
        }

        int y = startY;
        int rowIndex = 0;
        for (String teamName : teamOrder) {
            List<PlayerInfo> players = new ArrayList<>(teamPlayers.getOrDefault(teamName, List.of()));
            players.sort(playerComparator());
            RenderResult result = renderSingleTeamRoster(
                    graphics,
                    mc,
                    panelX,
                    panelWidth,
                    y,
                    maxY,
                    teamName,
                    players,
                    rowIndex,
                    alphaFactor,
                    nowMs);
            y = result.nextY;
            rowIndex = result.nextRowIndex;
            if (y > maxY) {
                break;
            }
        }
    }

    private static RenderResult renderSingleTeamRoster(
            GuiGraphics graphics,
            Minecraft mc,
            int panelX,
            int panelWidth,
            int startY,
            int maxY,
            String teamName,
            List<PlayerInfo> players,
            int rowIndex,
            float alphaFactor,
            long nowMs) {
        int accent = getTeamAccentColor(teamName);
        int headerHeight = GuiTextHelper.referenceScaled(14);
        int lineHeight = GuiTextHelper.referenceLineHeight(mc.font);
        if (startY + headerHeight > maxY) {
            return new RenderResult(maxY + 1, rowIndex);
        }

        graphics.fill(panelX, startY, panelX + panelWidth, startY + headerHeight,
                scaleAlpha(withAlpha(accent, 40), alphaFactor));
        graphics.fill(panelX, startY + headerHeight - 1, panelX + panelWidth, startY + headerHeight,
                scaleAlpha(withAlpha(accent, 160), alphaFactor));

        String teamKey = "screen.codpattern.tdm_room.team." + teamName.toLowerCase(Locale.ROOT);
        String teamLabel = Component.translatable(teamKey).getString();
        if (teamLabel.equals(teamKey)) {
            teamLabel = teamName.toUpperCase(Locale.ROOT);
        }
        String headerText = teamLabel + "  (" + players.size() + ")";
        GuiTextHelper.drawReferenceString(
                graphics,
                mc.font,
                headerText,
                panelX + GuiTextHelper.referenceScaled(5),
                startY + GuiTextHelper.referenceScaled(3),
                scaleAlpha(accent, alphaFactor),
                false);

        int y = startY + headerHeight + GuiTextHelper.referenceScaled(3);
        if (players.isEmpty()) {
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    Component.translatable("screen.codpattern.tdm_room.no_players"),
                    panelX + GuiTextHelper.referenceScaled(5),
                    y,
                    scaleAlpha(CodTheme.TEXT_DIM, alphaFactor),
                    false);
            return new RenderResult(y + lineHeight + GuiTextHelper.referenceScaled(5), rowIndex);
        }

        int rowHeight = GuiTextHelper.referenceScaled(14);
        int currentIndex = rowIndex;
        for (PlayerInfo player : players) {
            if (y + rowHeight > maxY) {
                GuiTextHelper.drawReferenceString(
                        graphics,
                        mc.font,
                        "...",
                        panelX + panelWidth - GuiTextHelper.referenceWidth(mc.font, "...") - GuiTextHelper.referenceScaled(6),
                        Math.max(startY + GuiTextHelper.referenceScaled(2), maxY - lineHeight),
                        scaleAlpha(CodTheme.TEXT_DIM, alphaFactor),
                        false);
                return new RenderResult(maxY + 1, currentIndex);
            }
            renderPlayerStatCard(graphics, mc, panelX, panelWidth, y, rowHeight, player, accent, currentIndex, alphaFactor, nowMs);
            y += rowHeight + GuiTextHelper.referenceScaled(3);
            currentIndex++;
        }
        return new RenderResult(y + GuiTextHelper.referenceScaled(4), currentIndex);
    }

    private static void renderPlayerStatCard(
            GuiGraphics graphics,
            Minecraft mc,
            int x,
            int width,
            int y,
            int height,
            PlayerInfo player,
            int teamColor,
            int rowIndex,
            float alphaFactor,
            long nowMs) {
        float alivePulse = player.isAlive()
                ? (0.75f + 0.25f * (0.5f + 0.5f * (float) Math.sin((nowMs / 185.0) + rowIndex * 0.55)))
                : 1.0f;

        int cardTop = player.isAlive() ? withAlpha(teamColor, 32) : 0x66331515;
        int cardBottom = player.isAlive() ? withAlpha(0xFF0F1114, 190) : 0x661A1212;
        int lifeColor = player.isAlive()
                ? withAlpha(0xFF4DFF8A, (int) (180.0f * alivePulse))
                : 0xFFFF6B6B;
        int cardRight = x + width;

        graphics.fillGradient(x, y, cardRight, y + height,
                scaleAlpha(cardTop, alphaFactor),
                scaleAlpha(cardBottom, alphaFactor));
        graphics.fill(x, y, x + 2, y + height, scaleAlpha(lifeColor, alphaFactor));

        String aliveMark = player.isAlive() ? "●" : "■";
        String readyMark = player.isReady() ? "§a[R]" : "§7[ ]";
        String nameText = isLocalPlayer(mc.player, player) ? "§e" + player.name() : player.name();
        String headline = readyMark + " " + aliveMark + " " + nameText;
        String meta = String.format("§c%d§7/§f%d  §8|  §b%dms",
                player.kills(),
                player.deaths(),
                Math.max(0, player.pingMs()));

        int textX = x + GuiTextHelper.referenceScaled(6);
        int rightPadding = GuiTextHelper.referenceScaled(5);
        int topY = y + Math.max(1, (height - GuiTextHelper.referenceLineHeight(mc.font)) / 2);
        int metaWidth = GuiTextHelper.referenceWidth(mc.font, meta);
        int nameMaxWidth = Math.max(GuiTextHelper.referenceScaled(28), width - metaWidth - GuiTextHelper.referenceScaled(18));
        GuiTextHelper.drawReferenceEllipsizedString(
                graphics,
                mc.font,
                headline,
                textX,
                topY,
                nameMaxWidth,
                scaleAlpha(0xFFF4F4F4, alphaFactor),
                false);
        GuiTextHelper.drawReferenceRightAlignedEllipsizedString(
                graphics,
                mc.font,
                meta,
                cardRight - rightPadding,
                topY,
                Math.max(GuiTextHelper.referenceScaled(28), width / 2),
                scaleAlpha(0xFFB5B5B5, alphaFactor),
                false);
    }

    private static Comparator<PlayerInfo> playerComparator() {
        return Comparator
                .comparingInt(PlayerInfo::kills).reversed()
                .thenComparingInt(PlayerInfo::deaths)
                .thenComparing(PlayerInfo::name, String.CASE_INSENSITIVE_ORDER);
    }

    private static int getTeamAccentColor(String teamName) {
        if (TdmTeamNames.KORTAC.equalsIgnoreCase(teamName)) {
            return 0xFFE35A5A;
        }
        if (TdmTeamNames.SPECGRU.equalsIgnoreCase(teamName)) {
            return 0xFF66A6FF;
        }
        return 0xFFB4C1CE;
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

    private static boolean isLocalPlayer(LocalPlayer localPlayer, PlayerInfo player) {
        return localPlayer != null && player != null && player.uuid().equals(localPlayer.getUUID());
    }

    private record RenderResult(int nextY, int nextRowIndex) {
    }
}
