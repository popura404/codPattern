package com.cdp.codpattern.network.tdm;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * @deprecated Use {@link com.cdp.codpattern.network.match.DeathCamPacket}.
 */
@Deprecated(forRemoval = false)
public class DeathCamPacket extends com.cdp.codpattern.network.match.DeathCamPacket {
    private static final UUID UNKNOWN_UUID = new UUID(0L, 0L);

    public DeathCamPacket(UUID killerUuid,
            String killerName,
            int deathCamTicks,
            int respawnDelayTicks,
            float lockedYaw,
            float lockedPitch) {
        super(killerUuid, killerName, deathCamTicks, respawnDelayTicks, lockedYaw, lockedPitch);
    }

    public DeathCamPacket(UUID killerUuid, String killerName, int deathCamTicks, int respawnDelayTicks) {
        super(killerUuid, killerName, deathCamTicks, respawnDelayTicks);
    }

    public DeathCamPacket(UUID killerUuid, String killerName, int durationTicks) {
        super(killerUuid, killerName, durationTicks);
    }

    public static DeathCamPacket clear() {
        return new DeathCamPacket(UNKNOWN_UUID, "", 0, 0);
    }

    public DeathCamPacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public static DeathCamPacket decode(FriendlyByteBuf buf) {
        return new DeathCamPacket(buf);
    }
}
