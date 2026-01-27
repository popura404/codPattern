package com.cdp.codpattern.client.gui.refit;

import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.network.handler.PacketHandler;
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
                Component.literal("+ 添加背包"),
                button -> {
                    PacketHandler.sendToServer(new AddBackpackPacket());
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> {
                        if (mc.player != null) {
                            mc.player.playNotifySound(
                                    SoundEvents.BAMBOO_WOOD_BUTTON_CLICK_ON,
                                    SoundSource.PLAYERS, 0.5f, 1.2f
                            );
                        }
                    });
                },
                DEFAULT_NARRATION);

        this.currentBackpackCount = currentCount;

        if (currentCount >= 10) {
            this.setTooltip(Tooltip.create(Component.literal("§c已达到最大背包数量限制 (10个)")));
            this.active = false;
        } else {
            this.setTooltip(Tooltip.create(Component.literal("§a点击添加新背包 §7(" + currentCount + "/10)")));
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

    protected void renderButtonText(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        String text = this.active ? "+ 添加新背包" : "已达上限";
        int textColor = this.active ?
                (this.isHoveredOrFocused() ? CodTheme.HOVER_BORDER : CodTheme.TEXT_PRIMARY) :
                CodTheme.DISABLED_TEXT;

        int textWidth = minecraft.font.width(text);
        int textX = this.getX() + (this.width - textWidth) / 2;
        int textY = this.getY() + (this.height - minecraft.font.lineHeight) / 2;

        graphics.drawString(minecraft.font, text, textX, textY, textColor, true);

        // 显示当前背包数量
        String countText = "(" + currentBackpackCount + "/10)";
        int countWidth = minecraft.font.width(countText);
        graphics.drawString(minecraft.font, countText,
                this.getX() + (this.width - countWidth) / 2,
                this.getY() + this.height - minecraft.font.lineHeight - 2,
                CodTheme.TEXT_SECONDARY, true);
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
