package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import com.cdp.codpattern.network.handler.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * S→C: 当前房间玩家名单增量更新。
 */
public class RoomPlayerDeltaPacket {
    public static final int CHANGE_READY = 1;
    public static final int CHANGE_STATS = 1 << 1;
    public static final int CHANGE_LIFE = 1 << 2;
    public static final int CHANGE_INVINCIBLE = 1 << 3;
    public static final int CHANGE_PING_BUCKET = 1 << 4;
    public static final int CHANGE_STREAK = 1 << 5;

    private final String roomKey;
    private final int rosterVersion;
    private final List<PlayerDelta> updates;

    public RoomPlayerDeltaPacket(String roomKey, int rosterVersion, List<PlayerDelta> updates) {
        this.roomKey = roomKey;
        this.rosterVersion = rosterVersion;
        this.updates = updates == null ? List.of() : List.copyOf(updates);
    }

    public RoomPlayerDeltaPacket(FriendlyByteBuf buf) {
        this.roomKey = buf.readUtf();
        this.rosterVersion = buf.readInt();
        int size = Math.max(0, buf.readInt());
        List<PlayerDelta> decoded = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            decoded.add(PlayerDelta.read(buf));
        }
        this.updates = decoded;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(roomKey);
        buf.writeInt(rosterVersion);
        buf.writeInt(updates.size());
        for (PlayerDelta update : updates) {
            update.write(buf);
        }
    }

    public static RoomPlayerDeltaPacket decode(FriendlyByteBuf buf) {
        return new RoomPlayerDeltaPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientPacketHandler.handleRoomPlayerDelta(roomKey, rosterVersion, updates));
        });
        ctx.get().setPacketHandled(true);
    }

    public record PlayerDelta(UUID playerId, String teamName, int changedMask, PlayerInfo snapshot) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUUID(playerId);
            buf.writeUtf(teamName == null ? "" : teamName);
            buf.writeInt(changedMask);
            snapshot.write(buf);
        }

        public static PlayerDelta read(FriendlyByteBuf buf) {
            UUID playerId = buf.readUUID();
            String teamName = buf.readUtf();
            int changedMask = buf.readInt();
            PlayerInfo snapshot = PlayerInfo.read(buf);
            return new PlayerDelta(playerId, teamName, changedMask, snapshot);
        }
    }
}
