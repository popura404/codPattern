package com.cdp.codpattern.event.handler;

import com.cdp.codpattern.config.configmanager.BackpackConfigManager;
import com.cdp.codpattern.config.server.BackpackSelectionConfig;
import com.cdp.codpattern.config.server.WeaponFilterConfig;
import com.cdp.codpattern.network.SyncBackpackConfigPacket;
import com.cdp.codpattern.network.handler.PacketHandler;
import com.tacz.guns.api.item.IGun;
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

    /**
     * 分发物品逻辑厨力
     */
    private static void distributeBackpackItems(ServerPlayer player) {
        // 加载武器筛选配置
        WeaponFilterConfig filterConfig = WeaponFilterConfig.load();

        //不是喜欢的冒险生存创造玩家，直接不发
        if (player.isSpectator()) return;

        //不是喜欢的带标签的玩家，直接不发
        if (filterConfig.isDistributeToTaggedPlayersOnly()) {
            if (!player.getTags().contains("cdpplayer")) {
                return;
            }
        }

        String uuid = player.getUUID().toString();
        BackpackSelectionConfig.PlayerBackpackData playerData =
                BackpackConfigManager.getConfig().getOrCreatePlayerData(uuid);

        int selectedId = playerData.getSelectedBackpack();
        BackpackSelectionConfig.Backpack backpack = playerData.getBackpacks_MAP().get(selectedId);

        if (backpack != null) {
            player.getInventory().clearContent();

            for (Map.Entry<String, BackpackSelectionConfig.Backpack.ItemData> entry :
                    backpack.getItem_MAP().entrySet()) {

                String weaponType = entry.getKey();
                BackpackSelectionConfig.Backpack.ItemData itemData = entry.getValue();

                ResourceLocation itemId = new ResourceLocation(itemData.getItem());
                Item item = BuiltInRegistries.ITEM.get(itemId);

                if (item != Items.AIR) {
                    ItemStack stack = new ItemStack(item, itemData.getCount());

                    if (itemData.getNbt() != null && !itemData.getNbt().isEmpty()) {
                        try {
                            //处理物品nbt
                            stack.setTag(TagParser.parseTag(itemData.getNbt()));
                            //分配子弹
                            IGun iGun = (IGun) stack.getItem();
                            int BackpackDummyAmmoAmount = iGun.getCurrentAmmoCount(stack) * filterConfig.getAmmunitionPerMagazineMultiple();
                            iGun.setDummyAmmoAmount(stack , BackpackDummyAmmoAmount);
                        } catch (Exception ignored) {
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

