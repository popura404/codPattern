package com.cdp.codpattern.app.backpack.service;

import com.cdp.codpattern.compat.fpsmatch.FpsMatchGatewayProvider;
import com.cdp.codpattern.compat.lrtactical.LrTacticalGatewayProvider;
import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import com.cdp.codpattern.config.ConfigFailureFeedback;
import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.config.backpack.BackpackConfigRepository;
import com.cdp.codpattern.config.backpack.BackpackItemStackFactory;
import com.cdp.codpattern.config.backpack.BackpackNameHelper;
import com.cdp.codpattern.config.backpack.BackpackSlot;
import com.cdp.codpattern.config.path.ConfigPath;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfig;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfigRepository;
import com.cdp.codpattern.core.throwable.ThrowableInventoryService;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

public class BackpackDistributor {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 分发物品逻辑处理
     */
    public static void distributeBackpackItems(ServerPlayer player) {
        distributeBackpackItems(player, false, true);
    }

    /** 静默分发物品，不向玩家发送背包装备提示。 */
    public static void distributeBackpackItemsSilently(ServerPlayer player) {
        distributeBackpackItems(player, false, false);
    }

    /** 强制发放 */
    public static void forceDistributeBackpackItems(ServerPlayer player) {
        distributeBackpackItems(player, true, true);
    }

    /** 强制静默发放，不向玩家发送背包装备提示。 */
    public static void forceDistributeBackpackItemsSilently(ServerPlayer player) {
        distributeBackpackItems(player, true, false);
    }

    private static void distributeBackpackItems(ServerPlayer player, boolean forceDistribute, boolean notifyPlayer) {
        if (player.server == null) {
            return;
        }

        // 加载武器筛选配置
        WeaponFilterConfig filterConfig;
        try {
            filterConfig = WeaponFilterConfigRepository.loadOrCreate(ConfigPath.SERVER_FILTER.getPath(player.server));
        } catch (WeaponFilterConfigRepository.RepositoryException failure) {
            ConfigFailureFeedback.report(player, failure);
            return;
        }
        if (filterConfig == null) {
            LOGGER.warn("WeaponFilterConfig is null. Skip distributing items for player {}", player.getGameProfile().getName());
            return;
        }
        //不是喜欢的冒险生存创造玩家，直接不发
        if (player.isSpectator()) return;

        // 非强制需在房间内
        if (!forceDistribute && !FpsMatchGatewayProvider.gateway().isInMatch(player.getUUID())) return;

        String uuid = player.getUUID().toString();
        BackpackConfig.PlayerBackpackData playerData;
        try {
            playerData = BackpackConfigRepository.loadOrCreatePlayer(uuid, ConfigPath.SERVERBACKPACK.getPath(player.server));
        } catch (BackpackConfigRepository.RepositoryException failure) {
            ConfigFailureFeedback.report(player, failure);
            return;
        }
        BackpackConfig.Backpack backpack = resolveBackpack(playerData);
        if (backpack == null) {
            LOGGER.warn("No available backpack for player {}", player.getGameProfile().getName());
            return;
        }

        player.getInventory().clearContent();
        ThrowableInventoryService.clearRuntime(player);
        giveSelectedItems(player, backpack, filterConfig);

        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(player.getInventory());
        ThrowableInventoryService.sync(player);

        if (notifyPlayer) {
            player.sendSystemMessage(Component.translatable(
                    "message.codpattern.game.equipped_backpack",
                    BackpackNameHelper.displayNameComponent(backpack, playerData.getSelectedBackpack())));
        }
    }

    public static void giveSelectedItems(ServerPlayer player, BackpackConfig.Backpack backpack,
            WeaponFilterConfig filterConfig) {
        boolean throwablesEnabled = (filterConfig == null || filterConfig.isThrowablesEnabled())
                && LrTacticalGatewayProvider.gateway().isLoaded();
        int ammoMultiple = resolveAmmoMultiple(filterConfig);
        for (BackpackSlot slot : BackpackSlot.values()) {
            if (slot.isEquipmentSlot() && !throwablesEnabled) {
                continue;
            }

            BackpackConfig.Backpack.ItemData itemData = backpack.getItem(slot);
            ItemStack stack = BackpackItemStackFactory.create(itemData);
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation itemId = ResourceLocation.tryParse(itemData.getItem());
            if (BackpackNamespaceFilter.isBlocked(filterConfig, stack, itemId)) {
                continue;
            }

            switch (slot) {
                case PRIMARY, SECONDARY -> {
                    if (TaczGatewayProvider.gateway().isValidGun(stack)) {
                        if (BackpackAttachmentFilter.removeBlockedInstalledAttachments(filterConfig, stack)) {
                            LOGGER.warn("Removed blocked attachment from backpack item player={} slot={} item={}",
                                    player.getGameProfile().getName(), slot.key(), itemId);
                        }
                        TaczGatewayProvider.gateway().configureGunAmmo(stack, ammoMultiple);
                        player.getInventory().setItem(slot == BackpackSlot.PRIMARY ? 0 : 1, stack);
                    }
                }
                case MELEE -> {
                    if (LrTacticalGatewayProvider.gateway().isMeleeWeapon(stack)) {
                        player.getInventory().setItem(2, stack);
                    }
                }
                case ITEM1 -> ThrowableInventoryService.seedThrowableSlot(player, ThrowableInventoryService.SLOT_ONE, stack);
                case ITEM2 -> ThrowableInventoryService.seedThrowableSlot(player, ThrowableInventoryService.SLOT_TWO, stack);
            }
        }
    }

    public static BackpackConfig.Backpack resolveBackpack(BackpackConfig.PlayerBackpackData playerData) {
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
