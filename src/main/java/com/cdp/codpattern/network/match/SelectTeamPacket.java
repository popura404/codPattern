package com.cdp.codpattern.network.match;

import com.cdp.codpattern.app.match.service.ModeRoomInteractionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

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
                ModeRoomInteractionService.selectTeamInRoom(player, roomKey, teamName);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
