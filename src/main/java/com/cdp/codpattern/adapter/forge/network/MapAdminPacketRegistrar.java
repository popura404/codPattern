package com.cdp.codpattern.adapter.forge.network;

import com.cdp.codpattern.network.map.MapAdminRequestPacket;
import com.cdp.codpattern.network.map.MapAdminResponsePacket;
import net.minecraftforge.network.NetworkDirection;

final class MapAdminPacketRegistrar {
    private MapAdminPacketRegistrar() { }

    static void register() {
        ModNetworkChannel.CHANNEL.messageBuilder(MapAdminRequestPacket.class,
                        ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(MapAdminRequestPacket::decode)
                .encoder(MapAdminRequestPacket::encode)
                .consumerMainThread(MapAdminRequestPacket::handle)
                .add();
        ModNetworkChannel.CHANNEL.messageBuilder(MapAdminResponsePacket.class,
                        ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(MapAdminResponsePacket::decode)
                .encoder(MapAdminResponsePacket::encode)
                .consumerMainThread(MapAdminResponsePacket::handle)
                .add();
    }
}
