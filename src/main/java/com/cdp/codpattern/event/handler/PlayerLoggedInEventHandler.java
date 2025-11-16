package com.cdp.codpattern.event.handler;

import com.cdp.codpattern.config.configmanager.BackpackConfigManager;
import com.cdp.codpattern.config.server.BackpackSelectionConfig;
import com.cdp.codpattern.network.SyncBackpackConfigPacket;
import com.cdp.codpattern.network.handler.PacketHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


@Mod.EventBusSubscriber(modid = "codpattern")
public class PlayerLoggedInEventHandler {

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!event.getEntity().level().isClientSide) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            String uuid = player.getUUID().toString();

            BackpackSelectionConfig.PlayerBackpackData playerData =
                    BackpackConfigManager.getConfig().getOrCreatePlayerData(uuid);

            // 同步配置到客户端
            PacketHandler.sendToPlayer(new SyncBackpackConfigPacket(uuid, playerData), player);

            // 检查是否为默认背包配置
            if (playerData.getBackpackCount() == 3) {
                boolean isDefault = true;
                for (int i = 1; i <= 3; i++) {
                    BackpackSelectionConfig.Backpack backpack = playerData.getBackpacks_MAP().get(i);
                    if (backpack == null || !backpack.getName().equals("自定义背包" + i)) {
                        isDefault = false;
                        break;
                    }
                }

                if (isDefault) {
                    BackpackConfigManager.save();
                }
            }
        }
    }
}
