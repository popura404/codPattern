package com.cdp.codpattern.network.tdm;

import net.minecraft.network.FriendlyByteBuf;

/**
 * @deprecated Use {@link com.cdp.codpattern.network.match.SubscribeRoomListPacket}.
 */
@Deprecated(forRemoval = false)
public class SubscribeRoomListPacket extends com.cdp.codpattern.network.match.SubscribeRoomListPacket {
    public SubscribeRoomListPacket() {
        super();
    }

    public SubscribeRoomListPacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public static SubscribeRoomListPacket decode(FriendlyByteBuf buf) {
        return new SubscribeRoomListPacket(buf);
    }
}
