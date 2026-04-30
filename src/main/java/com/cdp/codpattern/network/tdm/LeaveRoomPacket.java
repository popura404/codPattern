package com.cdp.codpattern.network.tdm;

import net.minecraft.network.FriendlyByteBuf;

/**
 * @deprecated Use {@link com.cdp.codpattern.network.match.LeaveRoomPacket}.
 */
@Deprecated(forRemoval = false)
public class LeaveRoomPacket extends com.cdp.codpattern.network.match.LeaveRoomPacket {
    public LeaveRoomPacket() {
        super();
    }

    public LeaveRoomPacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public static LeaveRoomPacket decode(FriendlyByteBuf buf) {
        return new LeaveRoomPacket(buf);
    }
}
