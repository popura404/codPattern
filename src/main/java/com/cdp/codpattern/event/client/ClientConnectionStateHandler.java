package com.cdp.codpattern.event.client;

import com.cdp.codpattern.CodPattern;
import com.cdp.codpattern.client.ClientTdmState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CodPattern.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientConnectionStateHandler {
    private ClientConnectionStateHandler() {
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientTdmState.resetMatchState();
    }
}
