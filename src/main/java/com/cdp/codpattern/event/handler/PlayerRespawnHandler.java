package com.cdp.codpattern.event.handler;

import com.cdp.codpattern.config.configmanager.BackpackConfigManager;
import com.cdp.codpattern.config.server.BagSelectConfig;
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

    /**
     * 玩家重生事件 - 服务端分发背包物品
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        // 仅在服务端执行
        if (!event.getEntity().level().isClientSide) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            distributeBackpackItems(player);
        }
    }

    /**
     * 玩家首次加入 - 初始化背包
     */
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        // 仅在服务端执行
        if (!event.getEntity().level().isClientSide) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            String uuid = player.getUUID().toString();

            // 获取或创建玩家数据（会自动创建3个默认背包）
            BagSelectConfig.PlayerBackpackData playerData =
                    BackpackConfigManager.getConfig().getOrCreatePlayerData(uuid);

            // 检查是否是新玩家（刚创建的3个默认背包）
            if (playerData.getBackpackCount() == 3) {
                boolean isDefault = true;
                for (int i = 1; i <= 3; i++) {
                    BagSelectConfig.Backpack backpack = playerData.getBackpacks_MAP().get(i);
                    if (backpack == null || !backpack.getName().equals("自定义背包" + i)) {
                        isDefault = false;
                        break;
                    }
                }

                if (isDefault) {
                    //player.sendSystemMessage(Component.literal("§a欢迎！已为您创建3个默认背包"));
                    // 保存配置
                    BackpackConfigManager.save();
                }
            }
        }
    }

    /**
     * 分发背包物品的核心逻辑
     */
    private static void distributeBackpackItems(ServerPlayer player) {
        String uuid = player.getUUID().toString();

        // 获取背包配置
        BagSelectConfig.PlayerBackpackData playerData = BackpackConfigManager.getConfig().getOrCreatePlayerData(uuid);

        // 获取选择的背包
        int selectedId = playerData.getSelectedBackpack();
        BagSelectConfig.Backpack backpack = playerData.getBackpacks_MAP().get(selectedId);

        if (backpack != null) {
            // 清空玩家物品栏
            player.getInventory().clearContent();

            // 分发背包中的物品
            for (Map.Entry<String, BagSelectConfig.Backpack.ItemData> entry : backpack.getItem_MAP().entrySet()) {

                String weaponType = entry.getKey();
                BagSelectConfig.Backpack.ItemData itemData = entry.getValue();

                // 解析物品
                ResourceLocation itemId = new ResourceLocation(itemData.getItem());
                Item item = BuiltInRegistries.ITEM.get(itemId);

                if (item != Items.AIR) {
                    ItemStack stack = new ItemStack(item, itemData.getCount());

                    // 应用NBT数据（如果有）
                    if (itemData.getNbt() != null && !itemData.getNbt().isEmpty()) {
                        try {
                            stack.setTag(TagParser.parseTag(itemData.getNbt()));
                        } catch (Exception e) {
                            // NBT解析失败，忽略
                        }
                    }

                    // 主武器放槽位0，副武器放槽位1
                    if ("primary".equals(weaponType)) {
                        player.getInventory().setItem(0, stack);
                    } else if ("secondary".equals(weaponType)) {
                        player.getInventory().setItem(1, stack);
                    }
                }
            }

            // 发送提示
            player.sendSystemMessage(Component.literal(
                    "§6已装备背包: §e" + backpack.getName()
            ));
        }
    }
}
