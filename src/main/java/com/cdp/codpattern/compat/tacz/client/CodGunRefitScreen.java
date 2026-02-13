package com.cdp.codpattern.compat.tacz.client;

import com.cdp.codpattern.client.gui.CodTheme;
import com.tacz.guns.client.gui.GunRefitScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

@OnlyIn(Dist.CLIENT)
public class CodGunRefitScreen extends GunRefitScreen {

    private final Screen parentScreen;

    public CodGunRefitScreen() {
        this(null);
    }

    public CodGunRefitScreen(Screen parentScreen) {
        super();
        this.parentScreen = parentScreen;
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics graphics) {
        graphics.fillGradient(0, 0, this.width, this.height, CodTheme.BG_TOP, CodTheme.BG_BOTTOM);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            if (parentScreen != null) {
                Minecraft.getInstance().setScreen(parentScreen);
            } else {
                Minecraft.getInstance().setScreen(null);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
