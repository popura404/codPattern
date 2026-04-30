package com.cdp.codpattern.network.match;

import com.cdp.codpattern.app.match.model.MetricDisplay;
import com.cdp.codpattern.app.match.model.ModeCapability;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.model.RoomSummaryMetric;
import com.cdp.codpattern.app.match.model.RoomSummarySnapshot;
import com.cdp.codpattern.network.handler.ClientPacketBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class RoomListSyncPacket {
    private final long snapshotVersion;
    private final Map<RoomId, RoomInfo> rooms;

    public RoomListSyncPacket(long snapshotVersion, Map<RoomId, ? extends RoomSyncInfo> rooms) {
        this.snapshotVersion = snapshotVersion;
        this.rooms = toRoomInfos(rooms);
    }

    public RoomListSyncPacket(FriendlyByteBuf buf) {
        this.snapshotVersion = buf.readLong();
        int size = buf.readInt();
        this.rooms = new HashMap<>();
        for (int i = 0; i < size; i++) {
            RoomId roomId = RoomId.read(buf);
            RoomInfo info = RoomInfo.read(buf);
            rooms.put(roomId, info);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(snapshotVersion);
        buf.writeInt(rooms.size());
        for (Map.Entry<RoomId, RoomInfo> entry : rooms.entrySet()) {
            entry.getKey().write(buf);
            entry.getValue().write(buf);
        }
    }

    public static RoomListSyncPacket decode(FriendlyByteBuf buf) {
        return new RoomListSyncPacket(buf);
    }

    private static Map<RoomId, RoomInfo> toRoomInfos(Map<RoomId, ? extends RoomSyncInfo> rooms) {
        if (rooms == null || rooms.isEmpty()) {
            return Map.of();
        }
        Map<RoomId, RoomInfo> converted = new HashMap<>();
        for (Map.Entry<RoomId, ? extends RoomSyncInfo> entry : rooms.entrySet()) {
            converted.put(entry.getKey(), RoomInfo.fromSyncInfo(entry.getValue()));
        }
        return converted;
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientPacketBridge.roomListSync(snapshotVersion, rooms));
        ctx.get().setPacketHandled(true);
    }

    public static class RoomInfo extends RoomSyncInfo {
        public RoomInfo(String state, int playerCount, int maxPlayers, Map<String, Integer> teamPlayerCounts,
                Map<String, Integer> teamScores, int remainingTimeTicks, boolean hasMatchEndTeleportPoint) {
            this(state,
                    playerCount,
                    maxPlayers,
                    teamPlayerCounts,
                    teamScores,
                    remainingTimeTicks,
                    hasMatchEndTeleportPoint,
                    List.of(),
                    Set.of());
        }

        public RoomInfo(String state, int playerCount, int maxPlayers, Map<String, Integer> teamPlayerCounts,
                Map<String, Integer> teamScores, int remainingTimeTicks, boolean hasMatchEndTeleportPoint,
                List<RoomSummaryMetric> metrics) {
            this(state,
                    playerCount,
                    maxPlayers,
                    teamPlayerCounts,
                    teamScores,
                    remainingTimeTicks,
                    hasMatchEndTeleportPoint,
                    metrics,
                    Set.of());
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

        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(state);
            buf.writeInt(playerCount);
            buf.writeInt(maxPlayers);
            buf.writeInt(teamPlayerCounts.size());
            for (Map.Entry<String, Integer> entry : teamPlayerCounts.entrySet()) {
                buf.writeUtf(entry.getKey());
                buf.writeInt(entry.getValue());
            }
            buf.writeInt(teamScores.size());
            for (Map.Entry<String, Integer> entry : teamScores.entrySet()) {
                buf.writeUtf(entry.getKey());
                buf.writeInt(entry.getValue());
            }
            buf.writeInt(remainingTimeTicks);
            buf.writeBoolean(hasMatchEndTeleportPoint);
            buf.writeInt(capabilities.size());
            for (ModeCapability capability : capabilities) {
                buf.writeUtf(capability.name());
            }
            buf.writeInt(metrics.size());
            for (RoomSummaryMetric metric : metrics) {
                buf.writeUtf(metric.key());
                buf.writeUtf(metric.translationKey());
                buf.writeInt(metric.value());
                buf.writeUtf(metric.display().name());
            }
        }

        public static RoomInfo read(FriendlyByteBuf buf) {
            String state = buf.readUtf();
            int playerCount = buf.readInt();
            int maxPlayers = buf.readInt();
            int teamCount = buf.readInt();
            Map<String, Integer> teamPlayerCounts = new HashMap<>();
            for (int i = 0; i < teamCount; i++) {
                teamPlayerCounts.put(buf.readUtf(), buf.readInt());
            }
            int scoreTeamCount = buf.readInt();
            Map<String, Integer> teamScores = new HashMap<>();
            for (int i = 0; i < scoreTeamCount; i++) {
                teamScores.put(buf.readUtf(), buf.readInt());
            }
            int remainingTimeTicks = buf.readInt();
            boolean hasMatchEndTeleportPoint = buf.readBoolean();
            int capabilityCount = buf.readInt();
            Set<ModeCapability> capabilities = new HashSet<>();
            for (int i = 0; i < capabilityCount; i++) {
                readCapability(buf).ifPresent(capabilities::add);
            }
            int metricCount = buf.readInt();
            List<RoomSummaryMetric> metrics = new ArrayList<>();
            for (int i = 0; i < metricCount; i++) {
                metrics.add(new RoomSummaryMetric(
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readInt(),
                        readMetricDisplay(buf)));
            }
            return new RoomInfo(state, playerCount, maxPlayers, teamPlayerCounts, teamScores, remainingTimeTicks,
                    hasMatchEndTeleportPoint, metrics, capabilities);
        }

        private static java.util.Optional<ModeCapability> readCapability(FriendlyByteBuf buf) {
            String rawCapability = buf.readUtf();
            try {
                return java.util.Optional.of(ModeCapability.valueOf(rawCapability));
            } catch (IllegalArgumentException ignored) {
                return java.util.Optional.empty();
            }
        }

        private static MetricDisplay readMetricDisplay(FriendlyByteBuf buf) {
            String rawDisplay = buf.readUtf();
            try {
                return MetricDisplay.valueOf(rawDisplay);
            } catch (IllegalArgumentException ignored) {
                return MetricDisplay.NUMBER;
            }
        }
    }
}
