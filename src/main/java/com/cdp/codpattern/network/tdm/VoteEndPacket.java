package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.app.tdm.service.TdmRoomInteractionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C→S: 投票结束游戏数据包
 */
public class VoteEndPacket {

    public VoteEndPacket() {
    }

    public VoteEndPacket(FriendlyByteBuf buf) {
        // 无数据需要读取
    }

    public void encode(FriendlyByteBuf buf) {
        // 无数据需要写入
    }

    public static VoteEndPacket decode(FriendlyByteBuf buf) {
        return new VoteEndPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                TdmRoomInteractionService.initiateEndVote(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
