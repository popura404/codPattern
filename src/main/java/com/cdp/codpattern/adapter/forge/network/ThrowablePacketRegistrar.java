package com.cdp.codpattern.adapter.forge.network;

import com.cdp.codpattern.network.StartThrowableUsePacket;
import com.cdp.codpattern.network.StopThrowableUsePacket;
import com.cdp.codpattern.network.SyncThrowableInventoryPacket;
import net.minecraftforge.network.NetworkDirection;

final class ThrowablePacketRegistrar {
    private ThrowablePacketRegistrar() {
    }

    static void register() {
        ModNetworkChannel.CHANNEL
                .messageBuilder(StartThrowableUsePacket.class, ModNetworkChannel.nextMessageId(),
                        NetworkDirection.PLAY_TO_SERVER)
                .decoder(StartThrowableUsePacket::decode)
                .encoder(StartThrowableUsePacket::encode)
                .consumerMainThread(StartThrowableUsePacket::handle)
                .add();

        ModNetworkChannel.CHANNEL
                .messageBuilder(StopThrowableUsePacket.class, ModNetworkChannel.nextMessageId(),
                        NetworkDirection.PLAY_TO_SERVER)
                .decoder(StopThrowableUsePacket::decode)
                .encoder(StopThrowableUsePacket::encode)
                .consumerMainThread(StopThrowableUsePacket::handle)
                .add();

        ModNetworkChannel.CHANNEL
                .messageBuilder(SyncThrowableInventoryPacket.class, ModNetworkChannel.nextMessageId(),
                        NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncThrowableInventoryPacket::decode)
                .encoder(SyncThrowableInventoryPacket::encode)
                .consumerMainThread(SyncThrowableInventoryPacket::handle)
                .add();
    }
}
