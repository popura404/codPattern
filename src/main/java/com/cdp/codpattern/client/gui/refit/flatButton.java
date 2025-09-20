package com.cdp.codpattern.client.gui.refit;

import com.cdp.codpattern.client.gui.screen.WeaponMenuScreen;
import com.cdp.codpattern.config.server.BagSelectConfig;
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

/**
*  屎山有点多，这个被废弃了，重写一个
*/

public class flatButton extends Button {

    private int focusedtime = 0;
    private boolean isPhotoButton = false;
    private ResourceLocation resourceLocation;
    private Integer BAGSERIAL;
    private BagSelectConfig.Backpack backpack;
    private int UNIT_LENGTH;

    public flatButton(int x , int y , int width , int height , @Nullable Button.OnPress pOnPress) {
        super(x, y, width, height, Component.literal("choose your bag"), pOnPress, DEFAULT_NARRATION);
    }
    //TODO:修改构造函数
    public flatButton(int pX, int pY, int pWidth, int pHeight, Integer bagserial, BagSelectConfig.Backpack backpack , ResourceLocation resourceLocation , int UNIT_LENGTH){
        super(pX, pY, pWidth, pHeight, Component.literal("choose ur weapon"), button -> {}, DEFAULT_NARRATION);
        this.isPhotoButton = true;
        this.resourceLocation = resourceLocation;
        this.BAGSERIAL = bagserial;
        this.backpack = backpack;
        this.UNIT_LENGTH = UNIT_LENGTH;
    }

    public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float pPartialTick) {
        if(this.isHoveredOrFocused() && focusedtime == 0){
            Minecraft.getInstance().execute(() ->{
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.playNotifySound(SoundEvents.BAMBOO_WOOD_BUTTON_CLICK_ON , SoundSource.PLAYERS , 1f , 1f);
                }
            });
            focusedtime = 1;
        } else if (!this.isHoveredOrFocused()) {
            focusedtime = 0;
        }

        //Minecraft minecraft = Minecraft.getInstance();
        //Font font = minecraft.font;
        graphics.fillGradient(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height,0xDA5C565C,0xCF524D52 );

        graphics.fillGradient(this.getX(), this.getY(), this.getX() - 6, this.getY() + this.height, 0xC019181A, 0xC019181A);
        graphics.fillGradient(this.getX(), this.getY() + this.height, this.getX() + this.width, this.getY() + this.height + 2, 0xC019181A, 0x7019181A);
        if (isHoveredOrFocused()) {
            renderOnHoveredOrFocused(graphics);
        }

        //渲染图片
        if(isPhotoButton == true) renderTexture(graphics);
    }

    protected void renderOnHoveredOrFocused(GuiGraphics graphics){
            graphics.fillGradient(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height,0xD0141A14,0xD02A2F2A);

            graphics.fillGradient(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1, 0xD0145200, 0xD0145200);
            graphics.fillGradient(this.getX(), this.getY() + this.height - 1, this.getX() + this.width, this.getY() + this.height + this.height/8, 0xD0145200, 0xD0145200);
    }

    protected void renderTexture(GuiGraphics graphics){
        graphics.blit(resourceLocation , this.getX() - UNIT_LENGTH , this.getY() - 3 * UNIT_LENGTH , 0.0F, 0.0F,24 * UNIT_LENGTH , 8 * UNIT_LENGTH , 24 * UNIT_LENGTH , 8 * UNIT_LENGTH);
    }

    @Override
    public void playDownSound(@NotNull SoundManager pHandler) {
        Minecraft.getInstance().execute(() ->{
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.playNotifySound(SoundEvents.AMETHYST_BLOCK_PLACE , SoundSource.PLAYERS , 0.5f , 1f);
            }else {
                super.playDownSound(pHandler);
            }
        });
    }

    public ResourceLocation getResourceLocation() {
        return resourceLocation;
    }
}
