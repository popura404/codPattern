package com.cdp.codpattern.network.match;

import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * Mode-neutral player roster delta used after packet decoding.
 */
public class RoomRosterDelta {
    public static final int CHANGE_READY = 1;
    public static final int CHANGE_STATS = 1 << 1;
    public static final int CHANGE_LIFE = 1 << 2;
    public static final int CHANGE_INVINCIBLE = 1 << 3;
    public static final int CHANGE_PING_BUCKET = 1 << 4;
    public static final int CHANGE_STREAK = 1 << 5;

    private final UUID playerId;
    private final String teamName;
    private final int changedMask;
    private final PlayerInfo snapshot;

    public RoomRosterDelta(UUID playerId, String teamName, int changedMask, PlayerInfo snapshot) {
        this.playerId = playerId;
        this.teamName = teamName;
        this.changedMask = changedMask;
        this.snapshot = snapshot;
    }

    public UUID playerId() {
        return playerId;
    }

    public String teamName() {
        return teamName;
    }

    public int changedMask() {
        return changedMask;
    }

    public PlayerInfo snapshot() {
        return snapshot;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(playerId);
        buf.writeUtf(teamName == null ? "" : teamName);
        buf.writeInt(changedMask);
        snapshot.write(buf);
    }

    public static RoomRosterDelta read(FriendlyByteBuf buf) {
        UUID playerId = buf.readUUID();
        String teamName = buf.readUtf();
        int changedMask = buf.readInt();
        PlayerInfo snapshot = PlayerInfo.read(buf);
        return new RoomRosterDelta(playerId, teamName, changedMask, snapshot);
    }
}
