package com.cdp.codpattern.client.gui.overlay;

import com.cdp.codpattern.client.zombies.ClientZombiesState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.List;
import java.util.Locale;

public final class ZombiesHudOverlay implements IGuiOverlay {
    public static final ZombiesHudOverlay INSTANCE = new ZombiesHudOverlay();

    private static final int PANEL_BG = 0x8C050607;
    private static final int PANEL_BORDER = 0x55FFFFFF;
    private static final int TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int TEXT_SECONDARY = 0xFFC9D1D9;
    private static final int TEXT_ACCENT = 0xFFFFD166;
    private static final int TEXT_DANGER = 0xFFFF6B6B;
    private static final int TEXT_OK = 0xFF86EFAC;

    private ZombiesHudOverlay() {
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        if (!ClientZombiesState.shouldRenderHud()) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        renderTopStats(graphics, font, screenWidth);
        renderPlayerStats(graphics, font, screenWidth, screenHeight);
        renderSurvivors(graphics, font, screenWidth, screenHeight);
        renderPhaseNotice(graphics, font, screenWidth, screenHeight);
    }

    private static void renderTopStats(GuiGraphics graphics, Font font, int screenWidth) {
        int panelWidth = Math.min(260, Math.max(180, screenWidth - 24));
        int x = (screenWidth - panelWidth) / 2;
        int y = 10;
        fillPanel(graphics, x, y, panelWidth, 34);

        String wave = Component.translatable("hud.codpattern.zombies.wave", Math.max(0, ClientZombiesState.wave())).getString();
        String zombies = Component.translatable("hud.codpattern.zombies.zombies_left", Math.max(0, ClientZombiesState.zombiesLeft())).getString();
        String alive = Component.translatable(
                "hud.codpattern.zombies.alive",
                Math.max(0, ClientZombiesState.alivePlayers()),
                Math.max(0, ClientZombiesState.maxPlayers())).getString();

        graphics.drawString(font, wave, x + 10, y + 7, TEXT_ACCENT, true);
        graphics.drawString(font, zombies, x + panelWidth / 2 - font.width(zombies) / 2, y + 7, TEXT_PRIMARY, true);
        graphics.drawString(font, alive, x + panelWidth - font.width(alive) - 10, y + 7, TEXT_SECONDARY, true);
    }

    private static void renderPlayerStats(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        int panelWidth = Math.max(150, Math.min(190, screenWidth - 16));
        int panelHeight = 76;
        int x = Math.max(8, screenWidth - panelWidth - 10);
        int y = Math.max(54, screenHeight - panelHeight - 12);
        fillPanel(graphics, x, y, panelWidth, panelHeight);

        String points = Component.translatable("hud.codpattern.zombies.points", ClientZombiesState.points()).getString();
        String combat = Component.translatable(
                "hud.codpattern.zombies.combat",
                ClientZombiesState.kills(),
                ClientZombiesState.assists(),
                ClientZombiesState.deaths()).getString();
        String growth = "Armor " + Math.max(0, ClientZombiesState.armorLevel())
                + " | Upg " + Math.max(0, ClientZombiesState.primaryUpgradeLevel());
        String power = "Power " + (ClientZombiesState.powerEnabled() ? "ON" : "OFF");
        String buffs = "Buffs " + shortBuffList(ClientZombiesState.ownedBuffIds());
        int textWidth = panelWidth - 18;
        graphics.drawString(font, fit(font, points, textWidth), x + 9, y + 8, TEXT_ACCENT, true);
        graphics.drawString(font, fit(font, combat, textWidth), x + 9, y + 22, TEXT_SECONDARY, true);
        graphics.drawString(font, fit(font, growth, textWidth), x + 9, y + 36, TEXT_PRIMARY, true);
        graphics.drawString(font, fit(font, power, textWidth), x + 9, y + 50, ClientZombiesState.powerEnabled() ? TEXT_OK : TEXT_DANGER, true);
        graphics.drawString(font, fit(font, buffs, textWidth), x + 9, y + 64, TEXT_SECONDARY, true);
    }

    private static void renderSurvivors(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        List<ClientZombiesState.SurvivorStatus> survivors = ClientZombiesState.survivors();
        if (survivors.isEmpty()) {
            return;
        }

        int rowHeight = 14;
        int panelWidth = Math.min(190, Math.max(132, screenWidth / 3));
        int panelHeight = 10 + survivors.size() * rowHeight;
        int x = 10;
        int y = Math.max(54, screenHeight - panelHeight - 12);
        fillPanel(graphics, x, y, panelWidth, panelHeight);

        int rowY = y + 6;
        for (ClientZombiesState.SurvivorStatus survivor : survivors) {
            int color = survivorColor(survivor);
            String marker = survivor.self() ? "> " : "";
            String name = fit(font, marker + safeName(survivor.name()), panelWidth - 54);
            String points = Integer.toString(Math.max(0, survivor.points()));
            graphics.drawString(font, name, x + 8, rowY, color, true);
            graphics.drawString(font, points, x + panelWidth - font.width(points) - 8, rowY, TEXT_SECONDARY, true);
            rowY += rowHeight;
        }
    }

    private static void renderPhaseNotice(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        String phase = ClientZombiesState.phaseKey();
        if (phase == null || phase.isBlank() || "WAVE_ACTIVE".equals(phase)) {
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

    private static int secondsLeft() {
        return Math.max(1, (ClientZombiesState.remainingTimeTicks() + 19) / 20);
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

    private static String safeName(String name) {
        return name == null || name.isBlank() ? "Player" : name;
    }

    private static String shortBuffList(List<String> buffIds) {
        if (buffIds == null || buffIds.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        int count = Math.min(3, buffIds.size());
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(shortBuffName(buffIds.get(i)));
        }
        if (buffIds.size() > count) {
            builder.append(" +").append(buffIds.size() - count);
        }
        return builder.toString();
    }

    private static String shortBuffName(String buffId) {
        if (buffId == null || buffId.isBlank()) {
            return "?";
        }
        String[] parts = buffId.trim().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!part.isBlank()) {
                builder.append(part.charAt(0));
            }
        }
        return builder.isEmpty() ? buffId.trim() : builder.toString();
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

    private static void fillPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL_BG);
        graphics.fill(x, y, x + width, y + 1, PANEL_BORDER);
        graphics.fill(x, y + height - 1, x + width, y + height, PANEL_BORDER);
        graphics.fill(x, y, x + 1, y + height, PANEL_BORDER);
        graphics.fill(x + width - 1, y, x + width, y + height, PANEL_BORDER);
    }
}
