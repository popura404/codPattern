package com.cdp.codpattern.event;

import com.cdp.codpattern.CodPattern;
import com.cdp.codpattern.app.tdm.service.RoomFoodLockService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CodPattern.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RoomFoodLockEventHandler {
    private RoomFoodLockEventHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || event.player.level().isClientSide
                || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (!RoomFoodLockService.isRoomPlayer(player)) {
            return;
        }
        RoomFoodLockService.enforce(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (RoomFoodLockService.shouldBlockHeal(player)) {
            event.setCanceled(true);
        }
    }
}
