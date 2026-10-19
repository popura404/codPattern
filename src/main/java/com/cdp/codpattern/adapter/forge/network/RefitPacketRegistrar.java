package com.cdp.codpattern.adapter.forge.network;

import com.cdp.codpattern.network.RequestAttachmentPresetPacket;
import com.cdp.codpattern.network.RefitActionPacket;
import com.cdp.codpattern.network.RefitOperationResultPacket;
import com.cdp.codpattern.network.SyncAttachmentCandidatesPacket;
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

        ModNetworkChannel.CHANNEL.messageBuilder(SyncAttachmentCandidatesPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncAttachmentCandidatesPacket::decode)
                .encoder(SyncAttachmentCandidatesPacket::encode)
                .consumerMainThread(SyncAttachmentCandidatesPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(RefitActionPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(RefitActionPacket::decode)
                .encoder(RefitActionPacket::encode)
                .consumerMainThread(RefitActionPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(RefitOperationResultPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(RefitOperationResultPacket::decode)
                .encoder(RefitOperationResultPacket::encode)
                .consumerMainThread(RefitOperationResultPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(UpdateWeaponResultPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(UpdateWeaponResultPacket::decode)
                .encoder(UpdateWeaponResultPacket::encode)
                .consumerMainThread(UpdateWeaponResultPacket::handle)
                .add();
    }
}
