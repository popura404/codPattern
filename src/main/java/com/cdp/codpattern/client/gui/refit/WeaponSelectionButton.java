package com.cdp.codpattern.client.gui.refit;

import com.cdp.codpattern.compat.lrtactical.LrTacticalClientApi;
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

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            // 近战武器等无 HUD 贴图的物品 - 放大渲染以匹配枪械贴图的视觉大小
            float scale = 3f;
            int itemSize = (int) (16 * scale);
            int x = this.getX() + (this.width - itemSize) / 2;
            int y = this.getY() + (this.height / 4);
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.renderItem(weapon, 0, 0);
            graphics.pose().popPose();
        }

        if (isHoveredOrFocused() && Minecraft.getInstance().screen != null) {
            graphics.renderTooltip(Minecraft.getInstance().font, weapon.getHoverName(), mouseX, mouseY);
        }

        //显示枪名
        graphics.drawString(Minecraft.getInstance().font, weapon.getHoverName(), this.getX() + 2 , this.getY() + this.height - UNIT_LENGTH ,0xDDFFFFFF);

        //显示枪包名 / LR Tactical 包名
        Component packName = null;
        if (weapon.getItem() instanceof IGun iGun) {
            ResourceLocation gunId = iGun.getGunId(weapon);
            if (gunId != null) {
                PackInfo packInfoObject = ClientAssetsManager.INSTANCE.getPackInfo(gunId);
                if (packInfoObject != null && packInfoObject.getName() != null) {
                    packName = Component.translatable(packInfoObject.getName()).withStyle(ChatFormatting.BLUE).withStyle(ChatFormatting.ITALIC);
                }
            }
        } else {
            packName = LrTacticalClientApi.getLrItemPackName(weapon);
        }
        if (packName != null) {
            graphics.drawString(Minecraft.getInstance().font, packName, this.getX() + 2, this.getY() + 2, 0xDDFFFFFF);
        }
    }
}
