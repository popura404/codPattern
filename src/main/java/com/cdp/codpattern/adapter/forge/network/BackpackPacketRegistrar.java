package com.cdp.codpattern.adapter.forge.network;

import com.cdp.codpattern.network.AddBackpackPacket;
import com.cdp.codpattern.network.CloneBackpackPacket;
import com.cdp.codpattern.network.DeleteBackpackPacket;
import com.cdp.codpattern.network.OpenBackpackScreenPacket;
import com.cdp.codpattern.network.RenameBackpackPacket;
import com.cdp.codpattern.network.RequestBackpackConfigPacket;
import com.cdp.codpattern.network.RequestWeaponFilterPacket;
import com.cdp.codpattern.network.SelectBackpackPacket;
import com.cdp.codpattern.network.SyncBackpackConfigPacket;
import com.cdp.codpattern.network.SyncWeaponFilterPacket;
import net.minecraftforge.network.NetworkDirection;

final class BackpackPacketRegistrar {
    private BackpackPacketRegistrar() {
    }

    static void register() {
        ModNetworkChannel.CHANNEL.messageBuilder(SelectBackpackPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(SelectBackpackPacket::decode)
                .encoder(SelectBackpackPacket::encode)
                .consumerMainThread(SelectBackpackPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(AddBackpackPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(AddBackpackPacket::decode)
                .encoder(AddBackpackPacket::encode)
                .consumerMainThread(AddBackpackPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(RenameBackpackPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(RenameBackpackPacket::decode)
                .encoder(RenameBackpackPacket::encode)
                .consumerMainThread(RenameBackpackPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(DeleteBackpackPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(DeleteBackpackPacket::decode)
                .encoder(DeleteBackpackPacket::encode)
                .consumerMainThread(DeleteBackpackPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(CloneBackpackPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(CloneBackpackPacket::decode)
                .encoder(CloneBackpackPacket::encode)
                .consumerMainThread(CloneBackpackPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(SyncBackpackConfigPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncBackpackConfigPacket::decode)
                .encoder(SyncBackpackConfigPacket::encode)
                .consumerMainThread(SyncBackpackConfigPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(RequestBackpackConfigPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(RequestBackpackConfigPacket::decode)
                .encoder(RequestBackpackConfigPacket::encode)
                .consumerMainThread(RequestBackpackConfigPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(RequestWeaponFilterPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(RequestWeaponFilterPacket::decode)
                .encoder(RequestWeaponFilterPacket::encode)
                .consumerMainThread(RequestWeaponFilterPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(SyncWeaponFilterPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncWeaponFilterPacket::decode)
                .encoder(SyncWeaponFilterPacket::encode)
                .consumerMainThread(SyncWeaponFilterPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(OpenBackpackScreenPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(OpenBackpackScreenPacket::decode)
                .encoder(OpenBackpackScreenPacket::encode)
                .consumerMainThread(OpenBackpackScreenPacket::handle)
                .add();
    }
}
