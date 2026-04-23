package com.cdp.codpattern.client.gui.screen;

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
        String mapName = ClientTdmState.syncedMapName();
        if (mapName == null || mapName.isBlank()) {
            mapName = ClientTdmState.roomContextName();
        }
        String worldTime = formatWorldTime(minecraft.level);
        int baseY = this.height - MAP_INFO_BOTTOM_OFFSET;
        String fittedMapName = GuiTextHelper.ellipsize(
                font,
                mapName == null ? "" : mapName,
                Math.max(1, (int) ((this.width - MAP_MARGIN_LEFT - 20) / MAP_NAME_SCALE)));

        if (!fittedMapName.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(MAP_MARGIN_LEFT, baseY, 0.0f);
            graphics.pose().scale(MAP_NAME_SCALE, MAP_NAME_SCALE, 1.0f);
            graphics.drawString(font, fittedMapName, 0, 0, 0xFFFFFFFF, true);
            graphics.pose().popPose();
        }

        graphics.drawString(font, worldTime, MAP_MARGIN_LEFT, baseY + 28, 0xFFEAEAEA, true);
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
}
