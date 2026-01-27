package com.cdp.codpattern.client.gui.refit;

import com.cdp.codpattern.client.gui.CodTheme;
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

    private int focusedTimes = 0;
    private final int normalColor;
    private final int hoverColor;

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

        // 基础背景 - 绿色主题
        graphics.fillGradient(this.getX(), this.getY(),
                this.getX() + this.width, this.getY() + this.height,
                GREEN_BG_TOP, GREEN_BG_BOTTOM);

        if (isHoveredOrFocused()) {
            renderOnHoveredOrFocused(graphics);
        }
        renderTitle(graphics);
    }

    protected void renderOnHoveredOrFocused(GuiGraphics graphics) {
        // 悬停背景 - 更亮的绿色
        graphics.fillGradient(this.getX(), this.getY(),
                this.getX() + this.width, this.getY() + this.height,
                GREEN_HOVER_TOP, GREEN_HOVER_BOTTOM);

        // 顶部荧光绿边框
        graphics.fill(this.getX(), this.getY(),
                this.getX() + this.width, this.getY() + 1,
                CodTheme.HOVER_BORDER);

        // 底部荧光绿边框
        graphics.fill(this.getX(), this.getY() + this.height - 1,
                this.getX() + this.width, this.getY() + this.height,
                CodTheme.HOVER_BORDER_SEMI);
    }

    protected void renderTitle(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        int textX = this.getX() + 6;  // 左对齐
        int textY = this.getY() + (this.height - minecraft.font.lineHeight) / 2;
        graphics.drawString(minecraft.font, this.getMessage(), textX, textY, this.isHoveredOrFocused() ? hoverColor : normalColor, true);
    }
}
