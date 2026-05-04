package com.cdp.codpattern.event;

import com.cdp.codpattern.CodPattern;
import com.cdp.codpattern.app.zombies.service.ZombiesBarrierMovementService;
import com.cdp.codpattern.compat.fpsmatch.map.ZombiesMap;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CodPattern.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ZombiesBarrierMovementEventHandler {
    private static final ZombiesBarrierMovementService MOVEMENT_SERVICE = ZombiesBarrierMovementService.instance();

    private ZombiesBarrierMovementEventHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || event.player.level().isClientSide
                || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (!FPSMCore.initialized()) {
            MOVEMENT_SERVICE.clear(player.getUUID());
            return;
        }

        FPSMCore.getInstance()
                .getMapByPlayerWithSpec(player)
                .filter(ZombiesMap.class::isInstance)
                .map(ZombiesMap.class::cast)
                .ifPresentOrElse(
                        zombiesMap -> MOVEMENT_SERVICE.enforce(
                                player,
                                zombiesMap.currentPhase(),
                                zombiesMap.runtimeBarrierSnapshot(),
                                zombiesMap::isRuntimeBarrierCleared,
                                zombiesMap::isAliveSurvivor),
                        () -> MOVEMENT_SERVICE.clear(player.getUUID()));
    }
}
