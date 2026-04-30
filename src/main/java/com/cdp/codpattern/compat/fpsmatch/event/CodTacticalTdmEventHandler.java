package com.cdp.codpattern.compat.fpsmatch.event;

import com.cdp.codpattern.app.match.GameModeBootstrap;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMapEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "codpattern", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CodTacticalTdmEventHandler {
    @SubscribeEvent
    public static void onRegisterFPSMap(RegisterFPSMapEvent event) {
        GameModeBootstrap.registerCommonProviders(event);
    }
}
