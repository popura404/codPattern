package com.cdp.codpattern.network.match;

import com.cdp.codpattern.network.handler.ClientPacketBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class CountdownPacket {
    private final int countdown;
    private final boolean blackout;

    public CountdownPacket(int countdown, boolean blackout) {
        this.countdown = countdown;
        this.blackout = blackout;
    }

    public CountdownPacket(FriendlyByteBuf buf) {
        this.countdown = buf.readInt();
        this.blackout = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(countdown);
        buf.writeBoolean(blackout);
    }

    public static CountdownPacket decode(FriendlyByteBuf buf) {
        return new CountdownPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientPacketBridge.countdown(countdown, blackout));
        ctx.get().setPacketHandled(true);
    }
}
