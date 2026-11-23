package com.cdp.codpattern.config.backpack;

import com.cdp.codpattern.compat.lrtactical.LrTacticalGatewayProvider;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class BackpackConfig {
    // This repository serializes static preset fields. The slot limit is not part of a player's save.
    public static final transient int MAX_BACKPACKS = 12;

    private Map<String, PlayerBackpackData> playerData = new HashMap<>();

    // 默认预制背包
    public static BackpackConfig.Backpack.ItemData itemDataP1 = new BackpackConfig.Backpack.ItemData("tacz:modern_kinetic_gun", 1, "{GunId:\"tacz:hk_g3\",GunCurrentAmmoCount:20,GunFireMode: \"AUTO\",HasBulletInBarrel:1}");
    public static BackpackConfig.Backpack.ItemData itemDataS1 = new BackpackConfig.Backpack.ItemData("tacz:modern_kinetic_gun", 1, "{GunId:\"tacz:glock_17\",GunCurrentAmmoCount:17,GunFireMode: \"SEMI\",HasBulletInBarrel:1}");

    public static BackpackConfig.Backpack.ItemData itemDataP2 = new BackpackConfig.Backpack.ItemData("tacz:modern_kinetic_gun", 1, "{GunId:\"tacz:ak47\",GunCurrentAmmoCount:30,GunFireMode: \"AUTO\",HasBulletInBarrel:1}");
    public static BackpackConfig.Backpack.ItemData itemDataS2 = new BackpackConfig.Backpack.ItemData("tacz:modern_kinetic_gun", 1, "{GunId:\"tacz:deagle\",GunCurrentAmmoCount:7,GunFireMode: \"SEMI\",HasBulletInBarrel:1}");

    public static BackpackConfig.Backpack.ItemData itemDataP3 = new BackpackConfig.Backpack.ItemData("tacz:modern_kinetic_gun", 1, "{GunId:\"tacz:m4a1\",GunCurrentAmmoCount:30,GunFireMode: \"AUTO\",HasBulletInBarrel:1}");
    public static BackpackConfig.Backpack.ItemData itemDataS3 = new BackpackConfig.Backpack.ItemData("tacz:modern_kinetic_gun", 1, "{GunId:\"tacz:p320\",GunCurrentAmmoCount:12,GunFireMode: \"SEMI\",HasBulletInBarrel:1}");
    // 添加背包时默认
    public static BackpackConfig.Backpack.ItemData itemDataADDP = new BackpackConfig.Backpack.ItemData("tacz:modern_kinetic_gun", 1, "{GunId:\"tacz:m4a1\",GunCurrentAmmoCount:30,GunFireMode: \"AUTO\",HasBulletInBarrel:1}");
    public static BackpackConfig.Backpack.ItemData itemDataADDS = new BackpackConfig.Backpack.ItemData("tacz:modern_kinetic_gun", 1, "{GunId:\"tacz:p320\",GunCurrentAmmoCount:12,GunFireMode: \"SEMI\",HasBulletInBarrel:1}");
    public static BackpackConfig.Backpack.ItemData itemDataMelee = new BackpackConfig.Backpack.ItemData("lrtactical:melee", 1, "{MeleeWeaponId:\"lrtactical:dagger\"}");
    public static BackpackConfig.Backpack.ItemData itemDataTactical = new BackpackConfig.Backpack.ItemData("lrtactical:throwable", 1, "{ThrowableId:\"lrtactical:m67\"}");
    public static BackpackConfig.Backpack.ItemData itemDataLethal = new BackpackConfig.Backpack.ItemData("lrtactical:throwable", 1, "{ThrowableId:\"lrtactical:smoke_grenade\"}");

    public static class PlayerBackpackData {
        private int selectedBackpack = 1;
        private Map<Integer, Backpack> backpacks_MAP = new HashMap<>();

        // Getters and setters
        public int getSelectedBackpack() { return selectedBackpack; }
        public void setSelectedBackpack(int id) { this.selectedBackpack = id; }
        public Map<Integer, Backpack> getBackpacks_MAP() { return backpacks_MAP; }

        public int getBackpackCount() {
            return backpacks_MAP.size();
        }

        /**
         * 添加自定义背包
         * 自动索取可添加背包ID
         * @param backpack 背包对象
         * @return 是否添加成功
         */
        public boolean addBackpack(int id,Backpack backpack) {
            if (id == 0) return false;
            backpacks_MAP.put(id, backpack);
            return true;
        }

        /** First free loadout ID, or zero when every slot is occupied. */
        public int getNextAvailableId() {
            for (int id = 1; id <= MAX_BACKPACKS; id++) {
                if (!backpacks_MAP.containsKey(id)) return id;
            }
            return 0;
        }
    }

    public static class Backpack {
        private String name;
        private Map<String, ItemData> item_MAP = new LinkedHashMap<>();
        // Original slots of surplus melee weapons in legacy, double-melee loadouts.
        private Map<String, ItemData> displacedLegacyMelee;

        public Backpack(String name){
            this.name = name;
        }

        public void setName(String name) { this.name = name;}

        public void setItem_MAP(String key, ItemData itemData) {
            BackpackSlot slot = BackpackSlot.fromMenuKey(key);
            if (slot != null) {
                setItem(slot, itemData);
            }
        }

        public void setItem(BackpackSlot slot, ItemData itemData) {
            item_MAP.put(slot.key(), itemData);
        }

        public ItemData getItem(BackpackSlot slot) {
            return item_MAP.get(slot.key());
        }

        void migrateSlots() {
            moveLegacyEquipment("tactical", BackpackSlot.ITEM1);
            moveLegacyEquipment("lethal", BackpackSlot.ITEM2);
            moveLegacyMelee(BackpackSlot.SECONDARY);
            moveLegacyMelee(BackpackSlot.PRIMARY);
            for (BackpackSlot slot : BackpackSlot.values()) {
                item_MAP.computeIfAbsent(slot.key(), ignored -> emptyItemData());
            }
        }

        private void moveLegacyEquipment(String oldKey, BackpackSlot slot) {
            ItemData previous = item_MAP.remove(oldKey);
            if (previous != null) {
                item_MAP.putIfAbsent(slot.key(), previous);
            }
        }

        private void moveLegacyMelee(BackpackSlot oldSlot) {
            ItemData previous = getItem(oldSlot);
            if (previous == null || !previous.isLegacyMelee()) {
                return;
            }
            ItemData melee = getItem(BackpackSlot.MELEE);
            if (melee == null || melee.item == null || melee.item.isBlank()) {
                setItem(BackpackSlot.MELEE, previous);
            } else {
                if (displacedLegacyMelee == null) displacedLegacyMelee = new LinkedHashMap<>();
                displacedLegacyMelee.put(oldSlot.key(), previous);
            }
            setItem(oldSlot, emptyItemData());
        }

        public static class ItemData {
            private String item;
            private int count;
            private String nbt; // 可选的NBT数据
            private String attachmentPreset; // 可选的配件预设SNBT

            public ItemData(String item, int count) {
                this.item = item;
                this.count = count;
            }

            public ItemData(String item, int count, String nbt) {
                this(item, count, nbt, null);
            }

            public ItemData(String item, int count, String nbt, String attachmentPreset) {
                this.item = item;
                this.count = count;
                this.nbt = nbt;
                this.attachmentPreset = attachmentPreset;
            }

            // Getters and setters
            public String getItem() { return item; }
            public int getCount() { return count; }
            public String getNbt() { return nbt; }
            public String getAttachmentPreset() { return attachmentPreset; }

            private boolean isLegacyMelee() {
                if ("lrtactical:melee".equals(item) || "lrtactical:flash_shield".equals(item)) {
                    return true;
                }
                // Pack-defined melee base items can have a different registry name.
                if (nbt != null && nbt.contains("MeleeWeaponId")) {
                    try {
                        return TagParser.parseTag(nbt).contains("MeleeWeaponId", Tag.TAG_STRING);
                    } catch (CommandSyntaxException ignored) {
                        // Keep malformed legacy data in its original slot.
                    }
                }
                return false;
            }
        }

        // Getters and setters
        public String getName() { return name; }
        public Map<String,ItemData> getItem_MAP() { return item_MAP; }
    }

    /**
     * 获取或创建玩家数据，新玩家将创建3个默认背包
     */
    public PlayerBackpackData getOrCreatePlayerData(String uuid) {
        return playerData.computeIfAbsent(uuid, k -> {
            PlayerBackpackData newData = new PlayerBackpackData();

            newData.addBackpack(1, createPreset(itemDataP1, itemDataS1));
            newData.addBackpack(2, createPreset(itemDataP2, itemDataS2));
            newData.addBackpack(3, createPreset(itemDataP3, itemDataS3));

            return newData;
        });
    }

    static Backpack createPreset(Backpack.ItemData primary, Backpack.ItemData secondary) {
        Backpack backpack = new Backpack("");
        backpack.setItem(BackpackSlot.PRIMARY, primary);
        backpack.setItem(BackpackSlot.SECONDARY, secondary);
        // Older server presets could put a custom melee in either firearm slot.
        backpack.moveLegacyMelee(BackpackSlot.SECONDARY);
        backpack.moveLegacyMelee(BackpackSlot.PRIMARY);
        if (backpack.getItem(BackpackSlot.MELEE) == null) {
            backpack.setItem(BackpackSlot.MELEE,
                    LrTacticalGatewayProvider.gateway().isLoaded() ? itemDataMelee : emptyItemData());
        }
        backpack.setItem(BackpackSlot.ITEM1, getItemDataTactical());
        backpack.setItem(BackpackSlot.ITEM2, getItemDataLethal());
        return backpack;
    }

    /**
     * 获取所有玩家数据
     */
    public Map<String, PlayerBackpackData> getPlayerData() {
        return playerData;
    }

    public static Backpack.ItemData getItemDataADDS() {
        return itemDataADDS;
    }

    public static Backpack.ItemData getItemDataADDP() {
        return itemDataADDP;
    }

    public static Backpack.ItemData getItemDataTactical() {
        return LrTacticalGatewayProvider.gateway().isLoaded()
                ? itemDataTactical
                : emptyItemData();
    }

    public static Backpack.ItemData getItemDataLethal() {
        return LrTacticalGatewayProvider.gateway().isLoaded()
                ? itemDataLethal
                : emptyItemData();
    }

    private static Backpack.ItemData emptyItemData() {
        return new Backpack.ItemData("", 1, "");
    }
}
