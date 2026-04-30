package com.cdp.codpattern.network.match;

import com.cdp.codpattern.app.match.service.ModeRoomInteractionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class VoteEndPacket {
    public VoteEndPacket() {
    }

    public VoteEndPacket(FriendlyByteBuf buf) {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public static VoteEndPacket decode(FriendlyByteBuf buf) {
        return new VoteEndPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                ModeRoomInteractionService.initiateEndVote(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
