package com.cdp.codpattern.event;

import com.cdp.codpattern.config.BackPackConfig.BackpackConfigManager;
import com.cdp.codpattern.config.WeaponFilterConfig.WeaponFilterConfig;
import com.cdp.codpattern.core.ConfigPath.ConfigPath;
import com.cdp.codpattern.network.SyncBackpackConfigPacket;
import com.cdp.codpattern.network.SyncWeaponFilterPacket;
import com.cdp.codpattern.network.handler.PacketHandler;
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

        Player player = event.getEntity();
        MinecraftServer server = player.getServer();

        Path backpackPath = ConfigPath.SERVERBACKPACK.getPath(server);
        Path filterPath = ConfigPath.SERVERFLITER.getPath(server);

        var fliterconfig = WeaponFilterConfig.LoadorCreate(filterPath);
        var playerBackpackData = BackpackConfigManager.LoadorCreatePlayer(player.getStringUUID(), backpackPath);

        // 同步到客户端
        PacketHandler.sendToPlayer(new SyncBackpackConfigPacket(playerBackpackData), (ServerPlayer) player);
        PacketHandler.sendToPlayer(new SyncWeaponFilterPacket(fliterconfig), (ServerPlayer) player);
    }
}
