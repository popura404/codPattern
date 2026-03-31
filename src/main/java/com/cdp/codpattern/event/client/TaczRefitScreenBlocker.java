package com.cdp.codpattern.event.client;

import com.cdp.codpattern.CodPattern;
import com.cdp.codpattern.client.gui.screen.BackpackMenuScreen;
import com.cdp.codpattern.compat.tacz.client.CodGunRefitScreen;
import com.tacz.guns.client.gui.GunRefitScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CodPattern.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TaczRefitScreenBlocker {
    private TaczRefitScreenBlocker() {
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof GunRefitScreen)
                || event.getNewScreen() instanceof CodGunRefitScreen
                || event.getNewScreen().getClass() != GunRefitScreen.class) {
            return;
        }

        event.setNewScreen(new BackpackMenuScreen());
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(
                    Component.translatable("message.codpattern.refit.tacz_screen_disabled"));
        }
    }
}
