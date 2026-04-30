package com.cdp.codpattern.network.tdm;

import net.minecraft.network.FriendlyByteBuf;

/**
 * @deprecated Use {@link com.cdp.codpattern.network.match.PhysicsMobRetainPacket}.
 */
@Deprecated(forRemoval = false)
public class PhysicsMobRetainPacket extends com.cdp.codpattern.network.match.PhysicsMobRetainPacket {
    public PhysicsMobRetainPacket(int entityId, double x, double y, double z, float yRot, float xRot, float yHeadRot,
            float yBodyRot, double motionX, double motionY, double motionZ) {
        super(entityId, x, y, z, yRot, xRot, yHeadRot, yBodyRot, motionX, motionY, motionZ);
    }

    public PhysicsMobRetainPacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public static PhysicsMobRetainPacket decode(FriendlyByteBuf buf) {
        return new PhysicsMobRetainPacket(buf);
    }
}
