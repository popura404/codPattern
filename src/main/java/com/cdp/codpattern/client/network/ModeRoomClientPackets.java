package com.cdp.codpattern.client.network;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.network.tdm.JoinRoomPacket;
import com.cdp.codpattern.network.tdm.LeaveRoomPacket;
import com.cdp.codpattern.network.tdm.RequestRoomPreviewRosterPacket;
import com.cdp.codpattern.network.tdm.RequestRoomRosterResyncPacket;
import com.cdp.codpattern.network.tdm.SelectTeamPacket;
import com.cdp.codpattern.network.tdm.SetReadyStatePacket;
import com.cdp.codpattern.network.tdm.SubscribeRoomListPacket;
import com.cdp.codpattern.network.tdm.UnsubscribeRoomListPacket;
import com.cdp.codpattern.network.tdm.VoteEndPacket;
import com.cdp.codpattern.network.tdm.VoteResponsePacket;
import com.cdp.codpattern.network.tdm.VoteStartPacket;

public final class ModeRoomClientPackets {
    private ModeRoomClientPackets() {
    }

    public static void subscribeRoomList() {
        ModNetworkChannel.sendToServer(new SubscribeRoomListPacket());
    }

    public static void unsubscribeRoomList() {
        ModNetworkChannel.sendToServer(new UnsubscribeRoomListPacket());
    }

    public static void joinRoom(String roomKey) {
        ModNetworkChannel.sendToServer(new JoinRoomPacket(roomKey, null));
    }

    public static void leaveRoom() {
        ModNetworkChannel.sendToServer(new LeaveRoomPacket());
    }

    public static void selectTeam(String roomKey, String teamName) {
        ModNetworkChannel.sendToServer(new SelectTeamPacket(roomKey, teamName));
    }

    public static void setReadyState(boolean ready) {
        ModNetworkChannel.sendToServer(new SetReadyStatePacket(ready));
    }

    public static void voteStart() {
        ModNetworkChannel.sendToServer(new VoteStartPacket());
    }

    public static void voteEnd() {
        ModNetworkChannel.sendToServer(new VoteEndPacket());
    }

    public static void respondToVote(long voteId, boolean accepted) {
        ModNetworkChannel.sendToServer(new VoteResponsePacket(voteId, accepted));
    }

    public static void requestRoomPreviewRoster(String roomKey) {
        ModNetworkChannel.sendToServer(new RequestRoomPreviewRosterPacket(roomKey));
    }

    public static void requestRoomRosterResync() {
        ModNetworkChannel.sendToServer(new RequestRoomRosterResyncPacket());
    }
}
