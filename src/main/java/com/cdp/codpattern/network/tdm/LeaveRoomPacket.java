package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.fpsmatch.map.CodTdmMap;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C→S: 离开房间数据包
 */
public class LeaveRoomPacket {

    public LeaveRoomPacket() {
    }

    public LeaveRoomPacket(FriendlyByteBuf buf) {
        // 无数据需要读取
    }

    public void encode(FriendlyByteBuf buf) {
        // 无数据需要写入
    }

    public static LeaveRoomPacket decode(FriendlyByteBuf buf) {
        return new LeaveRoomPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                FPSMCore.getInstance().getMapByPlayer(player).ifPresent(map -> {
                    if (map instanceof CodTdmMap tdmMap) {
                        tdmMap.leaveRoom(player);
                    } else {
                        map.leave(player);
                    }
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
