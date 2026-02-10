package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.fpsmatch.map.CodTdmMap;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C→S: 加入房间数据包
 */
public class JoinRoomPacket {
    private final String mapName;
    private final String teamName; // 可选，null表示自动分配

    public JoinRoomPacket(String mapName, String teamName) {
        this.mapName = mapName;
        this.teamName = teamName;
    }

    public JoinRoomPacket(FriendlyByteBuf buf) {
        this.mapName = buf.readUtf();
        this.teamName = buf.readBoolean() ? buf.readUtf() : null;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(mapName);
        buf.writeBoolean(teamName != null);
        if (teamName != null) {
            buf.writeUtf(teamName);
        }
    }

    public static JoinRoomPacket decode(FriendlyByteBuf buf) {
        return new JoinRoomPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                // 直接从 FPSMCore 获取地图
                FPSMCore.getInstance().getMapByName(mapName).ifPresent(baseMap -> {
                    if (baseMap instanceof CodTdmMap map) {
                        // 如果指定了队伍就加入，否则让 BaseMap 自动分配
                        if (teamName != null) {
                            map.join(teamName, player);
                        } else {
                            map.join(player);
                        }
                        // 同步数据到客户端
                        map.syncToClient();
                    }
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
