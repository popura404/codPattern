package com.cdp.codpattern.client.gui.refit;

import com.cdp.codpattern.config.server.BagSelectionConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public class FlatColorButton extends Button {

    private int focusedtime = 0;
    private boolean isPhotoButton = false;
    private ResourceLocation resourceLocation;
    private Integer BAGSERIAL;
    private BagSelectionConfig.Backpack backpack;
    private int UNIT_LENGTH;

    // 普通按钮构造函数
    public FlatColorButton(int x, int y, int width, int height, @Nullable Button.OnPress pOnPress) {
        super(x, y, width, height, Component.literal("choose your bag"), pOnPress, DEFAULT_NARRATION);
    }

    // 带贴图的按钮构造函数
    public FlatColorButton(int pX, int pY, int pWidth, int pHeight, Integer bagserial,
                           BagSelectionConfig.Backpack backpack, ResourceLocation resourceLocation,
                           int UNIT_LENGTH, @Nullable Button.OnPress onPress){
        super(pX, pY, pWidth, pHeight, Component.literal("choose ur weapon"),
                onPress != null ? onPress : button -> {}, DEFAULT_NARRATION);
        this.isPhotoButton = true;
        this.resourceLocation = resourceLocation;
        this.BAGSERIAL = bagserial;
        this.backpack = backpack;
        this.UNIT_LENGTH = UNIT_LENGTH;
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float pPartialTick) {
        // 播放悬停音效
        if(this.isHoveredOrFocused() && focusedtime == 0){
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.playNotifySound(
                            SoundEvents.BAMBOO_WOOD_BUTTON_CLICK_ON,
                            SoundSource.PLAYERS, 1f, 1f
                    );
                }
            });
            focusedtime = 1;
        } else if (!this.isHoveredOrFocused()) {
            focusedtime = 0;
        }

        // 渲染按钮背景
        graphics.fillGradient(
                this.getX(), this.getY(),
                this.getX() + this.width, this.getY() + this.height,
                0xDA5C565C, 0xED292729
        );

        // 渲染边框阴影
        graphics.fillGradient(
                this.getX(), this.getY(),
                this.getX() - 6, this.getY() + this.height,
                0xC019181A, 0xC019181A
        );
        graphics.fillGradient(
                this.getX(), this.getY() + this.height,
                this.getX() + this.width, this.getY() + this.height + 2,
                0xC019181A, 0x7019181A
        );

        // 悬停效果
        if (isHoveredOrFocused()) {
            renderOnHoveredOrFocused(graphics);
        }

        // 渲染贴图（支持悬停高亮）
        if(isPhotoButton && resourceLocation != null) {
            renderTexture(graphics, isHoveredOrFocused());
        }
    }

    protected void renderOnHoveredOrFocused(GuiGraphics graphics){
        // 悬停时的背景效果
        graphics.fillGradient(
                this.getX(), this.getY(),
                this.getX() + this.width, this.getY() + this.height,
                0xD0141A14, 0xD02A2F2A
        );

        // 顶部和底部高亮边框
        graphics.fillGradient(
                this.getX(), this.getY(),
                this.getX() + this.width, this.getY() + 1,
                0xD0145200, 0xD0145200
        );
        graphics.fillGradient(
                this.getX(), this.getY() + this.height - 1,
                this.getX() + this.width, this.getY() + this.height + this.height/8,
                0xD0145200, 0xD0145200
        );
    }

    protected void renderTexture(GuiGraphics graphics, boolean isHovered){
        if (resourceLocation == null) return;

        // 计算贴图居中位置
        int textureRenderWidth = 18 * UNIT_LENGTH;  // 按钮宽度减去边距
        int textureRenderHeight = 6 * UNIT_LENGTH; // 按钮高度减去边距

        // 居中贴图
        int textureX = this.getX() + 3 * UNIT_LENGTH;
        int textureY = this.getY() + 3 * UNIT_LENGTH;

        // 设置颜色和透明度（悬停时高亮）
        if (isHovered) {
            // 悬停时：更亮的效果
            RenderSystem.setShaderColor(1.25f, 1.25f, 1.25f, 1.0f);
        } else {
            // 正常状态
            RenderSystem.setShaderColor(0.85f, 0.85f, 0.85f, 0.9f);
        }

        // 渲染贴图
        graphics.blit(
                resourceLocation,
                textureX, textureY,                    // 屏幕位置
                0, 0,                                   // UV起始坐标
                textureRenderWidth, textureRenderHeight, // 渲染尺寸
                textureRenderWidth, textureRenderHeight  // 纹理文件实际尺寸
        );

        // 重置颜色
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        // 悬停时添加发光边框效果
        if (isHovered) {
            graphics.fillGradient(
                    textureX - 8, textureY - 5,
                    textureX + textureRenderWidth + 8, textureY - 4,
                    0x80FFFF00, 0x40FFFF00
            );
            graphics.fillGradient(
                    textureX - 8, textureY + textureRenderHeight - 5,
                    textureX + textureRenderWidth + 8, textureY + textureRenderHeight + 5,
                    0x40FFFF00, 0x80FFFF00
            );
            graphics.fillGradient(
                    textureX - 8, textureY - 5,
                    textureX - 7, textureY + textureRenderHeight + 5,
                    0x80FFFF00, 0x40FFFF00
            );
            graphics.fillGradient(
                    textureX + textureRenderWidth + 7, textureY - 5,
                    textureX + textureRenderWidth + 8, textureY + textureRenderHeight + 5,
                    0x40FFFF00, 0x80FFFF00
            );
        }
    }

    @Override
    public void playDownSound(@NotNull SoundManager pHandler) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.playNotifySound(
                        SoundEvents.AMETHYST_BLOCK_PLACE,
                        SoundSource.PLAYERS, 0.5f, 1f
                );
            } else {
                super.playDownSound(pHandler);
            }
        });
    }

    // Getters
    public ResourceLocation getResourceLocation() {
        return resourceLocation;
    }

    public Integer getBAGSERIAL() {
        return BAGSERIAL;
    }

    public BagSelectionConfig.Backpack getBackpack() {
        return backpack;
    }
}
