package com.cdp.codpattern.event;

import com.cdp.codpattern.app.backpack.service.BackpackDistributor;
import com.cdp.codpattern.compat.fpsmatch.FpsMatchGatewayProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "codpattern")
public class PlayerRespawnHandler {

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!event.getEntity().level().isClientSide) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            boolean joinedRoomPlayer = FpsMatchGatewayProvider.gateway()
                    .findPlayerRoomReadPort(player)
                    .map(port -> port.containsJoinedPlayer(player.getUUID()))
                    .orElse(false);
            if (!joinedRoomPlayer) {
                return;
            }
            BackpackDistributor.distributeBackpackItems(player);
        }
    }
}
