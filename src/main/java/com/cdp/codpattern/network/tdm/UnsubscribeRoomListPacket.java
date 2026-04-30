package com.cdp.codpattern.network.tdm;

import net.minecraft.network.FriendlyByteBuf;

/**
 * @deprecated Use {@link com.cdp.codpattern.network.match.UnsubscribeRoomListPacket}.
 */
@Deprecated(forRemoval = false)
public class UnsubscribeRoomListPacket extends com.cdp.codpattern.network.match.UnsubscribeRoomListPacket {
    public UnsubscribeRoomListPacket() {
        super();
    }

    public UnsubscribeRoomListPacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public static UnsubscribeRoomListPacket decode(FriendlyByteBuf buf) {
        return new UnsubscribeRoomListPacket(buf);
    }
}
