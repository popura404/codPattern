package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.app.tdm.service.TdmRoomInteractionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C→S: 选择队伍数据包
 */
public class SelectTeamPacket {
    private final String roomKey;
    private final String teamName;

    public SelectTeamPacket(String roomKey, String teamName) {
        this.roomKey = roomKey;
        this.teamName = teamName;
    }

    public SelectTeamPacket(FriendlyByteBuf buf) {
        this.roomKey = buf.readUtf();
        this.teamName = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(roomKey);
        buf.writeUtf(teamName);
    }

    public static SelectTeamPacket decode(FriendlyByteBuf buf) {
        return new SelectTeamPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                TdmRoomInteractionService.selectTeamInRoom(player, roomKey, teamName);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
