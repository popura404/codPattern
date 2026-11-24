package com.cdp.codpattern.event.client;

import com.cdp.codpattern.CodPatternConstants;
import com.tacz.guns.client.gui.GunRefitScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CodPatternConstants.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TaczRefitScreenBlocker {
    private TaczRefitScreenBlocker() {
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        var screen = event.getNewScreen();
        if (screen == null || screen.getClass() != GunRefitScreen.class) {
            return;
        }

        event.setCanceled(true);
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(
                    Component.translatable("message.codpattern.refit.tacz_screen_disabled"));
        }
    }
}
