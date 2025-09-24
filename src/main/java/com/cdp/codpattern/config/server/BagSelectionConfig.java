package com.cdp.codpattern.config.server;

import java.util.*;

public class BagSelectionConfig {

    private Map<String, PlayerBackpackData> playerData = new HashMap<>();

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
         * 获取下一个可用的ID（1-10范围内）
         * @return 第一个可用的空缺ID，如果1-10都已占用则返回11
         */
        public int getNextAvailableId() {
            // 从1开始遍历到10
            for (int i = 1; i <= 10; i++) {
                // key不存在 = 找到空缺位置
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
         * 设置物品，限制最多2个（主武器和副武器）
         */
        public void setItem_MAP(String key, ItemData itemData){
            if(this.item_MAP.size() >= 2) {
                // 已达到物品数量上限
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

            // 创建三个默认背包
            //tacz:modern_kinetic_gun{HasBulletInBarrel: 1b, GunFireMode: "SEMI", GunId: "tacz:ai_awp", AttachmentSCOPE: {id: "tacz:attachment", Count: 1b, tag: {AttachmentId: "tacz:scope_elcan_4x"}}, GunCurrentAmmoCount: 5}

            // 背包1 - 突击背包
            Backpack backpack1 = new Backpack("自定义背包1");
            backpack1.setItem_MAP("primary",
                    new Backpack.ItemData("tacz:modern_kinetic_gun", 1, "{GunId:\"tacz:hk_g3\",GunCurrentAmmoCount:20,GunFireMode: \"AUTO\",HasBulletInBarrel:1}"));
            backpack1.setItem_MAP("secondary",
                    new Backpack.ItemData("tacz:modern_kinetic_gun", 1, "{GunId:\"tacz:glock_17\",GunCurrentAmmoCount:17,GunFireMode: \"SEMI\",HasBulletInBarrel:1}"));
            newData.getBackpacks_MAP().put(1, backpack1);

            // 背包2 - 防御背包
            Backpack backpack2 = new Backpack("自定义背包2");
            backpack2.setItem_MAP("primary",
                    new Backpack.ItemData("tacz:modern_kinetic_gun", 1, "{GunId:\"tacz:ak47\",GunCurrentAmmoCount:30,GunFireMode: \"AUTO\",HasBulletInBarrel:1}"));
            backpack2.setItem_MAP("secondary",
                    new Backpack.ItemData("tacz:modern_kinetic_gun", 1, "{GunId:\"tacz:p320\",GunCurrentAmmoCount:12,GunFireMode: \"SEMI\",HasBulletInBarrel:1}"));
            newData.getBackpacks_MAP().put(2, backpack2);

            // 背包3 - 探索背包
            Backpack backpack3 = new Backpack("自定义背包3");
            backpack3.setItem_MAP("primary",
                    new Backpack.ItemData("tacz:modern_kinetic_gun", 1, "{GunId:\"tacz:m4a1\",GunCurrentAmmoCount:30,GunFireMode: \"AUTO\",HasBulletInBarrel:1}"));
            backpack3.setItem_MAP("secondary",
                    new Backpack.ItemData("tacz:modern_kinetic_gun", 1, "{GunId:\"tacz:deagle\",GunCurrentAmmoCount:7,GunFireMode: \"SEMI\",HasBulletInBarrel:1}"));
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
}
