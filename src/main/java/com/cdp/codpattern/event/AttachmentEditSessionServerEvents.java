package com.cdp.codpattern.event;

import com.cdp.codpattern.CodPatternConstants;
import com.cdp.codpattern.core.refit.AttachmentEditSessionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.server.ServerStoppingEvent;

@Mod.EventBusSubscriber(modid = CodPatternConstants.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AttachmentEditSessionServerEvents {

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        AttachmentEditSessionManager.abortAllSessions(event.getServer());
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
