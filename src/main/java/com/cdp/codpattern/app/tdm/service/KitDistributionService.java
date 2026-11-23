package com.cdp.codpattern.app.tdm.service;

import com.cdp.codpattern.app.backpack.service.BackpackDistributor;
import com.cdp.codpattern.config.ConfigFailureFeedback;
import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.config.backpack.BackpackConfigRepository;
import com.cdp.codpattern.config.backpack.BackpackNameHelper;
import com.cdp.codpattern.config.path.ConfigPath;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfig;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfigRepository;
import com.cdp.codpattern.core.throwable.ThrowableInventoryService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class KitDistributionService {
    private KitDistributionService() {
    }

    public static void distributePlayerKits(ServerPlayer player) {
        distributePlayerKits(player, true);
    }

    public static void distributePlayerKitsSilently(ServerPlayer player) {
        distributePlayerKits(player, false);
    }

    private static void distributePlayerKits(ServerPlayer player, boolean notifyPlayer) {
        if (player.server == null) {
            return;
        }

        BackpackConfig.PlayerBackpackData playerData;
        WeaponFilterConfig filterConfig;
        try {
            playerData = BackpackConfigRepository.loadOrCreatePlayer(
                    player.getStringUUID(), ConfigPath.SERVERBACKPACK.getPath(player.server));
            filterConfig = WeaponFilterConfigRepository.loadOrCreate(
                    ConfigPath.SERVER_FILTER.getPath(player.server));
        } catch (BackpackConfigRepository.RepositoryException | WeaponFilterConfigRepository.RepositoryException failure) {
            ConfigFailureFeedback.report(player, failure);
            return;
        }
        if (playerData == null) {
            return;
        }

        BackpackConfig.Backpack backpack = BackpackDistributor.resolveBackpack(playerData);
        player.getInventory().clearContent();
        ThrowableInventoryService.clearRuntime(player);

        if (backpack != null) {
            BackpackDistributor.giveSelectedItems(player, backpack, filterConfig);

            if (notifyPlayer) {
                player.sendSystemMessage(
                        Component.translatable("message.codpattern.game.equipped_backpack",
                                BackpackNameHelper.displayNameComponent(backpack, playerData.getSelectedBackpack())));
            }
        }

        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(player.getInventory());
        ThrowableInventoryService.sync(player);
    }
}
