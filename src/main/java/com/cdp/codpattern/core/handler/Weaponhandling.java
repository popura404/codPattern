package com.cdp.codpattern.core.handler;

import com.cdp.codpattern.config.BackPackConfig.BackpackConfigManager;
import com.cdp.codpattern.config.BackPackConfig.BackpackConfig;
import com.cdp.codpattern.config.WeaponFilterConfig.WeaponFilterConfig;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.item.IGun;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

import java.util.Map;

public class Weaponhandling {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 分发物品逻辑处理
     */
    public static void distributeBackpackItems(ServerPlayer player) {
        // 加载武器筛选配置
        //WeaponFilterConfig filterConfig = WeaponFilterConfig.LoadorCreate();
        WeaponFilterConfig filterConfig = WeaponFilterConfig.getWeaponFilterConfig();
        if (filterConfig == null) {
            LOGGER.warn("WeaponFilterConfig is null. Skip distributing items for player {}", player.getGameProfile().getName());
            return;
        }

        //不是喜欢的冒险生存创造玩家，直接不发
        if (player.isSpectator()) return;

        //不是喜欢的带标签的玩家，直接不发
        if (filterConfig.isDistributeToTaggedPlayersOnly() && !player.getTags().contains("cdpplayer")) return;

        String uuid = player.getUUID().toString();
        BackpackConfig.PlayerBackpackData playerData =
                BackpackConfigManager.getConfig().getOrCreatePlayerData(uuid);

        int selectedId = playerData.getSelectedBackpack();
        BackpackConfig.Backpack backpack = playerData.getBackpacks_MAP().get(selectedId);

        if (backpack != null) {
            player.getInventory().clearContent();

            for (Map.Entry<String, BackpackConfig.Backpack.ItemData> entry :
                    backpack.getItem_MAP().entrySet()) {

                String weaponType = entry.getKey();
                BackpackConfig.Backpack.ItemData itemData = entry.getValue();
                if (itemData == null) {
                    LOGGER.warn("ItemData is null for weaponType={} player={}", weaponType, player.getGameProfile().getName());
                    continue;
                }

                ResourceLocation itemId;
                try {
                    itemId = new ResourceLocation(itemData.getItem());
                } catch (Exception e) {
                    LOGGER.warn("Invalid item id {} for player {}", itemData.getItem(), player.getGameProfile().getName(), e);
                    continue;
                }
                Item item = BuiltInRegistries.ITEM.get(itemId);

                if (item != Items.AIR) {
                    ItemStack stack = new ItemStack(item, itemData.getCount());

                    if (itemData.getNbt() != null && !itemData.getNbt().isEmpty()) {
                        try {
                            //处理物品nbt
                            stack.setTag(TagParser.parseTag(itemData.getNbt()));
                        } catch (Exception e) {
                            LOGGER.warn("Failed to parse NBT for item {} (player={})", itemId, player.getGameProfile().getName(), e);
                        }
                    }

                    if (stack.getItem() instanceof IGun iGun) {
                        int BackpackDummyAmmoAmount = iGun.getCurrentAmmoCount(stack) * filterConfig.getAmmunitionPerMagazineMultiple();
                        iGun.setDummyAmmoAmount(stack , BackpackDummyAmmoAmount);
                    }

                    if ("primary".equals(weaponType)) {
                        player.getInventory().setItem(0, stack);
                    } else if ("secondary".equals(weaponType)) {
                        player.getInventory().setItem(1, stack);
                    } else if (("tactical".equals(weaponType) || "lethal".equals(weaponType))
                            && filterConfig.isThrowablesEnabled()) {
                        if ("tactical".equals(weaponType)) {
                            player.getInventory().setItem(2, stack);
                        } else {
                            player.getInventory().setItem(3, stack);
                        }
                    }
                }
            }

            player.sendSystemMessage(Component.literal(
                    "§6已装备背包: §e" + backpack.getName()
            ));
        }
    }
}
