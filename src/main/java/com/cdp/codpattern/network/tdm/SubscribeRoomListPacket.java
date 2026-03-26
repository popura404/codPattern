package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.fpsmatch.room.CodTdmRoomManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C→S: 订阅房间列表摘要流。
 */
public class SubscribeRoomListPacket {
    public SubscribeRoomListPacket() {
    }

    public SubscribeRoomListPacket(FriendlyByteBuf buf) {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public static SubscribeRoomListPacket decode(FriendlyByteBuf buf) {
        return new SubscribeRoomListPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                CodTdmRoomManager.getInstance().subscribeLobbySummary(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
