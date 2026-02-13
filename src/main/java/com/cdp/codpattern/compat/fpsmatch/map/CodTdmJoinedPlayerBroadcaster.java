package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.network.tdm.DeathCamPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.function.Supplier;

final class CodTdmJoinedPlayerBroadcaster {
    private final Supplier<List<ServerPlayer>> joinedPlayersSupplier;

    CodTdmJoinedPlayerBroadcaster(Supplier<List<ServerPlayer>> joinedPlayersSupplier) {
        this.joinedPlayersSupplier = joinedPlayersSupplier;
    }

    void broadcastToJoinedPlayers(Component message) {
        joinedPlayersSupplier.get().forEach(player -> player.sendSystemMessage(message));
    }

    <T> void broadcastPacketToJoinedPlayers(T packet) {
        joinedPlayersSupplier.get().forEach(player -> ModNetworkChannel.sendToPlayer(packet, player));
    }

    void clearDeathHudForAllPlayers() {
        broadcastPacketToJoinedPlayers(DeathCamPacket.clear());
    }
}
