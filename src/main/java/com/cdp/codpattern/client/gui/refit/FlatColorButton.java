package com.cdp.codpattern.client.gui.refit;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.PackInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public class FlatColorButton extends Button {

    private int focusedtime = 0;
    private boolean isPhotoButton = false;
    private Integer BAGSERIAL;
    private int UNIT_LENGTH;

    private ItemStack weapon;
    private Component weaponName;
    private Component weaponPackinfo;
    private ResourceLocation Teaxture;


    // 普通按钮
    public FlatColorButton(int x, int y, int width, int height, @Nullable Button.OnPress pOnPress) {
        super(x, y, width, height, Component.literal("choose your bag"), pOnPress, DEFAULT_NARRATION);
    }

    // 带贴图的按钮
    public FlatColorButton(int pX, int pY, int pWidth, int pHeight, Integer bagserial,
                           ItemStack itemStack,
                           int UNIT_LENGTH, @Nullable Button.OnPress onPress){
        super(pX, pY, pWidth, pHeight, Component.literal("choose ur weapon"), onPress != null ? onPress : button -> {}, DEFAULT_NARRATION);
        this.isPhotoButton = true;
        this.BAGSERIAL = bagserial;
        this.UNIT_LENGTH = UNIT_LENGTH;
        this.weapon = itemStack;
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float pPartialTick) {
        getInfo();
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
        graphics.fillGradient(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xDA5C565C, 0xED292729);

        // 渲染边框阴影
        graphics.fillGradient(this.getX(), this.getY(), this.getX() - 6, this.getY() + this.height, 0xC019181A, 0xC019181A);
        graphics.fillGradient(this.getX(), this.getY() + this.height, this.getX() + this.width, this.getY() + this.height + 2, 0xC019181A, 0x7019181A);

        // 悬停效果
        if (isHoveredOrFocused()) {
            renderOnHoveredOrFocused(graphics);
        }

        //渲染包名（如果有）
        if (!(weaponPackinfo == null)){
            graphics.drawString(Minecraft.getInstance().font , this.weaponPackinfo , this.getX() + 2 , this.getY() + 2 ,0xDDFFFFFF);
        }
        //渲染枪名（如果有）
        if (!(weaponName == null)){
            graphics.drawString(Minecraft.getInstance().font, this.weaponName, this.getX() + UNIT_LENGTH , this.getY() + this.height - UNIT_LENGTH ,0xDDFFFFFF);
        }

        // 渲染贴图（支持悬停高亮）
        if(isPhotoButton && Teaxture != null) {
            renderTexture(graphics, isHoveredOrFocused());
        }
    }

    protected void renderOnHoveredOrFocused(GuiGraphics graphics){
        // 悬停时的背景效果
        graphics.fillGradient(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xD0141A14, 0xD02A2F2A);

        // 顶部和底部高亮边框
        graphics.fillGradient(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1, 0xD0145200, 0xD0145200);
        graphics.fillGradient(this.getX(), this.getY() + this.height - 1, this.getX() + this.width, this.getY() + this.height + this.height/8, 0xD0145200, 0xD0145200);
    }

    protected void renderTexture(GuiGraphics graphics, boolean isHovered){
        if (Teaxture == null) return;

        // 计算贴图daxiao
        int textureRenderWidth = 18 * UNIT_LENGTH;
        int textureRenderHeight = 6 * UNIT_LENGTH;

        // 计算贴图位置
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
        graphics.blit(Teaxture, textureX, textureY,0 , 0 , textureRenderWidth, textureRenderHeight, textureRenderWidth, textureRenderHeight);

        // 重置颜色
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        // 悬停时添加发光边框效果
        if (isHovered) {
            graphics.fillGradient(textureX - 8, textureY - 5, textureX + textureRenderWidth + 8, textureY - 4, 0x80FFFF00, 0x40FFFF00);
            graphics.fillGradient(textureX - 8, textureY + textureRenderHeight - 5, textureX + textureRenderWidth + 8, textureY + textureRenderHeight + 5, 0x40FFFF00, 0x80FFFF00);
            graphics.fillGradient(textureX - 8, textureY - 5, textureX - 7, textureY + textureRenderHeight + 5, 0x80FFFF00, 0x40FFFF00);
            graphics.fillGradient(textureX + textureRenderWidth + 7, textureY - 5, textureX + textureRenderWidth + 8, textureY + textureRenderHeight + 5, 0x40FFFF00, 0x80FFFF00);
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

    private void getInfo(){
        if(weapon == null){
            return;
        }
        //图片处理
        TimelessAPI.getGunDisplay(weapon).ifPresent(display -> this.Teaxture = display.getHUDTexture());
        //包名处理
        IGun iGun = (IGun) weapon.getItem();
        ResourceLocation gunId = iGun.getGunId(weapon);
        PackInfo packInfoObject = ClientAssetsManager.INSTANCE.getPackInfo(gunId);
        this.weaponPackinfo = Component.translatable(packInfoObject.getName()).withStyle(ChatFormatting.BLACK).withStyle(ChatFormatting.ITALIC);
        //枪名处理
        this.weaponName = weapon.getHoverName();
    }

    // Getters
    public ResourceLocation getResourceLocation() {
        return Teaxture;
    }

    public Integer getBAGSERIAL() {
        return BAGSERIAL;
    }
}
