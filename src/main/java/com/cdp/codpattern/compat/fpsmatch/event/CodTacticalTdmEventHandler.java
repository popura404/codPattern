package com.cdp.codpattern.compat.fpsmatch.event;

import com.cdp.codpattern.app.tdm.model.TdmGameTypes;
import com.cdp.codpattern.compat.fpsmatch.map.CodTacticalTdmMap;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMapEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "codpattern", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CodTacticalTdmEventHandler {
    @SubscribeEvent
    public static void onRegisterFPSMap(RegisterFPSMapEvent event) {
        event.registerGameType(TdmGameTypes.CDP_TACTICAL_TDM, CodTacticalTdmMap::new);
    }
}
