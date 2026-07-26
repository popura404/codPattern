package com.cdp.codpattern.event;

import com.cdp.codpattern.CodPatternConstants;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CodPatternConstants.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ZombiesBarrierMovementEventHandler {
    private ZombiesBarrierMovementEventHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // Runtime zombies barriers now use temporary player-only barrier blocks for normal collision.
        // The old pullback service remains as a compatibility-tested fallback utility, but is not wired per tick.
    }
}
