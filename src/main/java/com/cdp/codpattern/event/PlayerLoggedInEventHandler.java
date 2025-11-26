package com.cdp.codpattern.event;

import com.cdp.codpattern.config.BackPackConfig.BackpackConfigManager;
import com.cdp.codpattern.config.BackPackConfig.BackpackConfig;
import com.cdp.codpattern.network.RequestBackpackConfigPacket;
import com.cdp.codpattern.network.RequestWeaponFilterPacket;
import com.cdp.codpattern.network.handler.PacketHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


@Mod.EventBusSubscriber(modid = "codpattern")
public class PlayerLoggedInEventHandler {

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {

    }
}
