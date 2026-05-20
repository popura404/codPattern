package com.cdp.codpattern.config.zombies;

import java.util.List;

public final class ZombiesWeaponDefaultsCompatTest {
    public static void main(String[] args) {
        starterWeaponDefaultsToTaczGlock();
        blankStarterWeaponNormalizesToTaczGlock();
        weaponPoolDefaultsUseTaczGunIds();
    }

    private static void starterWeaponDefaultsToTaczGlock() {
        ZombiesBackpackConfig.WeaponData weapon = ZombiesBackpackConfig.defaultWeapon();

        require(
                ZombiesBackpackConfig.DEFAULT_TACZ_GUN_ITEM.equals(weapon.getItem()),
                "starter item should be the TaCZ gun item");
        require(
                weapon.getNbt().contains("GunId:\"tacz:glock_17\""),
                "starter nbt should use tacz:glock_17");
    }

    private static void blankStarterWeaponNormalizesToTaczGlock() {
        ZombiesBackpackConfig.WeaponData weapon = new ZombiesBackpackConfig.WeaponData();
        weapon.normalize();

        require(
                ZombiesBackpackConfig.DEFAULT_TACZ_GUN_ITEM.equals(weapon.getItem()),
                "blank starter item should normalize to the TaCZ gun item");
        require(
                weapon.getNbt().contains("GunId:\"tacz:glock_17\""),
                "blank starter nbt should normalize to tacz:glock_17");
    }

    private static void weaponPoolDefaultsUseTaczGunIds() {
        ZombiesRulesConfig config = new ZombiesRulesConfig();
        List<String> gunIds = config.getWeaponWall()
                .getRarities()
                .stream()
                .flatMap(rarity -> rarity.getGuns().stream())
                .map(ZombiesRulesConfig.GunWeight::getGunId)
                .toList();

        require(
                gunIds.equals(List.of("tacz:glock_17", "tacz:ak47", "tacz:m4a1")),
                "default weapon pool should be exactly three TaCZ gun ids: " + gunIds);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private ZombiesWeaponDefaultsCompatTest() {
    }
}
