package com.cdp.codpattern.network.tdm;

import net.minecraft.network.FriendlyByteBuf;

/**
 * @deprecated Use {@link com.cdp.codpattern.network.match.VoteStartPacket}.
 */
@Deprecated(forRemoval = false)
public class VoteStartPacket extends com.cdp.codpattern.network.match.VoteStartPacket {
    public VoteStartPacket() {
        super();
    }

    public VoteStartPacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public static VoteStartPacket decode(FriendlyByteBuf buf) {
        return new VoteStartPacket(buf);
    }
}
