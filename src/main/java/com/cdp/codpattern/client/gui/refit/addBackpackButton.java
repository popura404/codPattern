package com.cdp.codpattern.client.gui.refit;

import com.cdp.codpattern.client.gui.screen.BackpackMenuScreen;
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

public class addBackpackButton extends Button {

    private int focusedTimes = 0;
    private final int currentBackpackCount;

    public addBackpackButton(int x, int y, int width, int height, int currentCount) {
        super(x, y, width, height,
                Component.literal("+ 添加背包"),
                button -> {
                    // 发送添加背包请求到服务端
                    PacketHandler.sendToServer(new AddBackpackPacket());
                    // 刷新界面
                    if (Minecraft.getInstance().screen != null) {
                        Minecraft.getInstance().screen.onClose();
                        Minecraft.getInstance().execute(() -> {
                            Minecraft.getInstance().setScreen(new BackpackMenuScreen());
                        });
                    }
                },
                DEFAULT_NARRATION);

        this.currentBackpackCount = currentCount;

        // 设置提示信息
        if (currentCount >= 10) {
            this.setTooltip(Tooltip.create(Component.literal("§c已达到最大背包数量限制 (10个)")));
            this.active = false; // 禁用按钮
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
                            SoundSource.PLAYERS, 1f, 1f);
                }
            });
            focusedTimes = 1;
        } else if (!this.isHoveredOrFocused()) {
            focusedTimes = 0;
        }

        // 根据是否可用渲染不同颜色
        if (this.active) {
            // 可用状态 - 绿色调
            graphics.fillGradient(this.getX(), this.getY(),
                    this.getX() + this.width, this.getY() + this.height,
                    0xDA2A5C2A, 0xCF1A491A);
        } else {
            // 禁用状态 - 灰色
            graphics.fillGradient(this.getX(), this.getY(),
                    this.getX() + this.width, this.getY() + this.height,
                    0xDA3C3C3C, 0xCF2D2D2D);
        }

        // 渲染阴影
        graphics.fillGradient(this.getX(), this.getY(),
                this.getX() - 6, this.getY() + this.height,
                0xC019181A, 0xC019181A);
        graphics.fillGradient(this.getX(), this.getY() + this.height,
                this.getX() + this.width, this.getY() + this.height + 2,
                0xC019181A, 0x7019181A);

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
                (this.isHoveredOrFocused() ? 0x7FFF00 : 0xFFFFFF) : 0x808080;

        int textWidth = minecraft.font.width(text);
        int textX = this.getX() + (this.width - textWidth) / 2;
        int textY = this.getY() + (this.height - minecraft.font.lineHeight) / 2;

        graphics.drawString(minecraft.font, text, textX, textY, textColor, true);

        // 显示当前数量
        String countText = "(" + currentBackpackCount + "/10)";
        int countWidth = minecraft.font.width(countText);
        graphics.drawString(minecraft.font, countText,
                this.getX() + (this.width - countWidth) / 2,
                this.getY() + this.height - minecraft.font.lineHeight - 2,
                0xAAAAAA, true);
    }

    protected void renderOnHoveredOrFocused(GuiGraphics graphics) {
        graphics.fillGradient(this.getX(), this.getY(),
                this.getX() + this.width, this.getY() + this.height,
                0xD0143014, 0xD02A4F2A);

        graphics.fillGradient(this.getX(), this.getY(),
                this.getX() + this.width, this.getY() + 1,
                0xD000FF00, 0xD000FF00);
        graphics.fillGradient(this.getX(), this.getY() + this.height - 1,
                this.getX() + this.width, this.getY() + this.height,
                0xD000FF00, 0xD000FF00);
    }

    @Override
    public void playDownSound(@NotNull SoundManager pHandler) {
        if (this.active) {
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.playNotifySound(
                            SoundEvents.ANVIL_USE,
                            SoundSource.PLAYERS, 0.5f, 1.5f);
                }
            });
        }
    }
}
