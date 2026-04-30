package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.app.match.model.ModeCapability;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.model.RoomSummaryMetric;
import com.cdp.codpattern.app.match.model.RoomSummarySnapshot;
import com.cdp.codpattern.network.match.RoomSyncInfo;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @deprecated Use {@link com.cdp.codpattern.network.match.RoomListSyncPacket}.
 */
@Deprecated(forRemoval = false)
public class RoomListSyncPacket extends com.cdp.codpattern.network.match.RoomListSyncPacket {
    public RoomListSyncPacket(long snapshotVersion, Map<RoomId, ? extends RoomSyncInfo> rooms) {
        super(snapshotVersion, rooms);
    }

    public RoomListSyncPacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public static RoomListSyncPacket decode(FriendlyByteBuf buf) {
        return new RoomListSyncPacket(buf);
    }

    /**
     * @deprecated Use {@link com.cdp.codpattern.network.match.RoomListSyncPacket.RoomInfo}.
     */
    @Deprecated(forRemoval = false)
    public static class RoomInfo extends com.cdp.codpattern.network.match.RoomListSyncPacket.RoomInfo {
        public RoomInfo(String state, int playerCount, int maxPlayers, Map<String, Integer> teamPlayerCounts,
                Map<String, Integer> teamScores, int remainingTimeTicks, boolean hasMatchEndTeleportPoint) {
            super(state,
                    playerCount,
                    maxPlayers,
                    teamPlayerCounts,
                    teamScores,
                    remainingTimeTicks,
                    hasMatchEndTeleportPoint);
        }

        public RoomInfo(String state, int playerCount, int maxPlayers, Map<String, Integer> teamPlayerCounts,
                Map<String, Integer> teamScores, int remainingTimeTicks, boolean hasMatchEndTeleportPoint,
                List<RoomSummaryMetric> metrics) {
            super(state,
                    playerCount,
                    maxPlayers,
                    teamPlayerCounts,
                    teamScores,
                    remainingTimeTicks,
                    hasMatchEndTeleportPoint,
                    metrics);
        }

        public RoomInfo(String state, int playerCount, int maxPlayers, Map<String, Integer> teamPlayerCounts,
                Map<String, Integer> teamScores, int remainingTimeTicks, boolean hasMatchEndTeleportPoint,
                List<RoomSummaryMetric> metrics, Set<ModeCapability> capabilities) {
            super(state,
                    playerCount,
                    maxPlayers,
                    teamPlayerCounts,
                    teamScores,
                    remainingTimeTicks,
                    hasMatchEndTeleportPoint,
                    metrics,
                    capabilities);
        }

        public static RoomInfo fromSnapshot(RoomSummarySnapshot snapshot, boolean hasMatchEndTeleportPoint) {
            return fromSyncInfo(RoomSyncInfo.fromSnapshot(snapshot, hasMatchEndTeleportPoint));
        }

        public static RoomInfo fromSyncInfo(RoomSyncInfo info) {
            return new RoomInfo(
                    info.state,
                    info.playerCount,
                    info.maxPlayers,
                    info.teamPlayerCounts,
                    info.teamScores,
                    info.remainingTimeTicks,
                    info.hasMatchEndTeleportPoint,
                    info.metrics,
                    info.capabilities);
        }

        public static RoomInfo read(FriendlyByteBuf buf) {
            com.cdp.codpattern.network.match.RoomListSyncPacket.RoomInfo info =
                    com.cdp.codpattern.network.match.RoomListSyncPacket.RoomInfo.read(buf);
            return fromSyncInfo(info);
        }
    }
}
