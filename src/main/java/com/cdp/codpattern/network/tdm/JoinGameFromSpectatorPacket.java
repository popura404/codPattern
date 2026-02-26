package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.app.tdm.service.TdmRoomInteractionService;
import com.cdp.codpattern.fpsmatch.room.CodTdmRoomManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C→S: 旁观状态下申请加入进行中对局
 */
public class JoinGameFromSpectatorPacket {
    private final String mapName;
    private final long requestId;

    public JoinGameFromSpectatorPacket(String mapName, long requestId) {
        this.mapName = mapName == null ? "" : mapName;
        this.requestId = requestId;
    }

    public JoinGameFromSpectatorPacket(FriendlyByteBuf buf) {
        this.mapName = buf.readUtf();
        this.requestId = buf.readLong();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(mapName);
        buf.writeLong(requestId);
    }

    public static JoinGameFromSpectatorPacket decode(FriendlyByteBuf buf) {
        return new JoinGameFromSpectatorPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            TdmRoomInteractionService.JoinResult result =
                    TdmRoomInteractionService.joinGameFromSpectator(player, mapName);
            if (result.success()) {
                CodTdmRoomManager.getInstance().markRoomListDirty();
            }
            ModNetworkChannel.sendToPlayer(
                    new JoinGameResultPacket(
                            result.success(),
                            requestId,
                            result.mapName(),
                            result.code(),
                            result.message()),
                    player);
        });
        ctx.get().setPacketHandled(true);
    }
}
