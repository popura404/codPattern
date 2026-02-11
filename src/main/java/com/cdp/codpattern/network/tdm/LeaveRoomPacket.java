package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.fpsmatch.map.CodTdmMap;
import com.cdp.codpattern.fpsmatch.room.CodTdmRoomManager;
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
            if (player == null) {
                return;
            }
            FPSMCore.getInstance().getMapByPlayerWithSpec(player).ifPresentOrElse(map -> {
                String roomName = map.mapName;
                if (map instanceof CodTdmMap tdmMap) {
                    tdmMap.leaveRoom(player);
                } else {
                    map.leave(player);
                }
                CodTdmRoomManager.getInstance().markRoomListDirty();
                com.cdp.codpattern.network.handler.PacketHandler.sendToPlayer(
                        new LeaveRoomResultPacket(true, roomName, "OK", ""), player);
            }, () -> com.cdp.codpattern.network.handler.PacketHandler.sendToPlayer(
                    new LeaveRoomResultPacket(false, "", "NOT_IN_ROOM", "当前不在房间内"), player));
        });
        ctx.get().setPacketHandled(true);
    }
}
