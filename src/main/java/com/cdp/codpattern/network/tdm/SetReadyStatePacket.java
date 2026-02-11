package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.fpsmatch.room.CodTdmRoomManager;
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
            CodTdmRoomManager.getInstance().getPlayerMap(player.getUUID()).ifPresentOrElse(map -> {
                if (map.setPlayerReady(player, ready)) {
                    player.sendSystemMessage(Component.literal(ready ? "§a已准备" : "§e已取消准备"));
                } else {
                    player.sendSystemMessage(Component.literal("§c当前阶段不可切换准备状态"));
                }
            }, () -> player.sendSystemMessage(Component.literal("§c未加入 TDM 房间")));
        });
        ctx.get().setPacketHandled(true);
    }
}
