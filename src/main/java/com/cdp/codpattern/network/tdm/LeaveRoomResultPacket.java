package com.cdp.codpattern.network.tdm;

import net.minecraft.network.FriendlyByteBuf;

/**
 * @deprecated Use {@link com.cdp.codpattern.network.match.LeaveRoomResultPacket}.
 */
@Deprecated(forRemoval = false)
public class LeaveRoomResultPacket extends com.cdp.codpattern.network.match.LeaveRoomResultPacket {
    public LeaveRoomResultPacket(boolean success, String roomKey, String reasonCode, String reasonMessage) {
        super(success, roomKey, reasonCode, reasonMessage);
    }

    public LeaveRoomResultPacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public static LeaveRoomResultPacket decode(FriendlyByteBuf buf) {
        return new LeaveRoomResultPacket(buf);
    }
}
