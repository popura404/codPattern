package com.cdp.codpattern.config.zombies;

import java.util.HashMap;
import java.util.Map;

public class ZombiesBackpackConfig {
    private Map<String, PlayerZombiesBackpackData> playerData = new HashMap<>();

    public Map<String, PlayerZombiesBackpackData> getPlayerData() {
        if (playerData == null) {
            playerData = new HashMap<>();
        }
        return playerData;
    }

    public void setPlayerData(Map<String, PlayerZombiesBackpackData> playerData) {
        this.playerData = playerData == null ? new HashMap<>() : playerData;
        normalize();
    }

    public PlayerZombiesBackpackData getOrCreatePlayerData(String uuid) {
        String key = uuid == null ? "" : uuid;
        return getPlayerData().computeIfAbsent(key, ignored -> new PlayerZombiesBackpackData());
    }

    public void normalize() {
        if (playerData == null) {
            playerData = new HashMap<>();
        }
        playerData.values().forEach(data -> {
            if (data != null) {
                data.normalize();
            }
        });
    }

    public static WeaponData defaultWeapon() {
        return new WeaponData(
                "tacz:modern_kinetic_gun",
                1,
                "{GunId:\"tacz:glock_17\",GunCurrentAmmoCount:17,GunFireMode:\"SEMI\",HasBulletInBarrel:1}",
                null
        );
    }

    public static class PlayerZombiesBackpackData {
        private WeaponData weapon = defaultWeapon();

        public WeaponData getWeapon() {
            if (weapon == null) {
                weapon = defaultWeapon();
            }
            return weapon;
        }

        public void setWeapon(WeaponData weapon) {
            this.weapon = weapon == null ? defaultWeapon() : weapon;
        }

        private void normalize() {
            setWeapon(weapon);
            weapon.normalize();
        }
    }

    public static class WeaponData {
        private String item;
        private int count;
        private String nbt;
        private String attachmentPreset;

        public WeaponData() {
        }

        public WeaponData(String item, int count, String nbt, String attachmentPreset) {
            this.item = item;
            this.count = count;
            this.nbt = nbt;
            this.attachmentPreset = attachmentPreset;
        }

        public String getItem() {
            return item;
        }

        public void setItem(String item) {
            this.item = item;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public String getNbt() {
            return nbt;
        }

        public void setNbt(String nbt) {
            this.nbt = nbt;
        }

        public String getAttachmentPreset() {
            return attachmentPreset;
        }

        public void setAttachmentPreset(String attachmentPreset) {
            this.attachmentPreset = attachmentPreset;
        }

        void normalize() {
            if (item == null || item.trim().isEmpty()) {
                item = "tacz:modern_kinetic_gun";
            }
            if (count <= 0) {
                count = 1;
            }
            if (nbt == null) {
                nbt = "";
            }
        }
    }
}
