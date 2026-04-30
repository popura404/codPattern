package com.cdp.codpattern.network.tdm;

import net.minecraft.network.FriendlyByteBuf;

/**
 * @deprecated Use {@link com.cdp.codpattern.network.match.VoteResponsePacket}.
 */
@Deprecated(forRemoval = false)
public class VoteResponsePacket extends com.cdp.codpattern.network.match.VoteResponsePacket {
    public VoteResponsePacket(long voteId, boolean accepted) {
        super(voteId, accepted);
    }

    public VoteResponsePacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public static VoteResponsePacket decode(FriendlyByteBuf buf) {
        return new VoteResponsePacket(buf);
    }
}
