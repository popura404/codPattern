package com.cdp.codpattern.client.gui;

import com.cdp.codpattern.client.gui.screen.backpack.BackpackUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

public final class BackNavigation {
    public static final Component LABEL = Component.translatable("screen.codpattern.common.back");
    private static final Component ESC = Component.literal("ESC");
    private static final int HEIGHT = 18;

    private BackNavigation() {}

    private static int contentWidth() {
        return Math.max(56, BackpackUi.textWidth(ESC, 1f) + 8 + 5 + BackpackUi.textWidth(LABEL, 1f));
    }

    /** Position relative to the lower left in each screen's own coordinate space. */
    public static ScreenRectangle bounds(int coordinateWidth, int coordinateHeight) {
        double scale = Math.min(coordinateWidth / 960.0, coordinateHeight / 540.0);
        return new ScreenRectangle((int) Math.round(36 * scale), coordinateHeight - (int) Math.round(35 * scale),
                Math.max(1, (int) Math.round(contentWidth() * scale)), Math.max(1, (int) Math.round(HEIGHT * scale)));
    }

    public static Button button(ScreenRectangle bounds, Runnable action) {
        return BackpackUi.button(bounds.left(), bounds.top(), bounds.width(), bounds.height(), LABEL, action,
                (g, b) -> draw(g, bounds, b.isHovered() || (b.isFocused() && Minecraft.getInstance().getLastInputType().isKeyboard())));
    }

    public static void draw(GuiGraphics g, ScreenRectangle bounds, boolean highlighted) {
        int keyWidth = BackpackUi.textWidth(ESC, 1f) + 8;
        int labelX = keyWidth + 5;
        int width = contentWidth();
        int color = highlighted ? BackpackUi.INK : 0xFFC1C9C7;
        g.pose().pushPose();
        g.pose().translate(bounds.left(), bounds.top(), 0);
        g.pose().scale(bounds.width() / (float) width, bounds.height() / (float) HEIGHT, 1);
        BackpackUi.keycap(g, ESC, 0, 0, keyWidth, HEIGHT, color, highlighted ? BackpackUi.ACCENT : 0xFF788B89);
        BackpackUi.text(g, LABEL, labelX, (HEIGHT - BackpackUi.lineHeight(1f)) / 2,
                width - labelX, 1f, color);
        g.pose().popPose();
    }
}
