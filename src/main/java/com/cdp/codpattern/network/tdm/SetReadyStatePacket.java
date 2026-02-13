package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.app.tdm.service.TdmRoomInteractionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C→S: 设置 READY 状态
 */
public class SetReadyStatePacket {
    private final boolean ready;

    public SetReadyStatePacket(boolean ready) {
        this.ready = ready;
    }

    public SetReadyStatePacket(FriendlyByteBuf buf) {
        this.ready = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(ready);
    }

    public static SetReadyStatePacket decode(FriendlyByteBuf buf) {
        return new SetReadyStatePacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            player.sendSystemMessage(Component.literal(TdmRoomInteractionService.setReadyState(player, ready)));
        });
        ctx.get().setPacketHandled(true);
    }
}
