package com.cdp.codpattern.network.tdm;

import net.minecraft.network.FriendlyByteBuf;

/**
 * @deprecated Use {@link com.cdp.codpattern.network.match.CountdownPacket}.
 */
@Deprecated(forRemoval = false)
public class CountdownPacket extends com.cdp.codpattern.network.match.CountdownPacket {
    public CountdownPacket(int countdown, boolean blackout) {
        super(countdown, blackout);
    }

    public CountdownPacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public static CountdownPacket decode(FriendlyByteBuf buf) {
        return new CountdownPacket(buf);
    }
}
