package com.cdp.codpattern.network.tdm;

import net.minecraft.network.FriendlyByteBuf;

/**
 * @deprecated Use {@link com.cdp.codpattern.network.match.SelectTeamPacket}.
 */
@Deprecated(forRemoval = false)
public class SelectTeamPacket extends com.cdp.codpattern.network.match.SelectTeamPacket {
    public SelectTeamPacket(String roomKey, String teamName) {
        super(roomKey, teamName);
    }

    public SelectTeamPacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public static SelectTeamPacket decode(FriendlyByteBuf buf) {
        return new SelectTeamPacket(buf);
    }
}
