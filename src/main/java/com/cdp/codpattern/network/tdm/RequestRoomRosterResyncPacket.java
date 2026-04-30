package com.cdp.codpattern.network.tdm;

import net.minecraft.network.FriendlyByteBuf;

/**
 * @deprecated Use {@link com.cdp.codpattern.network.match.RequestRoomRosterResyncPacket}.
 */
@Deprecated(forRemoval = false)
public class RequestRoomRosterResyncPacket extends com.cdp.codpattern.network.match.RequestRoomRosterResyncPacket {
    public RequestRoomRosterResyncPacket() {
        super();
    }

    public RequestRoomRosterResyncPacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public static RequestRoomRosterResyncPacket decode(FriendlyByteBuf buf) {
        return new RequestRoomRosterResyncPacket(buf);
    }
}
