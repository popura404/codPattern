package com.cdp.codpattern.event;

import com.cdp.codpattern.app.backpack.service.BackpackDistributor;
import com.cdp.codpattern.app.tdm.service.MatchEndTeleportRespawnService;
import com.cdp.codpattern.compat.fpsmatch.FpsMatchGatewayProvider;
import com.cdp.codpattern.app.match.port.ModeRoomReadPort;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

@Mod.EventBusSubscriber(modid = "codpattern")
public class PlayerRespawnHandler {

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!event.getEntity().level().isClientSide) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            Optional<ModeRoomReadPort> roomReadPort = FpsMatchGatewayProvider.gateway().findPlayerRoomReadPort(player);
            boolean joinedRoomPlayer = roomReadPort
                    .map(port -> port.containsJoinedPlayer(player.getUUID()))
                    .orElse(false);
            if (joinedRoomPlayer) {
                BackpackDistributor.distributeBackpackItems(player);
                return;
            }

            boolean roomParticipant = roomReadPort
                    .map(port -> port.containsSpectator(player))
                    .orElse(false);
            if (!roomParticipant && !event.isEndConquered()) {
                MatchEndTeleportRespawnService.teleportToRandomMatchEndPoint(player);
            }
        }
    }
}
