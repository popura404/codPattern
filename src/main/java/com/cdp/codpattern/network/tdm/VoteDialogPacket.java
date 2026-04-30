package com.cdp.codpattern.network.tdm;

import net.minecraft.network.FriendlyByteBuf;

/**
 * @deprecated Use {@link com.cdp.codpattern.network.match.VoteDialogPacket}.
 */
@Deprecated(forRemoval = false)
public class VoteDialogPacket extends com.cdp.codpattern.network.match.VoteDialogPacket {
    public VoteDialogPacket(String roomName, long voteId, String voteType, String initiatorName, int requiredVotes,
            int totalVoters) {
        super(roomName, voteId, voteType, initiatorName, requiredVotes, totalVoters);
    }

    public VoteDialogPacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public static VoteDialogPacket decode(FriendlyByteBuf buf) {
        return new VoteDialogPacket(buf);
    }
}
