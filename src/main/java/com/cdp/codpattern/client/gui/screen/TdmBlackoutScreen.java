package com.cdp.codpattern.client.gui.screen;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.client.ClientTdmState;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class TdmBlackoutScreen extends Screen {
    private static final int ACTION_BAR_Y_OFFSET = 68;
    private static final int MAP_MARGIN_LEFT = 20;
    private static final int MAP_INFO_BOTTOM_OFFSET = 62;
    private static final int MAP_INFO_LINE_GAP = 28;
    private static final float MAP_NAME_SCALE = 2.0f;

    public TdmBlackoutScreen() {
        super(Component.empty());
    }

    public static void show(Minecraft minecraft) {
        if (minecraft.screen instanceof TdmBlackoutScreen) {
            return;
        }
        minecraft.setScreen(new TdmBlackoutScreen());
    }

    public static void hide(Minecraft minecraft) {
        if (minecraft.screen instanceof TdmBlackoutScreen) {
            minecraft.setScreen(null);
        }
    }

    public static void sync(Minecraft minecraft) {
        if (shouldDisplay()) {
            show(minecraft);
            return;
        }
        hide(minecraft);
    }

    @Override
    public void tick() {
        if (!shouldDisplay()) {
            hide(Minecraft.getInstance());
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!shouldDisplay()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int blackoutAlpha = Mth.clamp((int) (ClientTdmState.getBlackoutAlpha() * 255.0f), 0, 255);
        if (blackoutAlpha > 0) {
            graphics.fill(0, 0, this.width, this.height, blackoutAlpha << 24);
        }

        if ("COUNTDOWN".equals(ClientTdmState.currentPhase())) {
            renderActionbarCountdown(graphics, font);
        }
        if ("WARMUP".equals(ClientTdmState.currentPhase())) {
            renderWarmupCountdown(graphics, font);
            renderMapInfo(graphics, font, minecraft);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static boolean shouldDisplay() {
        return "COUNTDOWN".equals(ClientTdmState.currentPhase()) || "WARMUP".equals(ClientTdmState.currentPhase());
    }

    private void renderActionbarCountdown(GuiGraphics graphics, Font font) {
        int secondsLeft = Math.max(1, (ClientTdmState.remainingTimeTicks() + 19) / 20);
        String message = Component.translatable("hud.codpattern.tdm.actionbar.countdown", secondsLeft).getString();
        if (message.isBlank()) {
            return;
        }

        int messageWidth = font.width(message);
        int centerX = this.width / 2;
        int y = this.height - ACTION_BAR_Y_OFFSET;
        graphics.fill(centerX - messageWidth / 2 - 4, y - 4, centerX + messageWidth / 2 + 4, y + font.lineHeight + 4, 0x55000000);
        graphics.drawString(font, message, centerX - messageWidth / 2, y, 0xFFFFFFFF, true);
    }

    private void renderWarmupCountdown(GuiGraphics graphics, Font font) {
        int secondsLeft = Math.max(1, (ClientTdmState.remainingTimeTicks() + 19) / 20);
        String text = String.valueOf(secondsLeft);
        float scale = 4.9f;
        int y = this.height / 2 - Math.round((font.lineHeight * scale) / 2.0f);

        graphics.pose().pushPose();
        graphics.pose().translate(this.width / 2.0f, y, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(font, text, -font.width(text) / 2, 0, 0xFFFFE28A, true);
        graphics.pose().popPose();
    }

    private void renderMapInfo(GuiGraphics graphics, Font font, Minecraft minecraft) {
        int infoAlpha = Mth.clamp((int) (ClientTdmState.getBlackoutInfoAlpha() * 255.0f), 0, 255);
        if (infoAlpha <= 0) {
            return;
        }

        RoomDisplayInfo displayInfo = resolveRoomDisplayInfo();
        String modeName = resolveModeName(displayInfo.gameType());
        String mapName = displayInfo.mapName();
        String worldTime = formatWorldTime(minecraft.level);
        int baseY = this.height - MAP_INFO_BOTTOM_OFFSET - MAP_INFO_LINE_GAP;

        renderScaledMapInfoLine(graphics, font, modeName, baseY, withAlpha(0xFFFFFFFF, infoAlpha));
        renderScaledMapInfoLine(graphics, font, mapName, baseY + MAP_INFO_LINE_GAP, withAlpha(0xFFFFFFFF, infoAlpha));
        graphics.drawString(font, worldTime, MAP_MARGIN_LEFT, baseY + MAP_INFO_LINE_GAP * 2,
                withAlpha(0xFFEAEAEA, infoAlpha), true);
    }

    private void renderScaledMapInfoLine(GuiGraphics graphics, Font font, String text, int y, int color) {
        String fittedText = GuiTextHelper.ellipsize(
                font,
                text == null ? "" : text,
                Math.max(1, (int) ((this.width - MAP_MARGIN_LEFT - 20) / MAP_NAME_SCALE)));

        if (fittedText.isEmpty()) {
            return;
        }

        graphics.pose().pushPose();
        graphics.pose().translate(MAP_MARGIN_LEFT, y, 0.0f);
        graphics.pose().scale(MAP_NAME_SCALE, MAP_NAME_SCALE, 1.0f);
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

    private int withAlpha(int color, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    private record RoomDisplayInfo(String gameType, String mapName) {
    }
}
