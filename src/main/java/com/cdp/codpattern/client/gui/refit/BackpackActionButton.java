package com.cdp.codpattern.client.gui.refit;

import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.NotNull;

/**
 * 背包动作按钮（配置）- COD MWII 2022 风格 - 绿色主题
 */
public class BackpackActionButton extends Button {
    private static final long REVEAL_MS = 130L;
    private static final long HOVER_PULSE_MS = 900L;

    private int focusedTimes = 0;
    private int hoverTicks = 0;
    private final int normalColor;
    private final int hoverColor;
    private final long createdAtMs = System.currentTimeMillis();

    // MWII 绿色主题色
    private static final int GREEN_BG_TOP = 0xE8183018;
    private static final int GREEN_BG_BOTTOM = 0xF0102810;
    private static final int GREEN_HOVER_TOP = 0xE8204020;
    private static final int GREEN_HOVER_BOTTOM = 0xF0183818;

    public BackpackActionButton(int x, int y, int width, int height, Component label, OnPress onPress, int normalColor, int hoverColor) {
        super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
        this.normalColor = normalColor;
        this.hoverColor = hoverColor;
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float revealFactor = revealProgress();
        if (this.isHoveredOrFocused() && focusedTimes == 0) {
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.playNotifySound(
                            SoundEvents.BAMBOO_WOOD_BUTTON_CLICK_ON,
                            SoundSource.PLAYERS, 0.5f, 1.2f
                    );
                }
            });
            focusedTimes = 1;
        } else if (!this.isHoveredOrFocused()) {
            focusedTimes = 0;
        }
        if (this.isHoveredOrFocused()) {
            hoverTicks = Math.min(6, hoverTicks + 1);
        } else {
            hoverTicks = Math.max(0, hoverTicks - 1);
        }

        // 基础背景 - 绿色主题
        graphics.fillGradient(this.getX(), this.getY(),
                this.getX() + this.width, this.getY() + this.height,
                scaleAlpha(GREEN_BG_TOP, revealFactor),
                scaleAlpha(GREEN_BG_BOTTOM, revealFactor));
        graphics.fill(this.getX(), this.getY(), this.getX() + 1, this.getY() + this.height,
                scaleAlpha(0x18FFFFFF, revealFactor));

        if (isHoveredOrFocused()) {
            renderOnHoveredOrFocused(graphics, revealFactor);
        }
        renderTitle(graphics, revealFactor);
    }

    protected void renderOnHoveredOrFocused(GuiGraphics graphics, float revealFactor) {
        float hoverFactor = hoverTicks / 6.0f;
        float pulse = 0.68f + (0.32f * oscillate(HOVER_PULSE_MS));
        // 悬停背景 - 更亮的绿色
        graphics.fillGradient(this.getX(), this.getY(),
                this.getX() + this.width, this.getY() + this.height,
                scaleAlpha(GREEN_HOVER_TOP, revealFactor * (0.55f + (0.25f * hoverFactor))),
                scaleAlpha(GREEN_HOVER_BOTTOM, revealFactor * (0.65f + (0.25f * hoverFactor))));

        // 顶部荧光绿边框
        graphics.fill(this.getX(), this.getY(),
                this.getX() + this.width, this.getY() + 1,
                scaleAlpha(CodTheme.HOVER_BORDER, revealFactor * pulse));

        // 底部荧光绿边框
        graphics.fill(this.getX(), this.getY() + this.height - 1,
                this.getX() + this.width, this.getY() + this.height,
                scaleAlpha(CodTheme.HOVER_BORDER_SEMI, revealFactor * pulse));
        graphics.fill(this.getX(), this.getY(), this.getX() + 2, this.getY() + this.height,
                scaleAlpha(withAlpha(CodTheme.HOVER_BORDER, (int) (28.0f + (32.0f * hoverFactor))), revealFactor));
    }

    protected void renderTitle(GuiGraphics graphics, float revealFactor) {
        Minecraft minecraft = Minecraft.getInstance();
        int textX = this.getX() + 6;  // 左对齐
        int textY = this.getY() + (this.height - GuiTextHelper.referenceLineHeight(minecraft.font)) / 2;
        int color = this.isHoveredOrFocused() ? hoverColor : normalColor;
        GuiTextHelper.drawReferenceString(
                graphics,
                minecraft.font,
                this.getMessage(),
                textX,
                textY,
                scaleAlpha(color, revealFactor),
                true
        );
    }

    private int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private int scaleAlpha(int color, float factor) {
        int alpha = (color >>> 24) & 0xFF;
        int scaled = Math.max(0, Math.min(255, (int) (alpha * Math.max(0.0f, Math.min(1.0f, factor)))));
        return (scaled << 24) | (color & 0x00FFFFFF);
    }

    private float revealProgress() {
        long elapsed = System.currentTimeMillis() - createdAtMs;
        float raw = Math.min(1.0f, Math.max(0.0f, elapsed / (float) REVEAL_MS));
        return 0.3f + (raw * 0.7f);
    }

    private float oscillate(long durationMs) {
        if (durationMs <= 0L) {
            return 1.0f;
        }
        double phase = ((System.currentTimeMillis() - createdAtMs) % durationMs) / (double) durationMs;
        return (float) ((Math.sin(phase * Math.PI * 2.0d) + 1.0d) * 0.5d);
    }
}
