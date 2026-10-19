package com.cdp.codpattern.network;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.app.refit.service.RefitActionService;
import com.cdp.codpattern.core.refit.RefitAction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record RefitActionPacket(UUID sessionId, RefitAction action) {
    public static void encode(RefitActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId);
        packet.action.encode(buffer);
    }

    public static RefitActionPacket decode(FriendlyByteBuf buffer) {
        return new RefitActionPacket(buffer.readUUID(), RefitAction.decode(buffer));
    }

    public static void handle(RefitActionPacket packet, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> {
            var player = ctx.getSender();
            if (player != null) {
                ModNetworkChannel.sendToPlayer(RefitActionService.execute(player, packet.sessionId, packet.action), player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
