package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.client.ClientTdmState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * S→C: 死亡视角数据包
 */
public class DeathCamPacket {
    private final UUID killerUuid;
    private final String killerName;
    private final int durationTicks;

    public DeathCamPacket(UUID killerUuid, String killerName, int durationTicks) {
        this.killerUuid = killerUuid;
        this.killerName = killerName;
        this.durationTicks = durationTicks;
    }

    public DeathCamPacket(FriendlyByteBuf buf) {
        this.killerUuid = buf.readUUID();
        this.killerName = buf.readUtf();
        this.durationTicks = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(killerUuid);
        buf.writeUtf(killerName);
        buf.writeInt(durationTicks);
    }

    public static DeathCamPacket decode(FriendlyByteBuf buf) {
        return new DeathCamPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft.getInstance().execute(() -> {
                // 设置死亡视角状态
                ClientTdmState.setDeathCam(killerName, durationTicks);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
