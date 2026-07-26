package com.cdp.codpattern.event.client;

import com.cdp.codpattern.CodPatternConstants;
import com.cdp.codpattern.client.ClientTdmState;
import com.cdp.codpattern.client.TdmCombatMarkerTracker;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CodPatternConstants.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientTickHandler {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            ClientTdmState.enforceDeathCamViewLock();
            return;
        }
        if (event.phase == TickEvent.Phase.END) {
            ClientTdmState.clientTick();
            TdmCombatMarkerTracker.INSTANCE.clientTick();
        }
    }
}
