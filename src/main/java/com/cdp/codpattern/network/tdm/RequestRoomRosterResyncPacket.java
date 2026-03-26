package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.compat.fpsmatch.FpsMatchGatewayProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C→S: 请求当前房间名单全量回正。
 */
public class RequestRoomRosterResyncPacket {
    public RequestRoomRosterResyncPacket() {
    }

    public RequestRoomRosterResyncPacket(FriendlyByteBuf buf) {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public static RequestRoomRosterResyncPacket decode(FriendlyByteBuf buf) {
        return new RequestRoomRosterResyncPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                FpsMatchGatewayProvider.gateway()
                        .findPlayerRoomActionPort(player)
                        .ifPresent(port -> port.requestRosterResync(player));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
