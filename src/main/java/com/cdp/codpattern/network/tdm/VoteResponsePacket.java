package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.app.tdm.service.TdmRoomInteractionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C→S: 投票响应数据包（接受/拒绝）
 */
public class VoteResponsePacket {
    private final long voteId;
    private final boolean accepted;

    public VoteResponsePacket(long voteId, boolean accepted) {
        this.voteId = voteId;
        this.accepted = accepted;
    }

    public VoteResponsePacket(FriendlyByteBuf buf) {
        this.voteId = buf.readLong();
        this.accepted = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(voteId);
        buf.writeBoolean(accepted);
    }

    public static VoteResponsePacket decode(FriendlyByteBuf buf) {
        return new VoteResponsePacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                TdmRoomInteractionService.submitVoteResponse(player, voteId, accepted);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
