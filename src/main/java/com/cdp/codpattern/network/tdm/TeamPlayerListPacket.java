package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;
import java.util.Map;

/**
 * @deprecated Use {@link com.cdp.codpattern.network.match.TeamPlayerListPacket}.
 */
@Deprecated(forRemoval = false)
public class TeamPlayerListPacket extends com.cdp.codpattern.network.match.TeamPlayerListPacket {
    public TeamPlayerListPacket(String roomKey, int rosterVersion, Map<String, List<PlayerInfo>> teamPlayers) {
        super(roomKey, rosterVersion, teamPlayers);
    }

    public TeamPlayerListPacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public static TeamPlayerListPacket decode(FriendlyByteBuf buf) {
        return new TeamPlayerListPacket(buf);
    }
}
