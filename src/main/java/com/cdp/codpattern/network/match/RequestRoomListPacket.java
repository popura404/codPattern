package com.cdp.codpattern.network.match;

import com.cdp.codpattern.fpsmatch.room.CodTdmRoomManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RequestRoomListPacket {
    public RequestRoomListPacket() {
    }

    public RequestRoomListPacket(FriendlyByteBuf buf) {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public static RequestRoomListPacket decode(FriendlyByteBuf buf) {
        return new RequestRoomListPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                CodTdmRoomManager.getInstance().syncRoomListToClient(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
