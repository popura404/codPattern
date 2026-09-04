package com.cdp.codpattern.app.match;

import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMapEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The single FPSMatch game-type registration bridge for every installed mode. */
@Mod.EventBusSubscriber(modid = "codpattern", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GameModeBootstrap {
    private GameModeBootstrap() {
    }

    @SubscribeEvent
    public static void onRegisterFPSMap(RegisterFPSMapEvent event) {
        ModeModules.catalog().runtimeProviders().forEach(provider ->
                event.registerGameType(provider.gameType(), provider::createMap));
    }
}
