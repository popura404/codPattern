package com.cdp.codpattern.event;

import com.cdp.codpattern.CodPattern;
import com.cdp.codpattern.core.refit.AttachmentEditSessionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod.EventBusSubscriber(modid = CodPattern.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AttachmentEditSessionServerEvents {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ServerLifecycleHooks.getCurrentServer() == null) {
            return;
        }
        AttachmentEditSessionManager.tickTimeouts(ServerLifecycleHooks.getCurrentServer());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getEntity().level().isClientSide) {
            return;
        }
        AttachmentEditSessionManager.abortSession(player, "logout", false);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getEntity().level().isClientSide) {
            return;
        }
        AttachmentEditSessionManager.abortSession(player, "dimension_changed");
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getEntity().level().isClientSide) {
            return;
        }
        AttachmentEditSessionManager.abortSession(player, "death");
    }
}
