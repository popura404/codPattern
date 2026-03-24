package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.app.tdm.service.TdmRoomInteractionService;
import com.cdp.codpattern.fpsmatch.room.CodTdmRoomManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C→S: 加入房间数据包
 */
public class JoinRoomPacket {
    private final String roomKey;
    private final String teamName; // 可选，null表示自动分配

    public JoinRoomPacket(String roomKey, String teamName) {
        this.roomKey = roomKey;
        this.teamName = teamName;
    }

    public JoinRoomPacket(FriendlyByteBuf buf) {
        this.roomKey = buf.readUtf();
        this.teamName = buf.readBoolean() ? buf.readUtf() : null;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(roomKey);
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
            if (player == null) {
                return;
            }
            TdmRoomInteractionService.JoinResult result = TdmRoomInteractionService.joinRoom(player, roomKey, teamName);
            sendResult(player, result.success(), result.roomKey(), result.code(), result.message());
        });
        ctx.get().setPacketHandled(true);
    }

    private static void sendResult(ServerPlayer player, boolean success, String roomKey, String code, String message) {
        CodTdmRoomManager.getInstance().markRoomListDirty();
        JoinRoomResultPacket packet = new JoinRoomResultPacket(success, roomKey, code, message);
        com.cdp.codpattern.adapter.forge.network.ModNetworkChannel.sendToPlayer(packet, player);
    }
}
