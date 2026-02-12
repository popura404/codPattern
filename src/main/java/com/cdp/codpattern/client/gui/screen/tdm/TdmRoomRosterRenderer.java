package com.cdp.codpattern.client.gui.screen.tdm;

import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.fpsmatch.map.CodTdmMap;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TdmRoomRosterRenderer {
    private TdmRoomRosterRenderer() {
    }

    public static void render(GuiGraphics graphics,
            Minecraft mc,
            int panelX,
            int panelWidth,
            int startY,
            int maxY,
            Map<String, List<PlayerInfo>> teamPlayers) {
        List<String> teamOrder = new ArrayList<>();
        teamOrder.add(CodTdmMap.TEAM_KORTAC);
        teamOrder.add(CodTdmMap.TEAM_SPECGRU);
        for (String key : teamPlayers.keySet()) {
            if (!teamOrder.contains(key)) {
                teamOrder.add(key);
            }
        }

        int y = startY;
        for (String teamName : teamOrder) {
            List<PlayerInfo> players = teamPlayers.getOrDefault(teamName, List.of());
            y = renderSingleTeamRoster(graphics, mc, panelX, panelWidth, y, maxY, teamName, players);
            if (y > maxY) {
                break;
            }
        }
    }

    private static int renderSingleTeamRoster(GuiGraphics graphics,
            Minecraft mc,
            int panelX,
            int panelWidth,
            int startY,
            int maxY,
            String teamName,
            List<PlayerInfo> players) {
        int accent = getTeamAccentColor(teamName);
        int headerHeight = 14;
        if (startY + headerHeight > maxY) {
            return maxY + 1;
        }

        graphics.fill(panelX, startY, panelX + panelWidth, startY + headerHeight, withAlpha(accent, 40));
        graphics.fill(panelX, startY + headerHeight - 1, panelX + panelWidth, startY + headerHeight, withAlpha(accent, 160));

        String teamKey = "screen.codpattern.tdm_room.team." + teamName.toLowerCase(Locale.ROOT);
        String teamLabel = Component.translatable(teamKey).getString();
        if (teamLabel.equals(teamKey)) {
            teamLabel = teamName.toUpperCase(Locale.ROOT);
        }
        String headerText = teamLabel + "  (" + players.size() + ")";
        graphics.drawString(mc.font, headerText, panelX + 5, startY + 3, accent);

        int y = startY + headerHeight + 3;
        if (players.isEmpty()) {
            graphics.drawString(mc.font, Component.translatable("screen.codpattern.tdm_room.no_players"), panelX + 5, y,
                    CodTheme.TEXT_DIM);
            return y + 14;
        }

        int rowHeight = 24;
        for (PlayerInfo player : players) {
            if (y + rowHeight > maxY) {
                graphics.drawString(mc.font, "...", panelX + panelWidth - 14, Math.max(startY + 2, maxY - 9),
                        CodTheme.TEXT_DIM);
                return maxY + 1;
            }
            renderPlayerStatCard(graphics, mc, panelX, panelWidth, y, rowHeight, player, accent);
            y += rowHeight + 3;
        }
        return y + 4;
    }

    private static void renderPlayerStatCard(GuiGraphics graphics,
            Minecraft mc,
            int x,
            int width,
            int y,
            int height,
            PlayerInfo player,
            int teamColor) {
        int cardTop = player.isAlive() ? withAlpha(teamColor, 30) : 0x66331515;
        int cardBottom = player.isAlive() ? withAlpha(0xFF0F1114, 190) : 0x661A1212;
        int lifeColor = player.isAlive() ? 0xFF4DFF8A : 0xFFFF6B6B;
        int cardRight = x + width;

        graphics.fillGradient(x, y, cardRight, y + height, cardTop, cardBottom);
        graphics.fill(x, y, x + 2, y + height, lifeColor);

        String aliveMark = player.isAlive() ? "● " : "✖ ";
        String readyMark = player.isReady() ? "§a[R] " : "§7[ ] ";
        String kdText = TdmRoomTextFormatter.formatKd(player.kills(), player.deaths());
        String headline = readyMark + aliveMark + player.name();
        String scoreText = "K/D " + player.kills() + "/" + player.deaths();
        String meta = Component.translatable("screen.codpattern.tdm_room.player_meta",
                TdmRoomTextFormatter.shortPlayerId(player.uuid()), kdText, Math.max(0, player.pingMs())).getString();

        graphics.drawString(mc.font, headline, x + 6, y + 3, 0xFFF4F4F4);
        graphics.drawString(mc.font, scoreText, cardRight - mc.font.width(scoreText) - 5, y + 3, 0xFFCCCCCC);
        graphics.drawString(mc.font, meta, x + 6, y + 13, 0xFFB5B5B5);
    }

    private static int getTeamAccentColor(String teamName) {
        if (CodTdmMap.TEAM_KORTAC.equalsIgnoreCase(teamName)) {
            return 0xFFE35A5A;
        }
        if (CodTdmMap.TEAM_SPECGRU.equalsIgnoreCase(teamName)) {
            return 0xFF66A6FF;
        }
        return 0xFFB4C1CE;
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }
}
