package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.fpsmatch.map.CodTdmMap;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C→S: 投票开始游戏数据包
 */
public class VoteStartPacket {

    public VoteStartPacket() {
    }

    public VoteStartPacket(FriendlyByteBuf buf) {
        // 无数据需要读取
    }

    public void encode(FriendlyByteBuf buf) {
        // 无数据需要写入
    }

    public static VoteStartPacket decode(FriendlyByteBuf buf) {
        return new VoteStartPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                FPSMCore.getInstance().getMapByPlayer(player).ifPresent(map -> {
                    if (map instanceof CodTdmMap tdmMap) {
                        tdmMap.initiateStartVote(player.getUUID());
                    }
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
