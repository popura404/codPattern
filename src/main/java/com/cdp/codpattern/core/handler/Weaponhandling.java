package com.cdp.codpattern.core.handler;

import com.cdp.codpattern.config.BackPackConfig.BackpackConfigManager;
import com.cdp.codpattern.config.BackPackConfig.BackpackConfig;
import com.cdp.codpattern.config.WeaponFilterConfig.WeaponFilterConfig;
import com.cdp.codpattern.fpsmatch.room.CodTdmRoomManager;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.util.AttachmentDataUtils;
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
import java.util.Optional;

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
        int ammoMultiple = resolveAmmoMultiple(filterConfig);

        //不是喜欢的冒险生存创造玩家，直接不发
        if (player.isSpectator()) return;

        // 未加入房间系统的玩家不发放
        if (CodTdmRoomManager.getInstance().getPlayerMap(player.getUUID()).isEmpty()) return;

        String uuid = player.getUUID().toString();
        BackpackConfig.PlayerBackpackData playerData =
                BackpackConfigManager.getConfig().getOrCreatePlayerData(uuid);
        BackpackConfig.Backpack backpack = resolveBackpack(playerData);
        if (backpack == null) {
            LOGGER.warn("No available backpack for player {}", player.getGameProfile().getName());
            return;
        }

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

            if (stack.getItem() instanceof IGun iGun) {
                configureGunAmmo(stack, iGun, ammoMultiple);
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

        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(player.getInventory());

        player.sendSystemMessage(Component.literal(
                "§6已装备背包: §e" + backpack.getName()
        ));
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

    public static void configureGunAmmo(ItemStack stack, IGun iGun, int ammoMultiple) {
        Optional<CommonGunIndex> gunIndex = TimelessAPI.getCommonGunIndex(iGun.getGunId(stack));
        int currentAmmo = Math.max(0, iGun.getCurrentAmmoCount(stack));
        int magazineAmmo = gunIndex
                .map(index -> Math.max(0, AttachmentDataUtils.getAmmoCountWithAttachment(stack, index.getGunData())))
                .orElse(currentAmmo);
        if (magazineAmmo <= 0) {
            magazineAmmo = currentAmmo;
        }
        int normalizedMagazineAmmo = Math.max(1, magazineAmmo);
        iGun.setCurrentAmmoCount(stack, normalizedMagazineAmmo);

        // 按枪机类型处理上膛：开膛待击不上膛，其他类型保证膛内至少一发
        Bolt boltType = gunIndex.map(index -> index.getGunData().getBolt()).orElse(null);
        if (boltType == Bolt.OPEN_BOLT) {
            iGun.setBulletInBarrel(stack, false);
        } else {
            iGun.setBulletInBarrel(stack, true);
        }

        int safeMultiple = Math.max(0, ammoMultiple);
        int reserveAmmo = Math.max(0, normalizedMagazineAmmo * safeMultiple);
        if (reserveAmmo <= 0 && safeMultiple > 0) {
            reserveAmmo = safeMultiple;
        }
        iGun.setMaxDummyAmmoAmount(stack, reserveAmmo);
        iGun.setDummyAmmoAmount(stack, reserveAmmo);
    }

    private static int resolveAmmoMultiple(WeaponFilterConfig filterConfig) {
        if (filterConfig == null || filterConfig.getAmmunitionPerMagazineMultiple() == null) {
            return 6;
        }
        return Math.max(0, filterConfig.getAmmunitionPerMagazineMultiple());
    }
}
