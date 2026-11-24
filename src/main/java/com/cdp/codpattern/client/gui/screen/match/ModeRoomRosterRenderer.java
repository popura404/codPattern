package com.cdp.codpattern.client.gui.screen.match;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.model.TeamDescriptor;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import static com.cdp.codpattern.client.gui.GuiTextHelper.referenceScaled;

final class ModeRoomRosterRenderer {
    private static final ResourceLocation GUI_ICONS = Objects.requireNonNull(
            ResourceLocation.tryBuild("minecraft", "textures/gui/icons.png"));
    private static final Comparator<PlayerInfo> PLAYER_ORDER = Comparator
            .comparingInt(PlayerInfo::kills).reversed()
            .thenComparingInt(PlayerInfo::deaths)
            .thenComparing(PlayerInfo::name, String.CASE_INSENSITIVE_ORDER);

    private record Team(Component label, int accent, int x, int y, int width, List<PlayerInfo> players) {}

    private final List<Team> teams = new ArrayList<>();
    private final int headerHeight = referenceScaled(17);
    private final int rowHeight = referenceScaled(16);
    private int height;

    ModeRoomRosterRenderer(int width, String gameType, Map<String, List<PlayerInfo>> playersByTeam) {
        // Keep registered team order and include extra teams reported by the server.
        Map<String, List<PlayerInfo>> remaining = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        remaining.putAll(playersByTeam);
        List<TeamDescriptor> descriptors = GameModeRegistry.getOrDefault(gameType).teams();
        List<String> registeredTeams = descriptors.isEmpty() ? List.of("kortac", "specgru")
                : descriptors.stream().map(TeamDescriptor::teamName).toList();
        int teamCount = remaining.size();
        for (String name : registeredTeams) {
            if (!remaining.containsKey(name)) teamCount++;
        }
        int columnGap = referenceScaled(8);
        int columns = Math.max(1, Math.min(teamCount, (width + columnGap) / (referenceScaled(128) + columnGap)));
        int columnWidth = (width - (columns - 1) * columnGap) / columns;
        int[] nextY = new int[columns];
        for (TeamDescriptor descriptor : descriptors) {
            addTeam(Component.translatable(descriptor.displayNameKey()), descriptor.accentColor(),
                    remaining.remove(descriptor.teamName()), width, columnWidth, columnGap, nextY);
        }
        if (descriptors.isEmpty()) {
            addTeam(Component.translatable("screen.codpattern.room.team.kortac"), 0xFFE35A5A,
                    remaining.remove("kortac"), width, columnWidth, columnGap, nextY);
            addTeam(Component.translatable("screen.codpattern.room.team.specgru"), 0xFF66A6FF,
                    remaining.remove("specgru"), width, columnWidth, columnGap, nextY);
        }
        remaining.forEach((id, players) -> {
            String key = "screen.codpattern.room.team." + id.toLowerCase(Locale.ROOT);
            Component label = Component.translatableWithFallback(key, id.toUpperCase(Locale.ROOT));
            addTeam(label, 0xFFB4C1CE, players, width, columnWidth, columnGap, nextY);
        });
    }

    private void addTeam(Component label, int accent, List<PlayerInfo> players, int width,
                         int columnWidth, int gap, int[] nextY) {
        int column = teams.size() % nextY.length;
        int x = column * (columnWidth + gap);
        int teamWidth = column == nextY.length - 1 ? width - x : columnWidth;
        List<PlayerInfo> members = players == null ? List.of() : players.stream().sorted(PLAYER_ORDER).toList();
        teams.add(new Team(label, accent | 0xFF000000, x, nextY[column], teamWidth, members));
        int bottom = nextY[column] + headerHeight + rowHeight * Math.max(1, members.size());
        nextY[column] = bottom + referenceScaled(7);
        height = Math.max(height, bottom);
    }

    int height() {
        return height;
    }

    void render(GuiGraphics graphics, Minecraft mc, int x, int y, int clipTop, int clipBottom, float alpha) {
        int padding = referenceScaled(5);
        int lineHeight = GuiTextHelper.referenceLineHeight(mc.font);
        for (Team team : teams) {
            int left = x + team.x();
            int top = y + team.y();
            int rowsTop = top + headerHeight;
            int rows = Math.max(1, team.players().size());
            if (top >= clipBottom || rowsTop + rows * rowHeight <= clipTop) {
                continue;
            }
            if (top < clipBottom && rowsTop > clipTop) {
                graphics.fill(left, top, left + team.width(), rowsTop, tint(0x55151A1C, alpha));
                graphics.fill(left, top, left + referenceScaled(2), rowsTop, tint(team.accent(), alpha));
                String count = Integer.toString(team.players().size());
                int right = left + team.width() - padding;
                int countWidth = GuiTextHelper.referenceWidth(mc.font, count);
                int textY = top + (headerHeight - lineHeight) / 2;
                GuiTextHelper.drawReferenceEllipsizedString(graphics, mc.font, team.label(), left + padding, textY,
                        team.width() - 3 * padding - countWidth, tint(team.accent(), alpha), false);
                GuiTextHelper.drawReferenceString(graphics, mc.font, count, right - countWidth, textY,
                        tint(0xFFD5DEDF, alpha), false);
            }
            int first = Math.max(0, (clipTop - rowsTop) / rowHeight);
            int end = Math.min(rows, (clipBottom - rowsTop + rowHeight - 1) / rowHeight);
            if (end <= first) {
                continue;
            }
            if (team.players().isEmpty()) {
                GuiTextHelper.drawReferenceEllipsizedString(graphics, mc.font,
                        Component.translatable("screen.codpattern.room.no_players"), left + padding,
                        rowsTop + (rowHeight - lineHeight) / 2, team.width() - 2 * padding,
                        tint(0xFFB1BCBF, alpha), false);
            } else {
                for (int row = first; row < end; row++) {
                    renderPlayer(graphics, mc, left, rowsTop + row * rowHeight, team.width(), team.players().get(row), alpha);
                }
            }
        }
    }

    private void renderPlayer(GuiGraphics graphics, Minecraft mc, int x, int y, int width,
                              PlayerInfo player, float alpha) {
        int pad = referenceScaled(5);
        int textY = y + (rowHeight - GuiTextHelper.referenceLineHeight(mc.font)) / 2;
        int marker = referenceScaled(3);
        int markerY = y + (rowHeight - marker) / 2;
        graphics.fill(x, y + rowHeight - 1, x + width, y + rowHeight, tint(0x26D7E1E4, alpha));
        graphics.fill(x + pad, markerY, x + pad + marker, markerY + marker,
                tint(player.isAlive() ? 0xFF95D5AE : 0xFFD5817D, alpha));

        int nameX = x + pad + marker + referenceScaled(4);
        int pingX = x + width - pad - referenceScaled(10);
        int statRight = pingX - referenceScaled(4);
        String kd = player.kills() + "/" + player.deaths();
        int statWidth = Math.min(GuiTextHelper.referenceWidth(mc.font, kd), Math.max(0, (statRight - nameX) / 2));
        GuiTextHelper.drawReferenceRightAlignedEllipsizedString(graphics, mc.font, kd, statRight, textY, statWidth,
                tint(0xFFD9DFDF, alpha), false);
        int nameRight = statRight - statWidth - pad;
        if (player.isReady() || player.isInvincible()) {
            String status = player.isReady() && player.isInvincible() ? "§aR §eINV" : player.isReady() ? "§aR" : "§eINV";
            int statusWidth = Math.min(Math.max(0, nameRight - nameX), GuiTextHelper.referenceWidth(mc.font, status));
            GuiTextHelper.drawReferenceRightAlignedEllipsizedString(graphics, mc.font, status, nameRight, textY,
                    statusWidth, tint(0xFFFFFFFF, alpha), false);
            nameRight -= statusWidth + pad;
        }
        boolean local = mc.player != null && mc.player.getUUID().equals(player.uuid());
        GuiTextHelper.drawReferenceEllipsizedString(graphics, mc.font, player.name(), nameX, textY,
                nameRight - nameX, tint(local ? 0xFFFFDE99 : 0xFFF1F4F4, alpha), false);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        int pingHeight = referenceScaled(8);
        graphics.blit(GUI_ICONS, pingX, y + (rowHeight - pingHeight) / 2, referenceScaled(10), pingHeight,
                0f, 176f + ModeRoomTextFormatter.pingBucket(player.pingMs()) * 8, 10, 8, 256, 256);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static int tint(int color, float alpha) {
        return Math.round((color >>> 24) * alpha) << 24 | (color & 0xFFFFFF);
    }
}
