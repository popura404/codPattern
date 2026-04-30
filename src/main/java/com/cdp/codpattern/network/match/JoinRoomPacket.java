package com.cdp.codpattern.network.match;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.app.match.service.ModeRoomInteractionService;
import com.cdp.codpattern.fpsmatch.room.CodTdmRoomManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class JoinRoomPacket {
    private final String roomKey;
    private final String teamName;

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
            ModeRoomInteractionService.JoinResult result = ModeRoomInteractionService.joinRoom(player, roomKey, teamName);
            sendResult(player, result.success(), result.roomKey(), result.code(), result.message());
        });
        ctx.get().setPacketHandled(true);
    }

    private static void sendResult(ServerPlayer player, boolean success, String roomKey, String code, String message) {
        CodTdmRoomManager.getInstance().markRoomListDirty();
        ModNetworkChannel.sendToPlayer(new JoinRoomResultPacket(success, roomKey, code, message), player);
    }
}
