package com.cdp.codpattern.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import com.cdp.codpattern.config.configmanager.BackpackConfigManager;
import com.cdp.codpattern.network.handler.PacketHandler;

import java.util.function.Supplier;

public class RequestConfigPacket {

    public RequestConfigPacket() {}

    public RequestConfigPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public static RequestConfigPacket decode(FriendlyByteBuf buf) {
        return new RequestConfigPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                String uuid = player.getUUID().toString();
                var playerData = BackpackConfigManager.getConfig().getOrCreatePlayerData(uuid);

                // 同步更新后的配置到客户端
                PacketHandler.sendToPlayer(new SyncBackpackConfigPacket(uuid, playerData), player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
