package com.cdp.codpattern.app.tdm.service;

import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.config.backpack.BackpackConfigRepository;
import com.cdp.codpattern.config.path.ConfigPath;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfig;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfigRepository;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public final class KitDistributionService {
    private KitDistributionService() {
    }

    public static void distributePlayerKits(ServerPlayer player) {
        if (player.server == null) {
            return;
        }

        player.getInventory().clearContent();

        BackpackConfig.PlayerBackpackData playerData = BackpackConfigRepository.loadOrCreatePlayer(
                player.getStringUUID(), ConfigPath.SERVERBACKPACK.getPath(player.server));
        if (playerData == null) {
            return;
        }

        BackpackConfig.Backpack backpack = resolveBackpack(playerData);
        WeaponFilterConfig filterConfig = WeaponFilterConfigRepository.loadOrCreate(
                ConfigPath.SERVER_FILTER.getPath(player.server));

        int ammoMultiple = (filterConfig != null && filterConfig.getAmmunitionPerMagazineMultiple() != null)
                ? filterConfig.getAmmunitionPerMagazineMultiple()
                : 6;
        ammoMultiple = Math.max(0, ammoMultiple);
        boolean throwablesEnabled = (filterConfig == null) || filterConfig.isThrowablesEnabled();

        if (backpack != null) {
            giveBackpackItem(player, backpack, "primary", 0, ammoMultiple);
            giveBackpackItem(player, backpack, "secondary", 1, ammoMultiple);
            if (throwablesEnabled) {
                giveBackpackItem(player, backpack, "tactical", 2, 0);
                giveBackpackItem(player, backpack, "lethal", 3, 0);
            }

            player.sendSystemMessage(
                    Component.translatable("message.codpattern.game.equipped_backpack", backpack.getName()));
        }

        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(player.getInventory());
    }

    private static void giveBackpackItem(ServerPlayer player, BackpackConfig.Backpack backpack, String key, int slot,
            int ammoMultiple) {
        BackpackConfig.Backpack.ItemData itemData = backpack.getItem_MAP().get(key);
        if (itemData == null) {
            return;
        }

        ResourceLocation itemId = ResourceLocation.tryParse(itemData.getItem());
        if (itemId == null) {
            return;
        }

        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            return;
        }

        ItemStack stack = new ItemStack(item, Math.max(1, itemData.getCount()));
        if (itemData.getNbt() != null && !itemData.getNbt().isEmpty()) {
            try {
                CompoundTag tag = TagParser.parseTag(itemData.getNbt());
                stack.setTag(tag);
            } catch (Exception ignored) {
            }
        }

        if (TaczGatewayProvider.gateway().isGun(stack)) {
            TaczGatewayProvider.gateway().configureGunAmmo(stack, ammoMultiple);
        }

        player.getInventory().setItem(slot, stack);
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
}
