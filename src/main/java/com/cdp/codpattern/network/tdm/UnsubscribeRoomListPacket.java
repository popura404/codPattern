package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.fpsmatch.room.CodTdmRoomManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C→S: 取消订阅房间列表摘要流。
 */
public class UnsubscribeRoomListPacket {
    public UnsubscribeRoomListPacket() {
    }

    public UnsubscribeRoomListPacket(FriendlyByteBuf buf) {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public static UnsubscribeRoomListPacket decode(FriendlyByteBuf buf) {
        return new UnsubscribeRoomListPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                CodTdmRoomManager.getInstance().unsubscribeLobbySummary(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
