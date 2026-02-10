package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.fpsmatch.room.CodTdmRoomManager;
import com.cdp.codpattern.network.handler.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C→S: 请求房间列表数据包
 */
public class RequestRoomListPacket {

    public RequestRoomListPacket() {
    }

    public RequestRoomListPacket(FriendlyByteBuf buf) {
        // 无数据需要读取
    }

    public void encode(FriendlyByteBuf buf) {
        // 无数据需要写入
    }

    public static RequestRoomListPacket decode(FriendlyByteBuf buf) {
        return new RequestRoomListPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                // 发送房间列表给客户端
                CodTdmRoomManager.getInstance().syncRoomListToClient(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
