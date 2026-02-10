package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.client.gui.screen.TdmRoomScreen;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * S→C: 同步队伍玩家列表数据包
 */
public class TeamPlayerListPacket {
    private final String mapName;
    private final Map<String, List<PlayerInfo>> teamPlayers;

    public TeamPlayerListPacket(String mapName, Map<String, List<PlayerInfo>> teamPlayers) {
        this.mapName = mapName;
        this.teamPlayers = teamPlayers;
    }

    public TeamPlayerListPacket(FriendlyByteBuf buf) {
        this.mapName = buf.readUtf();
        int teamCount = buf.readInt();
        this.teamPlayers = new HashMap<>();
        for (int i = 0; i < teamCount; i++) {
            String teamName = buf.readUtf();
            int playerCount = buf.readInt();
            List<PlayerInfo> players = new ArrayList<>();
            for (int j = 0; j < playerCount; j++) {
                players.add(PlayerInfo.read(buf));
            }
            teamPlayers.put(teamName, players);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(mapName);
        buf.writeInt(teamPlayers.size());
        for (Map.Entry<String, List<PlayerInfo>> entry : teamPlayers.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeInt(entry.getValue().size());
            for (PlayerInfo player : entry.getValue()) {
                player.write(buf);
            }
        }
    }

    public static TeamPlayerListPacket decode(FriendlyByteBuf buf) {
        return new TeamPlayerListPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft.getInstance().execute(() -> {
                Screen screen = Minecraft.getInstance().screen;
                if (screen instanceof TdmRoomScreen tdmScreen) {
                    tdmScreen.updatePlayerList(mapName, teamPlayers);
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
