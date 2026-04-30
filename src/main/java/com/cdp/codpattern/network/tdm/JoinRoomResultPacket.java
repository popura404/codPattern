package com.cdp.codpattern.network.tdm;

import net.minecraft.network.FriendlyByteBuf;

/**
 * @deprecated Use {@link com.cdp.codpattern.network.match.JoinRoomResultPacket}.
 */
@Deprecated(forRemoval = false)
public class JoinRoomResultPacket extends com.cdp.codpattern.network.match.JoinRoomResultPacket {
    public JoinRoomResultPacket(boolean success, String roomKey, String reasonCode, String reasonMessage) {
        super(success, roomKey, reasonCode, reasonMessage);
    }

    public JoinRoomResultPacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public static JoinRoomResultPacket decode(FriendlyByteBuf buf) {
        return new JoinRoomResultPacket(buf);
    }
}
