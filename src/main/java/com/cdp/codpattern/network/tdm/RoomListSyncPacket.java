package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.client.gui.screen.TdmRoomScreen;
import com.cdp.codpattern.client.gui.screen.tdm.TdmRoomData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * S→C: 同步房间列表数据包
 */
public class RoomListSyncPacket {
    private final Map<String, RoomInfo> rooms;

    public RoomListSyncPacket(Map<String, RoomInfo> rooms) {
        this.rooms = rooms;
    }

    public RoomListSyncPacket(FriendlyByteBuf buf) {
        int size = buf.readInt();
        this.rooms = new HashMap<>();
        for (int i = 0; i < size; i++) {
            String mapName = buf.readUtf();
            RoomInfo info = RoomInfo.read(buf);
            rooms.put(mapName, info);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(rooms.size());
        for (Map.Entry<String, RoomInfo> entry : rooms.entrySet()) {
            buf.writeUtf(entry.getKey());
            entry.getValue().write(buf);
        }
    }

    public static RoomListSyncPacket decode(FriendlyByteBuf buf) {
        return new RoomListSyncPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft.getInstance().execute(() -> {
                Screen screen = Minecraft.getInstance().screen;
                if (screen instanceof TdmRoomScreen tdmScreen) {
                    // 转换为 Screen 需要的格式
                    Map<String, TdmRoomData> roomDataMap = new HashMap<>();
                    for (Map.Entry<String, RoomInfo> entry : rooms.entrySet()) {
                        RoomInfo info = entry.getValue();
                        roomDataMap.put(entry.getKey(), new TdmRoomData(
                                entry.getKey(),
                                info.state,
                                info.playerCount,
                                info.maxPlayers,
                                info.teamPlayerCounts,
                                info.teamScores,
                                info.remainingTimeTicks,
                                info.hasMatchEndTeleportPoint));
                    }
                    tdmScreen.updateRoomList(roomDataMap);
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * 房间信息
     */
    public static class RoomInfo {
        public String state;
        public int playerCount;
        public int maxPlayers;
        public Map<String, Integer> teamPlayerCounts;
        public Map<String, Integer> teamScores;
        public int remainingTimeTicks;
        public boolean hasMatchEndTeleportPoint;

        public RoomInfo(String state, int playerCount, int maxPlayers, Map<String, Integer> teamPlayerCounts,
                Map<String, Integer> teamScores, int remainingTimeTicks, boolean hasMatchEndTeleportPoint) {
            this.state = state;
            this.playerCount = playerCount;
            this.maxPlayers = maxPlayers;
            this.teamPlayerCounts = teamPlayerCounts;
            this.teamScores = teamScores;
            this.remainingTimeTicks = remainingTimeTicks;
            this.hasMatchEndTeleportPoint = hasMatchEndTeleportPoint;
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
            return new RoomInfo(state, playerCount, maxPlayers, teamPlayerCounts, teamScores, remainingTimeTicks,
                    hasMatchEndTeleportPoint);
        }
    }
}
