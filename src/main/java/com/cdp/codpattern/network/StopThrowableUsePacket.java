package com.cdp.codpattern.network;

import com.cdp.codpattern.core.throwable.ThrowableInventoryService;
import com.cdp.codpattern.core.throwable.ThrowableStopReason;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class StopThrowableUsePacket {
    private final ThrowableStopReason reason;

    public StopThrowableUsePacket(ThrowableStopReason reason) {
        this.reason = reason;
    }

    public StopThrowableUsePacket(FriendlyByteBuf buf) {
        this.reason = buf.readEnum(ThrowableStopReason.class);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(reason);
    }

    public static StopThrowableUsePacket decode(FriendlyByteBuf buf) {
        return new StopThrowableUsePacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                ThrowableInventoryService.stopThrowableUse(player, reason);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
