package com.cdp.codpattern.client.gui.refit;

import com.cdp.codpattern.client.gui.screen.WeaponMenuScreen;
import com.cdp.codpattern.config.server.BagSelectionConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.NotNull;

public class SecodnButton extends Button {

    private final Integer BAGSERIAL;
    int focusedtimes = 0;
    private final BagSelectionConfig.Backpack backpack;

    protected SecodnButton(int pX, int pY, int pWidth, int pHeight, Component pMessage, int bagserial, BagSelectionConfig.Backpack backpack) {
        super(pX, pY, pWidth, pHeight, pMessage, button -> {
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new WeaponMenuScreen(backpack, bagserial)));
        }, DEFAULT_NARRATION);
        this.BAGSERIAL = bagserial;
        this.backpack = backpack;
    }

    public SecodnButton(BackPackButton button){
        super(button.getX() , button.getY() + button.getHeight() , button.getWidth() , button.getHeight()/3 + 6, Component.literal("change ur backpack") ,pbutton -> {
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new WeaponMenuScreen(button.getBackpack(), button.getBAGSERIAL())));
        } , DEFAULT_NARRATION);
        this.BAGSERIAL = button.getBAGSERIAL();
        this.backpack = button.getBackpack();
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float pPartialTick) {
        // 悬停音效处理
        if (this.isHoveredOrFocused() && focusedtimes == 0) {
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.playNotifySound(
                            SoundEvents.BAMBOO_WOOD_BUTTON_CLICK_ON,
                            SoundSource.PLAYERS, 1f, 1f);
                }
            });
            focusedtimes = 1;
        } else if (!this.isHoveredOrFocused()) {
            focusedtimes = 0;
        }

        // 渲染基础按钮背景
        //graphics.fillGradient(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xDA5C565C, 0xCF524D52);

        // 渲染阴影
        //graphics.fillGradient(this.getX(), this.getY(), this.getX() - 6, this.getY() + this.height, 0xC019181A, 0xC019181A);
        //graphics.fillGradient(this.getX(), this.getY() + this.height, this.getX() + this.width, this.getY() + this.height + 2, 0xC019181A, 0x7019181A);

        // 悬停效果
        if (isHoveredOrFocused()) {
            renderOnHoveredOrFocused(graphics);
        }
        rendertitle(graphics);
    }

    protected void renderOnHoveredOrFocused(GuiGraphics graphics) {
        graphics.fillGradient(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xD0141A14, 0xD02A2F2A);
        graphics.fillGradient(this.getX(), this.getY(), this.getX() + this.width, this.getY() , 0xD0145200, 0xD0145200);
        graphics.fillGradient(this.getX(), this.getY() + this.height, this.getX() + this.width, this.getY() + this.height + 1, 0xD0145200, 0xD0145200);
    }

    protected void rendertitle(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        String idText = "® 配置";
        int textX = this.getX() + 4;
        int textY = this.getY() + (this.height - minecraft.font.lineHeight) / 2;

        graphics.drawString(minecraft.font, idText, textX, textY, this.isHoveredOrFocused() ? 0x00FF00 : 0xFFFFFF, true);
    }
}
