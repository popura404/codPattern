package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;
import java.util.Map;

/**
 * @deprecated Use {@link com.cdp.codpattern.network.match.RoomPreviewRosterPacket}.
 */
@Deprecated(forRemoval = false)
public class RoomPreviewRosterPacket extends com.cdp.codpattern.network.match.RoomPreviewRosterPacket {
    public RoomPreviewRosterPacket(String roomKey, int rosterVersion, Map<String, List<PlayerInfo>> teamPlayers) {
        super(roomKey, rosterVersion, teamPlayers);
    }

    public RoomPreviewRosterPacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public static RoomPreviewRosterPacket decode(FriendlyByteBuf buf) {
        return new RoomPreviewRosterPacket(buf);
    }
}
