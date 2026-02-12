package com.cdp.codpattern.adapter.forge.network;

import com.cdp.codpattern.network.RequestAttachmentPresetPacket;
import com.cdp.codpattern.network.SaveAttachmentPresetPacket;
import com.cdp.codpattern.network.SyncAttachmentPresetPacket;
import com.cdp.codpattern.network.UpdateWeaponPacket;
import com.cdp.codpattern.network.UpdateWeaponResultPacket;
import net.minecraftforge.network.NetworkDirection;

final class RefitPacketRegistrar {
    private RefitPacketRegistrar() {
    }

    static void register() {
        ModNetworkChannel.CHANNEL.messageBuilder(UpdateWeaponPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(UpdateWeaponPacket::decode)
                .encoder(UpdateWeaponPacket::encode)
                .consumerMainThread(UpdateWeaponPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(RequestAttachmentPresetPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(RequestAttachmentPresetPacket::decode)
                .encoder(RequestAttachmentPresetPacket::encode)
                .consumerMainThread(RequestAttachmentPresetPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(SyncAttachmentPresetPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncAttachmentPresetPacket::decode)
                .encoder(SyncAttachmentPresetPacket::encode)
                .consumerMainThread(SyncAttachmentPresetPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(SaveAttachmentPresetPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(SaveAttachmentPresetPacket::decode)
                .encoder(SaveAttachmentPresetPacket::encode)
                .consumerMainThread(SaveAttachmentPresetPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(UpdateWeaponResultPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(UpdateWeaponResultPacket::decode)
                .encoder(UpdateWeaponResultPacket::encode)
                .consumerMainThread(UpdateWeaponResultPacket::handle)
                .add();
    }
}
