package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import com.cdp.codpattern.network.handler.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
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
    private final String roomKey;
    private final int rosterVersion;
    private final Map<String, List<PlayerInfo>> teamPlayers;

    public TeamPlayerListPacket(String roomKey, int rosterVersion, Map<String, List<PlayerInfo>> teamPlayers) {
        this.roomKey = roomKey;
        this.rosterVersion = rosterVersion;
        this.teamPlayers = teamPlayers;
    }

    public TeamPlayerListPacket(FriendlyByteBuf buf) {
        this.roomKey = buf.readUtf();
        this.rosterVersion = buf.readInt();
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
        buf.writeUtf(roomKey);
        buf.writeInt(rosterVersion);
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
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientPacketHandler.handleTeamPlayerList(roomKey, rosterVersion, teamPlayers));
        });
        ctx.get().setPacketHandled(true);
    }
}
