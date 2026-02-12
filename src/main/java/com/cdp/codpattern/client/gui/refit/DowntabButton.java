package com.cdp.codpattern.client.gui.refit;

import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.client.gui.screen.WeaponMenuScreen;
import com.cdp.codpattern.config.backpack.BackpackConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.NotNull;

/**
 * 用于更改选中的背包配置，搭配BackPackButton - MWII 绿色主题
 */
public class DowntabButton extends Button {

    private final Integer BAGSERIAL;
    int focusedtimes = 0;
    private final BackpackConfig.Backpack backpack;

    // cod2022 绿色主题色
    private static final int GREEN_BG_TOP = 0xE8183018;
    private static final int GREEN_BG_BOTTOM = 0xF0102810;
    private static final int GREEN_HOVER_TOP = 0xE8204020;
    private static final int GREEN_HOVER_BOTTOM = 0xF0183818;

    protected DowntabButton(int pX, int pY, int pWidth, int pHeight, Component pMessage, int bagserial, BackpackConfig.Backpack backpack) {
        super(pX, pY, pWidth, pHeight, pMessage, button -> {
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new WeaponMenuScreen(backpack, bagserial)));
        }, DEFAULT_NARRATION);
        this.BAGSERIAL = bagserial;
        this.backpack = backpack;
    }

    public DowntabButton(BackPackSelectButton button){
        super(button.getX() , button.getY() + button.getHeight() , button.getWidth() , calcSubHeight(button), Component.literal("change ur backpack") ,pbutton -> {
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new WeaponMenuScreen(button.getBackpack(), button.getBAGSERIAL())));
        } , DEFAULT_NARRATION);
        this.BAGSERIAL = button.getBAGSERIAL();
        this.backpack = button.getBackpack();
    }

    private static int calcSubHeight(BackPackSelectButton button) {
        int unit = Math.max(1, button.getWidth() / 20);
        return 2 * unit + 6;
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float pPartialTick) {
        // 悬停音效处理
        if (this.isHoveredOrFocused() && focusedtimes == 0) {
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.playNotifySound(
                            SoundEvents.BAMBOO_WOOD_BUTTON_CLICK_ON,
                            SoundSource.PLAYERS, 0.5f, 1.2f);
                }
            });
            focusedtimes = 1;
        } else if (!this.isHoveredOrFocused()) {
            focusedtimes = 0;
        }

        // 基础背景 - 绿色主题
        graphics.fillGradient(this.getX(), this.getY(),
                this.getX() + this.width, this.getY() + this.height,
                GREEN_BG_TOP, GREEN_BG_BOTTOM);

        // 悬停效果
        if (isHoveredOrFocused()) {
            renderOnHoveredOrFocused(graphics);
        }
        rendertitle(graphics);
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

    protected void rendertitle(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        String idText = "配置";
        int textX = this.getX() + 6;  // 左对齐
        int textY = this.getY() + (this.height - minecraft.font.lineHeight) / 2;

        graphics.drawString(minecraft.font, idText, textX, textY,
                this.isHoveredOrFocused() ? CodTheme.HOVER_BORDER : 0xFFFFFF, true);
    }
}
