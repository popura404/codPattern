package com.cdp.codpattern.app.backpack.service;

import com.cdp.codpattern.core.throwable.ThrowableInventoryService;
import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.config.backpack.BackpackConfigRepository;
import com.cdp.codpattern.config.path.ConfigPath;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfig;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfigRepository;
import com.cdp.codpattern.compat.fpsmatch.FpsMatchGatewayProvider;
import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import com.mojang.logging.LogUtils;
import com.cdp.codpattern.config.backpack.BackpackNameHelper;
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

public class BackpackDistributor {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 分发物品逻辑处理
     */
    public static void distributeBackpackItems(ServerPlayer player) {
        distributeBackpackItems(player, false);
    }

    /** 强制发放 */
    public static void forceDistributeBackpackItems(ServerPlayer player) {
        distributeBackpackItems(player, true);
    }

    private static void distributeBackpackItems(ServerPlayer player, boolean forceDistribute) {
        if (player.server == null) {
            return;
        }

        // 加载武器筛选配置
        WeaponFilterConfig filterConfig =
                WeaponFilterConfigRepository.loadOrCreate(ConfigPath.SERVER_FILTER.getPath(player.server));
        if (filterConfig == null) {
            LOGGER.warn("WeaponFilterConfig is null. Skip distributing items for player {}", player.getGameProfile().getName());
            return;
        }
        int ammoMultiple = resolveAmmoMultiple(filterConfig);

        //不是喜欢的冒险生存创造玩家，直接不发
        if (player.isSpectator()) return;

        // 非强制需在房间内
        if (!forceDistribute && !FpsMatchGatewayProvider.gateway().isInMatch(player.getUUID())) return;

        String uuid = player.getUUID().toString();
        BackpackConfig.PlayerBackpackData playerData =
                BackpackConfigRepository.loadOrCreatePlayer(uuid, ConfigPath.SERVERBACKPACK.getPath(player.server));
        BackpackConfig.Backpack backpack = resolveBackpack(playerData);
        if (backpack == null) {
            LOGGER.warn("No available backpack for player {}", player.getGameProfile().getName());
            return;
        }

        player.getInventory().clearContent();
        ThrowableInventoryService.clearRuntime(player);

        for (Map.Entry<String, BackpackConfig.Backpack.ItemData> entry :
                backpack.getItem_MAP().entrySet()) {

            String weaponType = entry.getKey();
            BackpackConfig.Backpack.ItemData itemData = entry.getValue();
            if (itemData == null) {
                LOGGER.warn("ItemData is null for weaponType={} player={}", weaponType, player.getGameProfile().getName());
                continue;
            }

            ResourceLocation itemId = ResourceLocation.tryParse(itemData.getItem());
            if (itemId == null) {
                LOGGER.warn("Invalid item id {} for player {}", itemData.getItem(), player.getGameProfile().getName());
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(itemId);
            if (item == null || item == Items.AIR) {
                LOGGER.warn("Unknown item id {} for player {}", itemId, player.getGameProfile().getName());
                continue;
            }

            ItemStack stack = new ItemStack(item, Math.max(1, itemData.getCount()));

            if (itemData.getNbt() != null && !itemData.getNbt().isEmpty()) {
                try {
                    //处理物品nbt
                    stack.setTag(TagParser.parseTag(itemData.getNbt()));
                } catch (Exception e) {
                    LOGGER.warn("Failed to parse NBT for item {} (player={})", itemId, player.getGameProfile().getName(), e);
                }
            }

            if (BackpackNamespaceFilter.isBlocked(filterConfig, stack, itemId)) {
                continue;
            }

            if (TaczGatewayProvider.gateway().isGun(stack)) {
                TaczGatewayProvider.gateway().configureGunAmmo(stack, ammoMultiple);
            }

            if ("primary".equals(weaponType)) {
                player.getInventory().setItem(0, stack);
            } else if ("secondary".equals(weaponType)) {
                player.getInventory().setItem(1, stack);
            } else if (("tactical".equals(weaponType) || "lethal".equals(weaponType))
                    && filterConfig.isThrowablesEnabled()) {
                if ("tactical".equals(weaponType)) {
                    ThrowableInventoryService.seedThrowableSlot(player, ThrowableInventoryService.SLOT_ONE, stack);
                } else {
                    ThrowableInventoryService.seedThrowableSlot(player, ThrowableInventoryService.SLOT_TWO, stack);
                }
            }
        }

        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(player.getInventory());
        ThrowableInventoryService.sync(player);

        player.sendSystemMessage(Component.translatable(
                "message.codpattern.game.equipped_backpack",
                BackpackNameHelper.displayNameComponent(backpack, playerData.getSelectedBackpack())));
    }

    private static BackpackConfig.Backpack resolveBackpack(BackpackConfig.PlayerBackpackData playerData) {
        if (playerData == null || playerData.getBackpacks_MAP() == null || playerData.getBackpacks_MAP().isEmpty()) {
            return null;
        }
        BackpackConfig.Backpack selected = playerData.getBackpacks_MAP().get(playerData.getSelectedBackpack());
        if (selected != null) {
            return selected;
        }
        Integer fallbackId = playerData.getBackpacks_MAP().keySet().stream().min(Integer::compareTo).orElse(null);
        if (fallbackId == null) {
            return null;
        }
        playerData.setSelectedBackpack(fallbackId);
        return playerData.getBackpacks_MAP().get(fallbackId);
    }

    private static int resolveAmmoMultiple(WeaponFilterConfig filterConfig) {
        if (filterConfig == null || filterConfig.getAmmunitionPerMagazineMultiple() == null) {
            return 6;
        }
        return Math.max(0, filterConfig.getAmmunitionPerMagazineMultiple());
    }
}
