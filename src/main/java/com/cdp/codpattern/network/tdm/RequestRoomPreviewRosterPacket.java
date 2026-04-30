package com.cdp.codpattern.network.tdm;

import net.minecraft.network.FriendlyByteBuf;

/**
 * @deprecated Use {@link com.cdp.codpattern.network.match.RequestRoomPreviewRosterPacket}.
 */
@Deprecated(forRemoval = false)
public class RequestRoomPreviewRosterPacket extends com.cdp.codpattern.network.match.RequestRoomPreviewRosterPacket {
    public RequestRoomPreviewRosterPacket(String roomKey) {
        super(roomKey);
    }

    public RequestRoomPreviewRosterPacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public static RequestRoomPreviewRosterPacket decode(FriendlyByteBuf buf) {
        return new RequestRoomPreviewRosterPacket(buf);
    }
}
