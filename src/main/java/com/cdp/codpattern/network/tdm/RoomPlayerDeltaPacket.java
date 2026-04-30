package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import com.cdp.codpattern.network.match.RoomRosterDelta;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;
import java.util.UUID;

/**
 * @deprecated Use {@link com.cdp.codpattern.network.match.RoomPlayerDeltaPacket}.
 */
@Deprecated(forRemoval = false)
public class RoomPlayerDeltaPacket extends com.cdp.codpattern.network.match.RoomPlayerDeltaPacket {
    public RoomPlayerDeltaPacket(String roomKey, int rosterVersion, List<? extends RoomRosterDelta> updates) {
        super(roomKey, rosterVersion, updates);
    }

    public RoomPlayerDeltaPacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public static RoomPlayerDeltaPacket decode(FriendlyByteBuf buf) {
        return new RoomPlayerDeltaPacket(buf);
    }

    /**
     * @deprecated Use {@link RoomRosterDelta}.
     */
    @Deprecated(forRemoval = false)
    public static class PlayerDelta extends com.cdp.codpattern.network.match.RoomPlayerDeltaPacket.PlayerDelta {
        public PlayerDelta(UUID playerId, String teamName, int changedMask, PlayerInfo snapshot) {
            super(playerId, teamName, changedMask, snapshot);
        }

        public static PlayerDelta read(FriendlyByteBuf buf) {
            RoomRosterDelta delta = RoomRosterDelta.read(buf);
            return new PlayerDelta(delta.playerId(), delta.teamName(), delta.changedMask(), delta.snapshot());
        }
    }
}
