package com.cdp.codpattern.network.tdm;

import net.minecraft.network.FriendlyByteBuf;

/**
 * @deprecated Use {@link com.cdp.codpattern.network.match.SetReadyStatePacket}.
 */
@Deprecated(forRemoval = false)
public class SetReadyStatePacket extends com.cdp.codpattern.network.match.SetReadyStatePacket {
    public SetReadyStatePacket(boolean ready) {
        super(ready);
    }

    public SetReadyStatePacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public static SetReadyStatePacket decode(FriendlyByteBuf buf) {
        return new SetReadyStatePacket(buf);
    }
}
