package com.cdp.codpattern.client.gui.refit;

import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.network.AddBackpackPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.NotNull;

/**
 * 新建背包按钮 - COD MWII 2022 风格
 */
public class NewBackpackButton extends Button {

    private int focusedTimes = 0;
    private final int currentBackpackCount;

    // cod2022 风格绿色主题
    private static final int ACTIVE_BG_TOP = 0xE8183018;
    private static final int ACTIVE_BG_BOTTOM = 0xF0102810;
    private static final int ACTIVE_HOVER_TOP = 0xE8204020;
    private static final int ACTIVE_HOVER_BOTTOM = 0xF0183818;

    public NewBackpackButton(int x, int y, int width, int height, int currentCount) {
        super(x, y, width, height,
                Component.translatable("screen.codpattern.backpack.add_new"),
                button -> {
                    ModNetworkChannel.sendToServer(new AddBackpackPacket());
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> {
                        if (mc.player != null) {
                            mc.player.playNotifySound(
                                    SoundEvents.BAMBOO_WOOD_BUTTON_CLICK_ON,
                                    SoundSource.PLAYERS, 0.5f, 1.2f);
                        }
                    });
                },
                DEFAULT_NARRATION);

        this.currentBackpackCount = currentCount;

        if (currentCount >= 10) {
            this.setTooltip(Tooltip.create(Component.translatable("screen.codpattern.backpack.limit_reached")));
            this.active = false;
        } else {
            this.setTooltip(
                    Tooltip.create(Component.translatable("screen.codpattern.backpack.add_hint", currentCount)));
        }
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float pPartialTick) {
        // 悬停音效处理
        if (this.isHoveredOrFocused() && focusedTimes == 0 && this.active) {
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.playNotifySound(
                            SoundEvents.BAMBOO_WOOD_BUTTON_CLICK_ON,
                            SoundSource.PLAYERS, 0.5f, 1.2f);
                }
            });
            focusedTimes = 1;
        } else if (!this.isHoveredOrFocused()) {
            focusedTimes = 0;
        }

        renderCardShadow(graphics);

        // 根据是否可用渲染不同颜色
        if (this.active) {
            // 可用状态 - cod2022绿色调
            graphics.fillGradient(this.getX(), this.getY(),
                    this.getX() + this.width, this.getY() + this.height,
                    ACTIVE_BG_TOP, ACTIVE_BG_BOTTOM);
        } else {
            // 禁用状态 - 灰色
            graphics.fillGradient(this.getX(), this.getY(),
                    this.getX() + this.width, this.getY() + this.height,
                    CodTheme.DISABLED_BG, 0xE0252525);
        }
        renderCardFrame(graphics);

        // 渲染左侧阴影
        graphics.fill(this.getX() - 3, this.getY(),
                this.getX(), this.getY() + this.height,
                CodTheme.SHADOW);
        // 渲染底部阴影
        graphics.fillGradient(this.getX(), this.getY() + this.height,
                this.getX() + this.width, this.getY() + this.height + 2,
                CodTheme.SHADOW, 0x40000000);

        // 悬停效果
        if (isHoveredOrFocused() && this.active) {
            renderOnHoveredOrFocused(graphics);
        }

        // 渲染按钮文字
        renderButtonText(graphics);
    }

    private void renderCardShadow(GuiGraphics graphics) {
        graphics.fill(this.getX() + 2, this.getY() + 2,
                this.getX() + this.width + 2, this.getY() + this.height + 2,
                0x38000000);
    }

    private void renderCardFrame(GuiGraphics graphics) {
        graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1, 0x14FFFFFF);
        graphics.fill(this.getX(), this.getY(), this.getX() + 1, this.getY() + this.height, 0x10FFFFFF);
        graphics.fill(this.getX() + 4, this.getY() + 4,
                this.getX() + Math.max(5, this.width / 4), this.getY() + 5,
                this.active ? CodTheme.HOVER_BORDER_SEMI : 0x20FFFFFF);
    }

    protected void renderButtonText(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        String text = this.active ? Component.translatable("screen.codpattern.backpack.add_new").getString()
                : Component.translatable("screen.codpattern.backpack.limit_reached").getString();
        int textColor = this.active ? (this.isHoveredOrFocused() ? CodTheme.HOVER_BORDER : CodTheme.TEXT_PRIMARY)
                : CodTheme.DISABLED_TEXT;

        String countText = "已用 " + currentBackpackCount + "/10";
        boolean showCount = this.height >= minecraft.font.lineHeight * 2 + 8;
        int totalTextHeight = showCount ? minecraft.font.lineHeight * 2 + 2 : minecraft.font.lineHeight;
        int baseY = this.getY() + (this.height - totalTextHeight) / 2;
        int textCenterX = this.getX() + this.width / 2;
        int textMaxWidth = this.width - 12;

        GuiTextHelper.drawCenteredEllipsizedString(
                graphics,
                minecraft.font,
                text,
                textCenterX,
                baseY,
                textMaxWidth,
                textColor,
                true
        );

        if (showCount) {
            GuiTextHelper.drawCenteredEllipsizedString(
                    graphics,
                    minecraft.font,
                    countText,
                    textCenterX,
                    baseY + minecraft.font.lineHeight + 2,
                    textMaxWidth,
                    CodTheme.TEXT_SECONDARY,
                    true
            );
        }
    }

    protected void renderOnHoveredOrFocused(GuiGraphics graphics) {
        // 悬停背景
        graphics.fillGradient(this.getX(), this.getY(),
                this.getX() + this.width, this.getY() + this.height,
                ACTIVE_HOVER_TOP, ACTIVE_HOVER_BOTTOM);

        // 顶部荧光绿边框
        graphics.fill(this.getX(), this.getY() - 1,
                this.getX() + this.width, this.getY(),
                CodTheme.HOVER_BORDER);

        // 底部荧光绿边框
        graphics.fill(this.getX(), this.getY() + this.height,
                this.getX() + this.width, this.getY() + this.height + 1,
                CodTheme.HOVER_BORDER_SEMI);
    }

    @Override
    public void playDownSound(@NotNull SoundManager pHandler) {
        if (this.active) {
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.playNotifySound(
                            SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.5f, 1.5f);
                }
            });
        }
    }
}
