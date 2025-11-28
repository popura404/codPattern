package com.cdp.codpattern.event.client;

import com.cdp.codpattern.client.gui.refit.BackpackButton;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "codpattern" , value = Dist.CLIENT , bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CreateMenuButtonsHandler {

    @SubscribeEvent
    public static void addButtononPauseScreen(ScreenEvent.Init.Post event) {
        var screen = event.getScreen();
        if (!(screen instanceof PauseScreen)) return;

        // 添加背包按键
        event.addListener(BackpackButton.create(screen.width / 2 - 102 , screen.height - 24 , 204 , 16));
    }
}
