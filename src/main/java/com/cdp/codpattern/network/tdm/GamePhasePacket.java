package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.client.ClientTdmState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S→C: 游戏阶段同步数据包
 */
public class GamePhasePacket {
    private final String phase;
    private final int remainingTicks;

    public GamePhasePacket(String phase, int remainingTicks) {
        this.phase = phase;
        this.remainingTicks = remainingTicks;
    }

    public GamePhasePacket(FriendlyByteBuf buf) {
        this.phase = buf.readUtf();
        this.remainingTicks = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(phase);
        buf.writeInt(remainingTicks);
    }

    public static GamePhasePacket decode(FriendlyByteBuf buf) {
        return new GamePhasePacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft.getInstance().execute(() -> {
                // 更新客户端游戏阶段显示
                ClientTdmState.updatePhase(phase, remainingTicks);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
