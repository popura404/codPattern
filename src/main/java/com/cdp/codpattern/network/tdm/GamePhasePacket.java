package com.cdp.codpattern.network.tdm;

import net.minecraft.network.FriendlyByteBuf;

/**
 * @deprecated Use {@link com.cdp.codpattern.network.match.GamePhasePacket}.
 */
@Deprecated(forRemoval = false)
public class GamePhasePacket extends com.cdp.codpattern.network.match.GamePhasePacket {
    public GamePhasePacket(String phase, int remainingTicks) {
        super(phase, remainingTicks);
    }

    public GamePhasePacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public static GamePhasePacket decode(FriendlyByteBuf buf) {
        return new GamePhasePacket(buf);
    }
}
