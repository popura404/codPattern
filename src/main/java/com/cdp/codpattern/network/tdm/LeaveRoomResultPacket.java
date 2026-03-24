package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.network.handler.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S→C: 离开房间结果 ACK
 */
public class LeaveRoomResultPacket {
    private final boolean success;
    private final String roomKey;
    private final String reasonCode;
    private final String reasonMessage;

    public LeaveRoomResultPacket(boolean success, String roomKey, String reasonCode, String reasonMessage) {
        this.success = success;
        this.roomKey = roomKey == null ? "" : roomKey;
        this.reasonCode = reasonCode == null ? "" : reasonCode;
        this.reasonMessage = reasonMessage == null ? "" : reasonMessage;
    }

    public LeaveRoomResultPacket(FriendlyByteBuf buf) {
        this.success = buf.readBoolean();
        this.roomKey = buf.readUtf();
        this.reasonCode = buf.readUtf();
        this.reasonMessage = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(success);
        buf.writeUtf(roomKey);
        buf.writeUtf(reasonCode);
        buf.writeUtf(reasonMessage);
    }

    public static LeaveRoomResultPacket decode(FriendlyByteBuf buf) {
        return new LeaveRoomResultPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.handleLeaveRoomResult(success, roomKey, reasonCode, reasonMessage)));
        ctx.get().setPacketHandled(true);
    }
}
