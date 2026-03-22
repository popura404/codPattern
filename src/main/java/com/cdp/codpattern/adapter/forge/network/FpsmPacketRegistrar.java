package com.cdp.codpattern.adapter.forge.network;

import com.phasetranscrystal.fpsmatch.common.packet.AddAreaDataS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.AddPointDataS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.MapCreatorToolActionC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.OpenMapCreatorToolScreenS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.OpenSpawnPointToolScreenS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.RemoveDebugDataByPrefixS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.SpawnPointToolActionC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.ToolInteractionC2SPacket;
import net.minecraftforge.network.NetworkDirection;

final class FpsmPacketRegistrar {
    private FpsmPacketRegistrar() {
    }

    static void register() {
        ModNetworkChannel.CHANNEL.messageBuilder(ToolInteractionC2SPacket.class, ModNetworkChannel.nextMessageId(),
                        NetworkDirection.PLAY_TO_SERVER)
                .decoder(ToolInteractionC2SPacket::decode)
                .encoder(ToolInteractionC2SPacket::encode)
                .consumerMainThread(ToolInteractionC2SPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(MapCreatorToolActionC2SPacket.class, ModNetworkChannel.nextMessageId(),
                        NetworkDirection.PLAY_TO_SERVER)
                .decoder(MapCreatorToolActionC2SPacket::decode)
                .encoder(MapCreatorToolActionC2SPacket::encode)
                .consumerMainThread(MapCreatorToolActionC2SPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(SpawnPointToolActionC2SPacket.class, ModNetworkChannel.nextMessageId(),
                        NetworkDirection.PLAY_TO_SERVER)
                .decoder(SpawnPointToolActionC2SPacket::decode)
                .encoder(SpawnPointToolActionC2SPacket::encode)
                .consumerMainThread(SpawnPointToolActionC2SPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(AddAreaDataS2CPacket.class, ModNetworkChannel.nextMessageId(),
                        NetworkDirection.PLAY_TO_CLIENT)
                .decoder(AddAreaDataS2CPacket::decode)
                .encoder(AddAreaDataS2CPacket::encode)
                .consumerMainThread(AddAreaDataS2CPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(AddPointDataS2CPacket.class, ModNetworkChannel.nextMessageId(),
                        NetworkDirection.PLAY_TO_CLIENT)
                .decoder(AddPointDataS2CPacket::decode)
                .encoder(AddPointDataS2CPacket::encode)
                .consumerMainThread(AddPointDataS2CPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(RemoveDebugDataByPrefixS2CPacket.class, ModNetworkChannel.nextMessageId(),
                        NetworkDirection.PLAY_TO_CLIENT)
                .decoder(RemoveDebugDataByPrefixS2CPacket::decode)
                .encoder(RemoveDebugDataByPrefixS2CPacket::encode)
                .consumerMainThread(RemoveDebugDataByPrefixS2CPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(OpenMapCreatorToolScreenS2CPacket.class, ModNetworkChannel.nextMessageId(),
                        NetworkDirection.PLAY_TO_CLIENT)
                .decoder(OpenMapCreatorToolScreenS2CPacket::decode)
                .encoder(OpenMapCreatorToolScreenS2CPacket::encode)
                .consumerMainThread(OpenMapCreatorToolScreenS2CPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(OpenSpawnPointToolScreenS2CPacket.class, ModNetworkChannel.nextMessageId(),
                        NetworkDirection.PLAY_TO_CLIENT)
                .decoder(OpenSpawnPointToolScreenS2CPacket::decode)
                .encoder(OpenSpawnPointToolScreenS2CPacket::encode)
                .consumerMainThread(OpenSpawnPointToolScreenS2CPacket::handle)
                .add();
    }
}
