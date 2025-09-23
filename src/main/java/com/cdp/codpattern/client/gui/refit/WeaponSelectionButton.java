package com.cdp.codpattern.client.gui.refit;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.PackInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class WeaponSelectionButton extends Button {

    private final ItemStack weapon;
    private final ResourceLocation texture;
    private final int UNIT_LENGTH;

    public WeaponSelectionButton(int x, int y, int width, int height, ItemStack weapon, ResourceLocation texture, OnPress onPress, int UNIT_LENGTH) {
        super(x, y, width, height, weapon.getHoverName(), onPress, DEFAULT_NARRATION);
        this.weapon = weapon;
        this.texture = texture;
        this.UNIT_LENGTH = UNIT_LENGTH;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xDA5C565C, 0xED292729);

        graphics.fillGradient(this.getX(), this.getY(), this.getX() - 3, this.getY() + this.height, 0xC019181A, 0xC019181A);

        if (isHoveredOrFocused()) {
            graphics.fillGradient(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xD0141A14, 0xD02A2F2A);

            graphics.fillGradient(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1, 0xD0145200, 0xD0145200);
        }

        //显示枪包名  <  关键代码》》Component.translatable(packInfoObject.getName()).withStyle(ChatFormatting.BLUE).withStyle(ChatFormatting.ITALIC);  >
        IGun iGun = (IGun) weapon.getItem();
        ResourceLocation gunId = iGun.getGunId(weapon);
        PackInfo packInfoObject = ClientAssetsManager.INSTANCE.getPackInfo(gunId);
        graphics.drawString(Minecraft.getInstance().font , Component.translatable(packInfoObject.getName()).withStyle(ChatFormatting.BLACK).withStyle(ChatFormatting.ITALIC) , this.getX() + 2 , this.getY() + 2 ,0xDDFFFFFF);

        if (texture != null) {
            RenderSystem.setShaderColor(
                    isHoveredOrFocused() ? 1.25f : 0.85f,
                    isHoveredOrFocused() ? 1.25f : 0.85f,
                    isHoveredOrFocused() ? 1.25f : 0.85f,
                    1.0f
            );

            int textureWidth = this.width / 4 * 3;
            int textureHeight = textureWidth / 3;
            int textureX = this.getX() + (this.width / 8);
            int textureY = this.getY() + (this.height / 4);
            //hud texture渲染
            graphics.blit(
                    texture,
                    textureX, textureY,
                    0, 0,
                    textureWidth, textureHeight,
                    textureWidth, textureHeight
            );

            //显示枪命
            graphics.drawString(Minecraft.getInstance().font, weapon.getHoverName(), this.getX() + 2 , this.getY() + this.height - UNIT_LENGTH ,0xDDFFFFFF);

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            graphics.renderItem(weapon, this.getX() + this.width / 2 - 8, this.getY() + this.height / 2 - 8);
        }

        if (isHoveredOrFocused() && Minecraft.getInstance().screen != null) {
            graphics.renderTooltip(Minecraft.getInstance().font, weapon.getHoverName(), mouseX, mouseY);
        }
    }
}
