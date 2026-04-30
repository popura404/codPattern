package com.cdp.codpattern.client.network;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.network.match.ModeRoomPacketFactory;

public final class ModeRoomClientPackets {
    private ModeRoomClientPackets() {
    }

    public static void subscribeRoomList() {
        ModNetworkChannel.sendToServer(ModeRoomPacketFactory.subscribeRoomList());
    }

    public static void unsubscribeRoomList() {
        ModNetworkChannel.sendToServer(ModeRoomPacketFactory.unsubscribeRoomList());
    }

    public static void joinRoom(String roomKey) {
        ModNetworkChannel.sendToServer(ModeRoomPacketFactory.joinRoom(roomKey));
    }

    public static void leaveRoom() {
        ModNetworkChannel.sendToServer(ModeRoomPacketFactory.leaveRoom());
    }

    public static void selectTeam(String roomKey, String teamName) {
        ModNetworkChannel.sendToServer(ModeRoomPacketFactory.selectTeam(roomKey, teamName));
    }

    public static void setReadyState(boolean ready) {
        ModNetworkChannel.sendToServer(ModeRoomPacketFactory.setReadyState(ready));
    }

    public static void voteStart() {
        ModNetworkChannel.sendToServer(ModeRoomPacketFactory.voteStart());
    }

    public static void voteEnd() {
        ModNetworkChannel.sendToServer(ModeRoomPacketFactory.voteEnd());
    }

    public static void respondToVote(long voteId, boolean accepted) {
        ModNetworkChannel.sendToServer(ModeRoomPacketFactory.voteResponse(voteId, accepted));
    }

    public static void requestRoomPreviewRoster(String roomKey) {
        ModNetworkChannel.sendToServer(ModeRoomPacketFactory.requestRoomPreviewRoster(roomKey));
    }

    public static void requestRoomRosterResync() {
        ModNetworkChannel.sendToServer(ModeRoomPacketFactory.requestRoomRosterResync());
    }
}
