package com.cdp.codpattern.network.tdm;

import net.minecraft.network.FriendlyByteBuf;

/**
 * @deprecated Use {@link com.cdp.codpattern.network.match.RequestRoomListPacket}.
 */
@Deprecated(forRemoval = false)
public class RequestRoomListPacket extends com.cdp.codpattern.network.match.RequestRoomListPacket {
    public RequestRoomListPacket() {
        super();
    }

    public RequestRoomListPacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public static RequestRoomListPacket decode(FriendlyByteBuf buf) {
        return new RequestRoomListPacket(buf);
    }
}
