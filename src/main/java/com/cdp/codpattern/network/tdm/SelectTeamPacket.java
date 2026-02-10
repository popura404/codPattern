package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.fpsmatch.map.CodTdmMap;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C→S: 选择队伍数据包
 */
public class SelectTeamPacket {
    private final String teamName;

    public SelectTeamPacket(String teamName) {
        this.teamName = teamName;
    }

    public SelectTeamPacket(FriendlyByteBuf buf) {
        this.teamName = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(teamName);
    }

    public static SelectTeamPacket decode(FriendlyByteBuf buf) {
        return new SelectTeamPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                FPSMCore.getInstance().getMapByPlayer(player).ifPresent(map -> {
                    if (map instanceof CodTdmMap tdmMap) {
                        // 先离开当前队伍，再加入新队伍
                        map.leave(player);
                        map.join(teamName, player);
                    }
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
