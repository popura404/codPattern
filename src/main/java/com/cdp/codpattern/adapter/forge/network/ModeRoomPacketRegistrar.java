package com.cdp.codpattern.adapter.forge.network;

import com.cdp.codpattern.network.match.JoinRoomPacket;
import com.cdp.codpattern.network.match.JoinRoomResultPacket;
import com.cdp.codpattern.network.match.LeaveRoomPacket;
import com.cdp.codpattern.network.match.LeaveRoomResultPacket;
import com.cdp.codpattern.network.match.PopupNoticePacket;
import com.cdp.codpattern.network.match.RequestRoomListPacket;
import com.cdp.codpattern.network.match.RequestRoomPreviewRosterPacket;
import com.cdp.codpattern.network.match.RequestRoomRosterResyncPacket;
import com.cdp.codpattern.network.match.RoomListSyncPacket;
import com.cdp.codpattern.network.match.RoomPlayerDeltaPacket;
import com.cdp.codpattern.network.match.RoomPreviewRosterPacket;
import com.cdp.codpattern.network.match.SelectTeamPacket;
import com.cdp.codpattern.network.match.SetReadyStatePacket;
import com.cdp.codpattern.network.match.SubscribeRoomListPacket;
import com.cdp.codpattern.network.match.TeamPlayerListPacket;
import com.cdp.codpattern.network.match.UnsubscribeRoomListPacket;
import com.cdp.codpattern.network.match.VoteDialogPacket;
import com.cdp.codpattern.network.match.VoteEndPacket;
import com.cdp.codpattern.network.match.VoteResponsePacket;
import com.cdp.codpattern.network.match.VoteStartPacket;
import net.minecraftforge.network.NetworkDirection;

final class ModeRoomPacketRegistrar {
    private ModeRoomPacketRegistrar() {
    }

    static void registerInitialRoomPackets() {
        ModNetworkChannel.CHANNEL.messageBuilder(RequestRoomListPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(RequestRoomListPacket::decode)
                .encoder(RequestRoomListPacket::encode)
                .consumerMainThread(RequestRoomListPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(SubscribeRoomListPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(SubscribeRoomListPacket::decode)
                .encoder(SubscribeRoomListPacket::encode)
                .consumerMainThread(SubscribeRoomListPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(UnsubscribeRoomListPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(UnsubscribeRoomListPacket::decode)
                .encoder(UnsubscribeRoomListPacket::encode)
                .consumerMainThread(UnsubscribeRoomListPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(RequestRoomRosterResyncPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(RequestRoomRosterResyncPacket::decode)
                .encoder(RequestRoomRosterResyncPacket::encode)
                .consumerMainThread(RequestRoomRosterResyncPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(RequestRoomPreviewRosterPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(RequestRoomPreviewRosterPacket::decode)
                .encoder(RequestRoomPreviewRosterPacket::encode)
                .consumerMainThread(RequestRoomPreviewRosterPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(JoinRoomPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(JoinRoomPacket::decode)
                .encoder(JoinRoomPacket::encode)
                .consumerMainThread(JoinRoomPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(LeaveRoomPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(LeaveRoomPacket::decode)
                .encoder(LeaveRoomPacket::encode)
                .consumerMainThread(LeaveRoomPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(SelectTeamPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(SelectTeamPacket::decode)
                .encoder(SelectTeamPacket::encode)
                .consumerMainThread(SelectTeamPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(VoteStartPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(VoteStartPacket::decode)
                .encoder(VoteStartPacket::encode)
                .consumerMainThread(VoteStartPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(VoteEndPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(VoteEndPacket::decode)
                .encoder(VoteEndPacket::encode)
                .consumerMainThread(VoteEndPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(VoteResponsePacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(VoteResponsePacket::decode)
                .encoder(VoteResponsePacket::encode)
                .consumerMainThread(VoteResponsePacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(RoomListSyncPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(RoomListSyncPacket::decode)
                .encoder(RoomListSyncPacket::encode)
                .consumerMainThread(RoomListSyncPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(TeamPlayerListPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(TeamPlayerListPacket::decode)
                .encoder(TeamPlayerListPacket::encode)
                .consumerMainThread(TeamPlayerListPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(RoomPreviewRosterPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(RoomPreviewRosterPacket::decode)
                .encoder(RoomPreviewRosterPacket::encode)
                .consumerMainThread(RoomPreviewRosterPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(RoomPlayerDeltaPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(RoomPlayerDeltaPacket::decode)
                .encoder(RoomPlayerDeltaPacket::encode)
                .consumerMainThread(RoomPlayerDeltaPacket::handle)
                .add();
    }

    static void registerRoomFeedbackPackets() {
        ModNetworkChannel.CHANNEL.messageBuilder(VoteDialogPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(VoteDialogPacket::decode)
                .encoder(VoteDialogPacket::encode)
                .consumerMainThread(VoteDialogPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(PopupNoticePacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(PopupNoticePacket::decode)
                .encoder(PopupNoticePacket::encode)
                .consumerMainThread(PopupNoticePacket::handle)
                .add();
    }

    static void registerLateRoomPackets() {
        ModNetworkChannel.CHANNEL.messageBuilder(SetReadyStatePacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(SetReadyStatePacket::decode)
                .encoder(SetReadyStatePacket::encode)
                .consumerMainThread(SetReadyStatePacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(JoinRoomResultPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(JoinRoomResultPacket::decode)
                .encoder(JoinRoomResultPacket::encode)
                .consumerMainThread(JoinRoomResultPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(LeaveRoomResultPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(LeaveRoomResultPacket::decode)
                .encoder(LeaveRoomResultPacket::encode)
                .consumerMainThread(LeaveRoomResultPacket::handle)
                .add();
    }
}
