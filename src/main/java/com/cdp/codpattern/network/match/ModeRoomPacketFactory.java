package com.cdp.codpattern.network.match;

/**
 * Centralizes mode-room wire packet construction for client callers.
 */
public final class ModeRoomPacketFactory {
    private ModeRoomPacketFactory() {
    }

    public static Object subscribeRoomList() {
        return new SubscribeRoomListPacket();
    }

    public static Object unsubscribeRoomList() {
        return new UnsubscribeRoomListPacket();
    }

    public static Object joinRoom(String roomKey) {
        return new JoinRoomPacket(roomKey, null);
    }

    public static Object leaveRoom() {
        return new LeaveRoomPacket();
    }

    public static Object selectTeam(String roomKey, String teamName) {
        return new SelectTeamPacket(roomKey, teamName);
    }

    public static Object setReadyState(boolean ready) {
        return new SetReadyStatePacket(ready);
    }

    public static Object voteStart() {
        return new VoteStartPacket();
    }

    public static Object voteEnd() {
        return new VoteEndPacket();
    }

    public static Object voteResponse(long voteId, boolean accepted) {
        return new VoteResponsePacket(voteId, accepted);
    }

    public static Object requestRoomPreviewRoster(String roomKey) {
        return new RequestRoomPreviewRosterPacket(roomKey);
    }

    public static Object requestRoomRosterResync() {
        return new RequestRoomRosterResyncPacket();
    }
}
