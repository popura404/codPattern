package com.cdp.codpattern.client.gui.overlay;

import com.cdp.codpattern.client.zombies.ClientZombiesState;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.List;
import java.util.Locale;

public final class ZombiesHudOverlay implements IGuiOverlay {
    public static final ZombiesHudOverlay INSTANCE = new ZombiesHudOverlay();

    private static final int TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int TEXT_SECONDARY = 0xFFC9D1D9;
    private static final int TEXT_ACCENT = 0xFFFFD166;
    private static final int TEXT_WAVE_DARK_RED = 0xFF7D1414;
    private static final int TEXT_ZOMBIES_DARK_YELLOW = 0xFFC28B18;
    private static final int TEXT_DANGER = 0xFFFF6B6B;
    private static final int TEXT_OK = 0xFF86EFAC;
    private static final int PLAYER_STATUS_HEALTH_COLOR = 0xFFE53935;
    private static final int PLAYER_STATUS_ARMOR_COLOR = 0xFF4DA3FF;
    private static final float WAVE_NUMBER_SCALE = 7.5F;
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

    private ZombiesHudOverlay() {
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        if (!ClientZombiesState.shouldRenderHud()) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        TdmHudOverlay.INSTANCE.renderSharedPlayerScreenEffects(graphics, partialTick, screenWidth, screenHeight);
        renderTopStats(graphics, font, screenWidth);
        renderRoomTeammateStatus(graphics, font, screenWidth, screenHeight);
        renderPhaseNotice(graphics, font, screenWidth, screenHeight);
        renderPlayerStatus(graphics, font, screenWidth, screenHeight);
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

}
