package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.network.handler.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S→C: 投票弹窗请求数据包
 */
public class VoteDialogPacket {
    private final String roomName;
    private final long voteId;
    private final String voteType;
    private final String initiatorName;
    private final int requiredVotes;
    private final int totalVoters;

    public VoteDialogPacket(String roomName, long voteId, String voteType, String initiatorName, int requiredVotes,
            int totalVoters) {
        this.roomName = roomName;
        this.voteId = voteId;
        this.voteType = voteType;
        this.initiatorName = initiatorName;
        this.requiredVotes = requiredVotes;
        this.totalVoters = totalVoters;
    }

    public VoteDialogPacket(FriendlyByteBuf buf) {
        this.roomName = buf.readUtf();
        this.voteId = buf.readLong();
        this.voteType = buf.readUtf();
        this.initiatorName = buf.readUtf();
        this.requiredVotes = buf.readInt();
        this.totalVoters = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(roomName);
        buf.writeLong(voteId);
        buf.writeUtf(voteType);
        buf.writeUtf(initiatorName);
        buf.writeInt(requiredVotes);
        buf.writeInt(totalVoters);
    }

    public static VoteDialogPacket decode(FriendlyByteBuf buf) {
        return new VoteDialogPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleVoteDialog(
                    roomName,
                    voteId,
                    voteType,
                    initiatorName,
                    requiredVotes,
                    totalVoters));
        });
        ctx.get().setPacketHandled(true);
    }
}
