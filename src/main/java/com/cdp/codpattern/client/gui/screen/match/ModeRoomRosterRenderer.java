package com.cdp.codpattern.client.gui.screen.match;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.model.TeamDescriptor;
import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ModeRoomRosterRenderer {
    private static final String LEGACY_KORTAC_TEAM = "kortac";
    private static final String LEGACY_SPECGRU_TEAM = "specgru";
    private static final ResourceLocation GUI_ICONS_LOCATION =
            Objects.requireNonNull(ResourceLocation.tryBuild("minecraft", "textures/gui/icons.png"));

    private ModeRoomRosterRenderer() {
    }

    public static void render(
            GuiGraphics graphics,
            Minecraft mc,
            int panelX,
            int panelWidth,
            int startY,
            int maxY,
            Map<String, List<PlayerInfo>> teamPlayers,
            String gameType,
            float alphaFactor,
            long nowMs) {
        Map<String, List<PlayerInfo>> safeTeamPlayers = teamPlayers == null ? Map.of() : teamPlayers;
        List<String> teamOrder = orderedTeamNames(gameType, safeTeamPlayers);

        if (panelWidth < GuiTextHelper.referenceScaled(120)) {
            renderSingleColumn(
                    graphics,
                    mc,
                    panelX,
                    panelWidth,
                    startY,
                    maxY,
                    teamOrder,
                    safeTeamPlayers,
                    gameType,
                    alphaFactor,
                    nowMs);
            return;
        }

        int columnGap = GuiTextHelper.referenceScaled(8);
        int columnWidth = Math.max(GuiTextHelper.referenceScaled(52), (panelWidth - columnGap) / 2);
        int[] columnYs = new int[] {startY, startY};
        int rowIndex = 0;
        for (int index = 0; index < teamOrder.size(); index++) {
            String teamName = teamOrder.get(index);
            List<PlayerInfo> players = new ArrayList<>(safeTeamPlayers.getOrDefault(teamName, List.of()));
            players.sort(playerComparator());
            int columnIndex = index % 2;
            int columnX = panelX + columnIndex * (columnWidth + columnGap);
            int effectiveWidth = columnIndex == 0
                    ? columnWidth
                    : Math.max(GuiTextHelper.referenceScaled(52), panelX + panelWidth - columnX);
            RenderResult result = renderSingleTeamRoster(
                    graphics,
                    mc,
                    columnX,
                    effectiveWidth,
                    columnYs[columnIndex],
                    maxY,
                    teamName,
                    players,
                    rowIndex,
                    gameType,
                    alphaFactor,
                    nowMs);
            columnYs[columnIndex] = result.nextY;
            rowIndex = result.nextRowIndex;
        }
    }

    private static void renderSingleColumn(
            GuiGraphics graphics,
            Minecraft mc,
            int panelX,
            int panelWidth,
            int startY,
            int maxY,
            List<String> teamOrder,
            Map<String, List<PlayerInfo>> teamPlayers,
            String gameType,
            float alphaFactor,
            long nowMs) {
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
                    gameType,
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
            String gameType,
            float alphaFactor,
            long nowMs) {
        int accent = getTeamAccentColor(gameType, teamName);
        int headerHeight = GuiTextHelper.referenceScaled(15);
        int lineHeight = GuiTextHelper.referenceLineHeight(mc.font);
        if (startY + headerHeight > maxY) {
            return new RenderResult(maxY + 1, rowIndex);
        }

        graphics.fill(panelX, startY, panelX + panelWidth, startY + headerHeight,
                scaleAlpha(withAlpha(accent, 40), alphaFactor));
        graphics.fill(panelX, startY + headerHeight - 1, panelX + panelWidth, startY + headerHeight,
                scaleAlpha(withAlpha(accent, 160), alphaFactor));

        String teamLabel = teamDisplayName(gameType, teamName);
        String headerText = teamLabel + "  (" + players.size() + ")";
        GuiTextHelper.drawReferenceString(
                graphics,
                mc.font,
                headerText,
                panelX + GuiTextHelper.referenceScaled(5),
                startY + GuiTextHelper.referenceScaled(3),
                scaleAlpha(accent, alphaFactor),
                false);

        int y = startY + headerHeight + GuiTextHelper.referenceScaled(4);
        if (players.isEmpty()) {
            GuiTextHelper.drawReferenceString(
                    graphics,
                    mc.font,
                    Component.translatable("screen.codpattern.room.no_players"),
                    panelX + GuiTextHelper.referenceScaled(5),
                    y,
                    scaleAlpha(CodTheme.TEXT_DIM, alphaFactor),
                    false);
            return new RenderResult(y + lineHeight + GuiTextHelper.referenceScaled(5), rowIndex);
        }

        int rowHeight = GuiTextHelper.referenceScaled(13);
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
        String readyMark = player.isReady() ? " §aR" : "";
        String invincibleMark = player.isInvincible() ? " §eINV" : "";
        String nameText = isLocalPlayer(mc.player, player) ? "§e" + player.name() : player.name();
        String headline = aliveMark + " " + nameText + readyMark + invincibleMark;
        String meta = String.format("§c%d§7/§f%d",
                player.kills(),
                player.deaths());

        int textX = x + GuiTextHelper.referenceScaled(6);
        int rightPadding = GuiTextHelper.referenceScaled(5);
        int topY = y + Math.max(1, (height - GuiTextHelper.referenceLineHeight(mc.font)) / 2);
        int pingIconWidth = GuiTextHelper.referenceScaled(10);
        int pingIconHeight = GuiTextHelper.referenceScaled(8);
        int pingGap = GuiTextHelper.referenceScaled(4);
        int pingX = cardRight - rightPadding - pingIconWidth;
        int pingY = y + Math.max(0, (height - pingIconHeight) / 2);
        int metaRight = pingX - pingGap;
        int metaWidth = GuiTextHelper.referenceWidth(mc.font, meta);
        int nameMaxWidth = Math.max(
                GuiTextHelper.referenceScaled(20),
                width - metaWidth - pingIconWidth - GuiTextHelper.referenceScaled(20));
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
                metaRight,
                topY,
                Math.max(GuiTextHelper.referenceScaled(28), width / 2),
                scaleAlpha(0xFFB5B5B5, alphaFactor),
                false);
        renderPingIcon(graphics, pingX, pingY, player.pingMs(), alphaFactor);
    }

    private static Comparator<PlayerInfo> playerComparator() {
        return Comparator
                .comparingInt(PlayerInfo::kills).reversed()
                .thenComparingInt(PlayerInfo::deaths)
                .thenComparing(PlayerInfo::name, String.CASE_INSENSITIVE_ORDER);
    }

    private static List<String> orderedTeamNames(String gameType, Map<String, List<PlayerInfo>> teamPlayers) {
        List<String> teamOrder = new ArrayList<>();
        for (TeamDescriptor descriptor : modeTeamDescriptors(gameType)) {
            addTeamIfMissing(teamOrder, descriptor.teamName());
        }
        if (teamOrder.isEmpty()) {
            addTeamIfMissing(teamOrder, LEGACY_KORTAC_TEAM);
            addTeamIfMissing(teamOrder, LEGACY_SPECGRU_TEAM);
        }
        for (String key : teamPlayers.keySet()) {
            addTeamIfMissing(teamOrder, key);
        }
        return teamOrder;
    }

    private static void addTeamIfMissing(List<String> teamOrder, String teamName) {
        if (teamName == null || teamName.isBlank()) {
            return;
        }
        for (String existing : teamOrder) {
            if (existing.equalsIgnoreCase(teamName)) {
                return;
            }
        }
        teamOrder.add(teamName);
    }

    private static List<TeamDescriptor> modeTeamDescriptors(String gameType) {
        if (gameType == null || gameType.isBlank()) {
            return List.of();
        }
        return GameModeRegistry.getOrDefault(gameType).teams();
    }

    private static Optional<TeamDescriptor> modeTeamDescriptor(String gameType, String teamName) {
        if (teamName == null || teamName.isBlank()) {
            return Optional.empty();
        }
        return modeTeamDescriptors(gameType).stream()
                .filter(descriptor -> descriptor.teamName().equalsIgnoreCase(teamName))
                .findFirst();
    }

    private static String teamDisplayName(String gameType, String teamName) {
        Optional<TeamDescriptor> descriptor = modeTeamDescriptor(gameType, teamName);
        if (descriptor.isPresent()) {
            return Component.translatable(descriptor.get().displayNameKey()).getString();
        }
        String teamKey = "screen.codpattern.room.team." + teamName.toLowerCase(Locale.ROOT);
        String teamLabel = Component.translatable(teamKey).getString();
        if (teamLabel.equals(teamKey)) {
            return teamName.toUpperCase(Locale.ROOT);
        }
        return teamLabel;
    }

    private static int getTeamAccentColor(String gameType, String teamName) {
        Optional<TeamDescriptor> descriptor = modeTeamDescriptor(gameType, teamName);
        if (descriptor.isPresent()) {
            return descriptor.get().accentColor();
        }
        if (LEGACY_KORTAC_TEAM.equalsIgnoreCase(teamName)) {
            return 0xFFE35A5A;
        }
        if (LEGACY_SPECGRU_TEAM.equalsIgnoreCase(teamName)) {
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

    private static void renderPingIcon(GuiGraphics graphics, int x, int y, int pingMs, float alphaFactor) {
        int iconIndex = ModeRoomTextFormatter.pingBucket(pingMs);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, Math.max(0.0F, Math.min(1.0F, alphaFactor)));
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 100.0F);
        graphics.blit(
                GUI_ICONS_LOCATION,
                x,
                y,
                0,
                176 + iconIndex * 8,
                GuiTextHelper.referenceScaled(10),
                GuiTextHelper.referenceScaled(8));
        graphics.pose().popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private record RenderResult(int nextY, int nextRowIndex) {
    }
}
