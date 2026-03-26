package com.cdp.codpattern.adapter.forge.network;

import com.cdp.codpattern.network.tdm.CountdownPacket;
import com.cdp.codpattern.network.tdm.CombatMarkerConfigPacket;
import com.cdp.codpattern.network.tdm.DeathCamPacket;
import com.cdp.codpattern.network.tdm.GamePhasePacket;
import com.cdp.codpattern.network.tdm.JoinRoomPacket;
import com.cdp.codpattern.network.tdm.JoinRoomResultPacket;
import com.cdp.codpattern.network.tdm.KillFeedPacket;
import com.cdp.codpattern.network.tdm.LeaveRoomPacket;
import com.cdp.codpattern.network.tdm.LeaveRoomResultPacket;
import com.cdp.codpattern.network.tdm.PhysicsMobRetainPacket;
import com.cdp.codpattern.network.tdm.PopupNoticePacket;
import com.cdp.codpattern.network.tdm.RequestRoomListPacket;
import com.cdp.codpattern.network.tdm.RoomListSyncPacket;
import com.cdp.codpattern.network.tdm.ScoreUpdatePacket;
import com.cdp.codpattern.network.tdm.SelectTeamPacket;
import com.cdp.codpattern.network.tdm.SetReadyStatePacket;
import com.cdp.codpattern.network.tdm.TeamPlayerListPacket;
import com.cdp.codpattern.network.tdm.VoteDialogPacket;
import com.cdp.codpattern.network.tdm.VoteEndPacket;
import com.cdp.codpattern.network.tdm.VoteResponsePacket;
import com.cdp.codpattern.network.tdm.VoteStartPacket;
import net.minecraftforge.network.NetworkDirection;

final class TdmPacketRegistrar {
    private TdmPacketRegistrar() {
    }

    static void register() {
        ModNetworkChannel.CHANNEL.messageBuilder(RequestRoomListPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(RequestRoomListPacket::decode)
                .encoder(RequestRoomListPacket::encode)
                .consumerMainThread(RequestRoomListPacket::handle)
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

        ModNetworkChannel.CHANNEL.messageBuilder(DeathCamPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(DeathCamPacket::decode)
                .encoder(DeathCamPacket::encode)
                .consumerMainThread(DeathCamPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(KillFeedPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(KillFeedPacket::decode)
                .encoder(KillFeedPacket::encode)
                .consumerMainThread(KillFeedPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(PhysicsMobRetainPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(PhysicsMobRetainPacket::decode)
                .encoder(PhysicsMobRetainPacket::encode)
                .consumerMainThread(PhysicsMobRetainPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(GamePhasePacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(GamePhasePacket::decode)
                .encoder(GamePhasePacket::encode)
                .consumerMainThread(GamePhasePacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(CountdownPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(CountdownPacket::decode)
                .encoder(CountdownPacket::encode)
                .consumerMainThread(CountdownPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(ScoreUpdatePacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(ScoreUpdatePacket::decode)
                .encoder(ScoreUpdatePacket::encode)
                .consumerMainThread(ScoreUpdatePacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(CombatMarkerConfigPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(CombatMarkerConfigPacket::decode)
                .encoder(CombatMarkerConfigPacket::encode)
                .consumerMainThread(CombatMarkerConfigPacket::handle)
                .add();

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
