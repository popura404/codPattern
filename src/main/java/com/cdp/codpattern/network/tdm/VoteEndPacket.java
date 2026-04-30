package com.cdp.codpattern.network.tdm;

import net.minecraft.network.FriendlyByteBuf;

/**
 * @deprecated Use {@link com.cdp.codpattern.network.match.VoteEndPacket}.
 */
@Deprecated(forRemoval = false)
public class VoteEndPacket extends com.cdp.codpattern.network.match.VoteEndPacket {
    public VoteEndPacket() {
        super();
    }

    public VoteEndPacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public static VoteEndPacket decode(FriendlyByteBuf buf) {
        return new VoteEndPacket(buf);
    }
}
