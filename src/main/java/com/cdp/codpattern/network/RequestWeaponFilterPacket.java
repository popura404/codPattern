package com.cdp.codpattern.network;

import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfig;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfigRepository;
import com.cdp.codpattern.config.path.ConfigPath;
import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.network.NetworkEvent;

import java.nio.file.Path;
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
                Path path = ConfigPath.SERVER_FILTER.getPath(player.server);
                // 服务端加载配置并发送给客户端
                WeaponFilterConfig config = WeaponFilterConfigRepository.loadOrCreate(path);
                ModNetworkChannel.sendToPlayer(new SyncWeaponFilterPacket(config), player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
