package com.cdp.codpattern.network.match;

import com.cdp.codpattern.network.handler.ClientPacketBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class DeathCamPacket {
    private static final UUID UNKNOWN_UUID = new UUID(0L, 0L);
    private final UUID killerUuid;
    private final String killerName;
    private final int deathCamTicks;
    private final int respawnDelayTicks;
    private final float lockedYaw;
    private final float lockedPitch;

    public DeathCamPacket(UUID killerUuid,
            String killerName,
            int deathCamTicks,
            int respawnDelayTicks,
            float lockedYaw,
            float lockedPitch) {
        this.killerUuid = killerUuid == null ? UNKNOWN_UUID : killerUuid;
        this.killerName = killerName == null ? "" : killerName;
        this.deathCamTicks = Math.max(0, deathCamTicks);
        this.respawnDelayTicks = Math.max(0, respawnDelayTicks);
        this.lockedYaw = lockedYaw;
        this.lockedPitch = lockedPitch;
    }

    public DeathCamPacket(UUID killerUuid, String killerName, int deathCamTicks, int respawnDelayTicks) {
        this(killerUuid, killerName, deathCamTicks, respawnDelayTicks, Float.NaN, Float.NaN);
    }

    public DeathCamPacket(UUID killerUuid, String killerName, int durationTicks) {
        this(killerUuid, killerName, durationTicks, durationTicks, Float.NaN, Float.NaN);
    }

    public static DeathCamPacket clear() {
        return new DeathCamPacket(UNKNOWN_UUID, "", 0, 0);
    }

    public DeathCamPacket(FriendlyByteBuf buf) {
        this.killerUuid = buf.readUUID();
        this.killerName = buf.readUtf();
        this.deathCamTicks = buf.readInt();
        this.respawnDelayTicks = buf.readableBytes() >= Integer.BYTES ? buf.readInt() : this.deathCamTicks;
        if (buf.readableBytes() >= Float.BYTES * 2) {
            this.lockedYaw = buf.readFloat();
            this.lockedPitch = buf.readFloat();
        } else {
            this.lockedYaw = Float.NaN;
            this.lockedPitch = Float.NaN;
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(killerUuid);
        buf.writeUtf(killerName);
        buf.writeInt(deathCamTicks);
        buf.writeInt(respawnDelayTicks);
        if (hasLockedRotation()) {
            buf.writeFloat(lockedYaw);
            buf.writeFloat(lockedPitch);
        }
    }

    public static DeathCamPacket decode(FriendlyByteBuf buf) {
        return new DeathCamPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                ClientPacketBridge.deathCam(killerName, deathCamTicks, respawnDelayTicks, lockedYaw, lockedPitch));
        ctx.get().setPacketHandled(true);
    }

    private boolean hasLockedRotation() {
        return !Float.isNaN(lockedYaw) && !Float.isNaN(lockedPitch);
    }
}
