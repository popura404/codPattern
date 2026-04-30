package com.cdp.codpattern.network.tdm;

import net.minecraft.network.FriendlyByteBuf;

/**
 * @deprecated Use {@link com.cdp.codpattern.network.match.CombatMarkerConfigPacket}.
 */
@Deprecated(forRemoval = false)
public class CombatMarkerConfigPacket extends com.cdp.codpattern.network.match.CombatMarkerConfigPacket {
    public CombatMarkerConfigPacket(float focusHalfAngleDegrees,
            int focusRequiredTicks,
            double barMaxDistance,
            int barVisibleGraceTicks) {
        super(focusHalfAngleDegrees, focusRequiredTicks, barMaxDistance, barVisibleGraceTicks);
    }

    public CombatMarkerConfigPacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public static CombatMarkerConfigPacket decode(FriendlyByteBuf buf) {
        return new CombatMarkerConfigPacket(buf);
    }
}
