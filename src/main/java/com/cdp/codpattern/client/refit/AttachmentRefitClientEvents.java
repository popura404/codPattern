package com.cdp.codpattern.client.refit;

import com.cdp.codpattern.CodPattern;
import com.cdp.codpattern.compat.tacz.client.CodGunRefitScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CodPattern.MODID, value = Dist.CLIENT)
public class AttachmentRefitClientEvents {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            AttachmentRefitClientState.tryOpenIfReady();
        }
    }

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof CodGunRefitScreen) {
            AttachmentRefitClientState.onRefitScreenClosed();
        }
    }
}
