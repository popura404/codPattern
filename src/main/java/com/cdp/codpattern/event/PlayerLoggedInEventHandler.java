package com.cdp.codpattern.event;

import com.cdp.codpattern.config.backpack.BackpackConfigRepository;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfigRepository;
import com.cdp.codpattern.config.path.ConfigPath;
import com.cdp.codpattern.network.SyncBackpackConfigPacket;
import com.cdp.codpattern.network.SyncWeaponFilterPacket;
import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Path;


@Mod.EventBusSubscriber(modid = "codpattern")
public class PlayerLoggedInEventHandler {

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity().level().isClientSide){
            return;
        }

        Player player = event.getEntity();
        MinecraftServer server = player.getServer();

        Path backpackPath = ConfigPath.SERVERBACKPACK.getPath(server);
        Path filterPath = ConfigPath.SERVER_FILTER.getPath(server);

        var fliterconfig = WeaponFilterConfigRepository.loadOrCreate(filterPath);
        var playerBackpackData = BackpackConfigRepository.loadOrCreatePlayer(player.getStringUUID(), backpackPath);

        // 同步到客户端
        ModNetworkChannel.sendToPlayer(new SyncBackpackConfigPacket(playerBackpackData), (ServerPlayer) player);
        ModNetworkChannel.sendToPlayer(new SyncWeaponFilterPacket(fliterconfig), (ServerPlayer) player);
    }
}
