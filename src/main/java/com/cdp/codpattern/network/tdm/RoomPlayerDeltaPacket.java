package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import com.cdp.codpattern.network.handler.ClientPacketBridge;
import com.cdp.codpattern.network.match.RoomRosterDelta;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * S→C: 当前房间玩家名单增量更新。
 */
public class RoomPlayerDeltaPacket {
    public static final int CHANGE_READY = RoomRosterDelta.CHANGE_READY;
    public static final int CHANGE_STATS = RoomRosterDelta.CHANGE_STATS;
    public static final int CHANGE_LIFE = RoomRosterDelta.CHANGE_LIFE;
    public static final int CHANGE_INVINCIBLE = RoomRosterDelta.CHANGE_INVINCIBLE;
    public static final int CHANGE_PING_BUCKET = RoomRosterDelta.CHANGE_PING_BUCKET;
    public static final int CHANGE_STREAK = RoomRosterDelta.CHANGE_STREAK;

    private final String roomKey;
    private final int rosterVersion;
    private final List<RoomRosterDelta> updates;

    public RoomPlayerDeltaPacket(String roomKey, int rosterVersion, List<? extends RoomRosterDelta> updates) {
        this.roomKey = roomKey;
        this.rosterVersion = rosterVersion;
        this.updates = updates == null ? List.of() : new ArrayList<>(updates);
    }

    public RoomPlayerDeltaPacket(FriendlyByteBuf buf) {
        this.roomKey = buf.readUtf();
        this.rosterVersion = buf.readInt();
        int size = Math.max(0, buf.readInt());
        List<RoomRosterDelta> decoded = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            decoded.add(RoomRosterDelta.read(buf));
        }
        this.updates = decoded;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(roomKey);
        buf.writeInt(rosterVersion);
        buf.writeInt(updates.size());
        for (RoomRosterDelta update : updates) {
            update.write(buf);
        }
    }

    public static RoomPlayerDeltaPacket decode(FriendlyByteBuf buf) {
        return new RoomPlayerDeltaPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientPacketBridge.roomPlayerDelta(roomKey, rosterVersion, updates);
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * Legacy nested DTO retained for older callers. New code should use {@link RoomRosterDelta}.
     */
    public static class PlayerDelta extends RoomRosterDelta {
        public PlayerDelta(UUID playerId, String teamName, int changedMask, PlayerInfo snapshot) {
            super(playerId, teamName, changedMask, snapshot);
        }

        public static PlayerDelta read(FriendlyByteBuf buf) {
            RoomRosterDelta delta = RoomRosterDelta.read(buf);
            return new PlayerDelta(delta.playerId(), delta.teamName(), delta.changedMask(), delta.snapshot());
        }
    }
}
