package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.network.handler.ClientPacketBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * S→C: 死亡视角数据包
 */
public class DeathCamPacket {
    private static final UUID UNKNOWN_UUID = new UUID(0L, 0L);
    private final UUID killerUuid;
    private final String killerName;
    private final int deathCamTicks;
    private final int respawnDelayTicks;

    public DeathCamPacket(UUID killerUuid, String killerName, int deathCamTicks, int respawnDelayTicks) {
        this.killerUuid = killerUuid == null ? UNKNOWN_UUID : killerUuid;
        this.killerName = killerName == null ? "" : killerName;
        this.deathCamTicks = Math.max(0, deathCamTicks);
        this.respawnDelayTicks = Math.max(0, respawnDelayTicks);
    }

    public DeathCamPacket(UUID killerUuid, String killerName, int durationTicks) {
        this(killerUuid, killerName, durationTicks, durationTicks);
    }

    public static DeathCamPacket clear() {
        return new DeathCamPacket(UNKNOWN_UUID, "", 0, 0);
    }

    public DeathCamPacket(FriendlyByteBuf buf) {
        this.killerUuid = buf.readUUID();
        this.killerName = buf.readUtf();
        this.deathCamTicks = buf.readInt();
        this.respawnDelayTicks = buf.readableBytes() >= Integer.BYTES ? buf.readInt() : this.deathCamTicks;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(killerUuid);
        buf.writeUtf(killerName);
        buf.writeInt(deathCamTicks);
        buf.writeInt(respawnDelayTicks);
    }

    public static DeathCamPacket decode(FriendlyByteBuf buf) {
        return new DeathCamPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientPacketBridge.deathCam(killerName, respawnDelayTicks);
        });
        ctx.get().setPacketHandled(true);
    }
}
