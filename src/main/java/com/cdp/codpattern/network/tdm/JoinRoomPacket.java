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
            if (player == null) {
                return;
            }
            TdmRoomInteractionService.JoinResult result = TdmRoomInteractionService.joinRoom(player, mapName, teamName);
            sendResult(player, result.success(), result.mapName(), result.code(), result.message());
        });
        ctx.get().setPacketHandled(true);
    }

    private static void sendResult(ServerPlayer player, boolean success, String mapName, String code, String message) {
        CodTdmRoomManager.getInstance().markRoomListDirty();
        JoinRoomResultPacket packet = new JoinRoomResultPacket(success, mapName, code, message);
        com.cdp.codpattern.adapter.forge.network.ModNetworkChannel.sendToPlayer(packet, player);
    }
}
