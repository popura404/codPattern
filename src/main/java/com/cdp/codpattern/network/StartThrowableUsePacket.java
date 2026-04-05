package com.cdp.codpattern.network;

import com.cdp.codpattern.core.throwable.ThrowableInventoryService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class StartThrowableUsePacket {
    private final int slotIndex;

    public StartThrowableUsePacket(int slotIndex) {
        this.slotIndex = slotIndex;
    }

    public StartThrowableUsePacket(FriendlyByteBuf buf) {
        this.slotIndex = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(slotIndex);
    }

    public static StartThrowableUsePacket decode(FriendlyByteBuf buf) {
        return new StartThrowableUsePacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                ThrowableInventoryService.startThrowableUse(player, slotIndex);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
