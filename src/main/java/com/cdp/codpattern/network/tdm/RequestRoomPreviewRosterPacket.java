package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.compat.fpsmatch.FpsMatchGatewayProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C→S: 请求指定房间的预览名单快照。
 */
public class RequestRoomPreviewRosterPacket {
    private final String roomKey;

    public RequestRoomPreviewRosterPacket(String roomKey) {
        this.roomKey = roomKey == null ? "" : roomKey;
    }

    public RequestRoomPreviewRosterPacket(FriendlyByteBuf buf) {
        this.roomKey = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(roomKey);
    }

    public static RequestRoomPreviewRosterPacket decode(FriendlyByteBuf buf) {
        return new RequestRoomPreviewRosterPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || roomKey.isBlank()) {
                return;
            }
            RoomId roomId;
            try {
                roomId = RoomId.decode(roomKey);
            } catch (IllegalArgumentException ignored) {
                return;
            }
            FpsMatchGatewayProvider.gateway()
                    .findRoomActionPort(roomId)
                    .ifPresent(port -> port.requestRosterPreview(player));
        });
        ctx.get().setPacketHandled(true);
    }
}
