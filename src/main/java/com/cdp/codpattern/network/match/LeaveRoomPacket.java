package com.cdp.codpattern.network.match;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.app.match.service.ModeRoomInteractionService;
import com.cdp.codpattern.fpsmatch.room.CodTdmRoomManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class LeaveRoomPacket {
    public LeaveRoomPacket() {
    }

    public LeaveRoomPacket(FriendlyByteBuf buf) {
    }

    public void encode(FriendlyByteBuf buf) {
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
            ModeRoomInteractionService.LeaveResult result = ModeRoomInteractionService.leaveRoom(player);
            if (result.success()) {
                CodTdmRoomManager.getInstance().markRoomListDirty();
            }
            ModNetworkChannel.sendToPlayer(
                    new LeaveRoomResultPacket(result.success(), result.roomKey(), result.code(), result.message()),
                    player);
        });
        ctx.get().setPacketHandled(true);
    }
}
