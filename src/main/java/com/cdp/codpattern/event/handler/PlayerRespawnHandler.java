package com.cdp.codpattern.event.handler;

import com.cdp.codpattern.config.configmanager.BackpackConfigManager;
import com.cdp.codpattern.config.server.BagSelectionConfig;
import com.cdp.codpattern.config.server.WeaponFilterConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

@Mod.EventBusSubscriber(modid = "codpattern")
public class PlayerRespawnHandler {

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!event.getEntity().level().isClientSide) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            distributeBackpackItems(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!event.getEntity().level().isClientSide) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            String uuid = player.getUUID().toString();

            BagSelectionConfig.PlayerBackpackData playerData =
                    BackpackConfigManager.getConfig().getOrCreatePlayerData(uuid);

            if (playerData.getBackpackCount() == 3) {
                boolean isDefault = true;
                for (int i = 1; i <= 3; i++) {
                    BagSelectionConfig.Backpack backpack = playerData.getBackpacks_MAP().get(i);
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

    /**
     * 分发物品逻辑厨力
     * @param player
     */
    private static void distributeBackpackItems(ServerPlayer player) {
        // 加载武器筛选配置
        WeaponFilterConfig filterConfig = WeaponFilterConfig.load();

        // 检查是否仅分发给带标签的玩家
        if (filterConfig.isDistributeToTaggedPlayersOnly()) {
            if (!player.getTags().contains("cdpplayer")) {
                return; // 不分发物品
            }
        }

        String uuid = player.getUUID().toString();
        BagSelectionConfig.PlayerBackpackData playerData =
                BackpackConfigManager.getConfig().getOrCreatePlayerData(uuid);

        int selectedId = playerData.getSelectedBackpack();
        BagSelectionConfig.Backpack backpack = playerData.getBackpacks_MAP().get(selectedId);

        if (backpack != null) {
            player.getInventory().clearContent();

            for (Map.Entry<String, BagSelectionConfig.Backpack.ItemData> entry :
                    backpack.getItem_MAP().entrySet()) {

                String weaponType = entry.getKey();
                BagSelectionConfig.Backpack.ItemData itemData = entry.getValue();

                ResourceLocation itemId = new ResourceLocation(itemData.getItem());
                Item item = BuiltInRegistries.ITEM.get(itemId);

                if (item != Items.AIR) {
                    ItemStack stack = new ItemStack(item, itemData.getCount());

                    if (itemData.getNbt() != null && !itemData.getNbt().isEmpty()) {
                        try {
                            stack.setTag(TagParser.parseTag(itemData.getNbt()));
                        } catch (Exception e) {
                            // NBT解析失败，忽略
                        }
                    }

                    if ("primary".equals(weaponType)) {
                        player.getInventory().setItem(0, stack);
                    } else if ("secondary".equals(weaponType)) {
                        player.getInventory().setItem(1, stack);
                    }
                }
            }

            player.sendSystemMessage(Component.literal(
                    "§6已装备背包: §e" + backpack.getName()
            ));
        }
    }
}

