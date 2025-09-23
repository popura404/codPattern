package com.cdp.codpattern.client.gui.screen;

import com.cdp.codpattern.client.gui.refit.FlatColorButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * 已废弃
 */
@Deprecated
public class MainMenuScreen extends Screen {

    private static int UNIT_LENGTH = 0;

    public MainMenuScreen(){super(Component.literal("Select you bag"));}

    private void addMainMenuBagButton(){
        //int scale = (int) Minecraft.getInstance().getWindow().getGuiScale();
        addRenderableWidget(new FlatColorButton(6 * UNIT_LENGTH , this.height - 18 * UNIT_LENGTH ,  26 * UNIT_LENGTH , 13 * UNIT_LENGTH , button -> openSelectBagMenuScreen()));
    }

    @Override
    public void render(@NotNull GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        this.renderBackground(pGuiGraphics);
        super.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics pGuiGraphics) {
        pGuiGraphics.fillGradient(
                0, 0,                  // 左上角坐标
                this.width, this.height,  // 右下角坐标（覆盖整个屏幕）
                0x90202020,  // 顶部颜色
                0xC0000000   // 底部颜色
        );
    }
    public void init(){
        super.init();
        int SCREEN_WIDTH = this.width;
        UNIT_LENGTH = SCREEN_WIDTH / 120;
        addMainMenuBagButton();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public void openSelectBagMenuScreen() {
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().setScreen(null);
            Minecraft.getInstance().setScreen(new BackpackMenuScreen());
        });
    }
}

