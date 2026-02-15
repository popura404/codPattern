package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.network.handler.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S→C: 倒计时同步数据包
 */
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
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientPacketHandler.handleCountdown(countdown, blackout));
        });
        ctx.get().setPacketHandled(true);
    }
}
