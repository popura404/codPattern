package com.cdp.codpattern.network;

import com.cdp.codpattern.config.server.WeaponFilterConfig;
import com.cdp.codpattern.network.handler.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RequestWeaponFilterPacket {

    public RequestWeaponFilterPacket() {}

    public RequestWeaponFilterPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public static RequestWeaponFilterPacket decode(FriendlyByteBuf buf) {
        return new RequestWeaponFilterPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                // 服务端加载配置并发送给客户端
                WeaponFilterConfig config = WeaponFilterConfig.load();
                PacketHandler.sendToPlayer(new SyncWeaponFilterPacket(config), player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
