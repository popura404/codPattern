package com.cdp.codpattern.config.backpack;

import java.util.*;

public class BackpackConfig {

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

        /**
         * 获取添加背包可用ID
         * @return 第一个可用的空缺ID，如果1-10都已占用则返回11
         */
        public int getNextAvailableId() {
            // 从1开始遍历到10
            for (int i = 1; i <= 10; i++) {
                // 找不到key说明背包是空的
                if (!backpacks_MAP.containsKey(i)) {
                    return i;  // 返回这个空缺的位置
                }
            }
            // 如果1-10都已占用，返回0
            return 0;
        }
    }

    public static class Backpack {
        private String name;
        private Map<String,ItemData> item_MAP = new HashMap<>();

        public Backpack(String name){
            this.name = name;
        }

        public void setName(String name) { this.name = name;}

        /**
         * 设置物品，限制最多4个（主武器、副武器、战术投掷物、杀伤投掷物）
         */
        public void setItem_MAP(String key, ItemData itemData){
            if(this.item_MAP.size() >= 4 && !this.item_MAP.containsKey(key)) {
                return;
            }
            this.item_MAP.put(key, itemData);
        }

        public static class ItemData {
            private String item;
            private int count;
            private String nbt; // 可选的NBT数据

            public ItemData(String item, int count) {
                this.item = item;
                this.count = count;
            }

            public ItemData(String item, int count, String nbt) {
                this.item = item;
                this.count = count;
                this.nbt = nbt;
            }

            // Getters and setters
            public String getItem() { return item; }
            public int getCount() { return count; }
            public String getNbt() { return nbt; }
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

            // 背包1
            Backpack backpack1 = new Backpack("");
            backpack1.setItem_MAP("primary", itemDataP1);
            backpack1.setItem_MAP("secondary", itemDataS1);
            backpack1.setItem_MAP("tactical", itemDataTactical);
            backpack1.setItem_MAP("lethal", itemDataLethal);
            newData.getBackpacks_MAP().put(1, backpack1);

            // 背包2
            Backpack backpack2 = new Backpack("");
            backpack2.setItem_MAP("primary", itemDataP2);
            backpack2.setItem_MAP("secondary", itemDataS2);
            backpack2.setItem_MAP("tactical", itemDataTactical);
            backpack2.setItem_MAP("lethal", itemDataLethal);
            newData.getBackpacks_MAP().put(2, backpack2);

            // 背包3
            Backpack backpack3 = new Backpack("");
            backpack3.setItem_MAP("primary", itemDataP3);
            backpack3.setItem_MAP("secondary", itemDataS3);
            backpack3.setItem_MAP("tactical", itemDataTactical);
            backpack3.setItem_MAP("lethal", itemDataLethal);
            newData.getBackpacks_MAP().put(3, backpack3);

            // 默认选择背包1
            newData.setSelectedBackpack(1);

            return newData;
        });
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
        return itemDataTactical;
    }

    public static Backpack.ItemData getItemDataLethal() {
        return itemDataLethal;
    }
}
