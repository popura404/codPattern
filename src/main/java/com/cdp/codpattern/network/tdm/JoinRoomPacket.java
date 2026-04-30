package com.cdp.codpattern.network.tdm;

import net.minecraft.network.FriendlyByteBuf;

/**
 * @deprecated Use {@link com.cdp.codpattern.network.match.JoinRoomPacket}.
 */
@Deprecated(forRemoval = false)
public class JoinRoomPacket extends com.cdp.codpattern.network.match.JoinRoomPacket {
    public JoinRoomPacket(String roomKey, String teamName) {
        super(roomKey, teamName);
    }

    public JoinRoomPacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public static JoinRoomPacket decode(FriendlyByteBuf buf) {
        return new JoinRoomPacket(buf);
    }
}
