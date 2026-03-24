package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.network.handler.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S→C: 旁观者加入游戏请求结果 ACK
 */
public class JoinGameResultPacket {
    private final boolean success;
    private final long requestId;
    private final String roomKey;
    private final String reasonCode;
    private final String reasonMessage;

    public JoinGameResultPacket(boolean success, long requestId, String roomKey, String reasonCode, String reasonMessage) {
        this.success = success;
        this.requestId = requestId;
        this.roomKey = roomKey == null ? "" : roomKey;
        this.reasonCode = reasonCode == null ? "" : reasonCode;
        this.reasonMessage = reasonMessage == null ? "" : reasonMessage;
    }

    public JoinGameResultPacket(FriendlyByteBuf buf) {
        this.success = buf.readBoolean();
        this.requestId = buf.readLong();
        this.roomKey = buf.readUtf();
        this.reasonCode = buf.readUtf();
        this.reasonMessage = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(success);
        buf.writeLong(requestId);
        buf.writeUtf(roomKey);
        buf.writeUtf(reasonCode);
        buf.writeUtf(reasonMessage);
    }

    public static JoinGameResultPacket decode(FriendlyByteBuf buf) {
        return new JoinGameResultPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.handleJoinGameResult(
                        success,
                        requestId,
                        roomKey,
                        reasonCode,
                        reasonMessage)));
        ctx.get().setPacketHandled(true);
    }
}
