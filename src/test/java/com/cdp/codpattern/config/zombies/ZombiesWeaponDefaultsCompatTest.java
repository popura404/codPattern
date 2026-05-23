package com.cdp.codpattern.config.zombies;

import java.util.List;
import java.util.Map;

public final class ZombiesWeaponDefaultsCompatTest {
    public static void main(String[] args) {
        starterWeaponDefaultsToTaczGlock();
        blankStarterWeaponNormalizesToTaczGlock();
        weaponPoolDefaultsUseTaczGunIds();
        weaponRulesDefaultAmmoUsesScaledReserve();
        armorDefaultsUseRequestedDamageReductions();
        armorValidatorRejectsOutOfRangeReduction();
        ultimateMachineDefaultsUseServerconfigLevelDamage();
        ultimateMachineValidatorRejectsIncompleteLevels();
    }

    private static void starterWeaponDefaultsToTaczGlock() {
        ZombiesRulesConfig.StarterWeapon weapon = new ZombiesRulesConfig().getStarterWeapon();

        require(
                ZombiesRulesConfig.DEFAULT_STARTER_GUN_ITEM.equals(weapon.getItem()),
                "starter item should be the TaCZ gun item");
        require(
                weapon.getNbt().contains("GunId:\"tacz:glock_17\""),
                "starter nbt should use tacz:glock_17");
    }

    private static void blankStarterWeaponNormalizesToTaczGlock() {
        ZombiesRulesConfig.StarterWeapon weapon = new ZombiesRulesConfig.StarterWeapon();
        weapon.setItem("");
        weapon.setCount(0);
        weapon.setNbt("");
        ZombiesRulesConfig config = new ZombiesRulesConfig();
        config.setStarterWeapon(weapon);
        config.normalize();
        ZombiesRulesConfig.StarterWeapon normalizedWeapon = config.getStarterWeapon();

        require(
                ZombiesRulesConfig.DEFAULT_STARTER_GUN_ITEM.equals(normalizedWeapon.getItem()),
                "blank starter item should normalize to the TaCZ gun item");
        require(
                normalizedWeapon.getNbt().contains("GunId:\"tacz:glock_17\""),
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

    private static void weaponRulesDefaultAmmoUsesScaledReserve() {
        ZombiesRulesConfig config = new ZombiesRulesConfig();

        require(
                config.getWeaponRules().getStarterWeaponAmmunitionPerMagazineMultiple() == 10,
                "starter weapon reserve ammo multiple should default to floor(7 * 3 / 2)");
        require(
                config.getWeaponRules().getWeaponPoolAmmunitionPerMagazineMultiple() == 10,
                "weapon pool reserve ammo multiple should default to floor(7 * 3 / 2)");

        config.getWeaponRules().setStarterWeaponAmmunitionPerMagazineMultiple(-1);
        config.getWeaponRules().setWeaponPoolAmmunitionPerMagazineMultiple(-1);
        config.normalize();

        require(
                config.getWeaponRules().getStarterWeaponAmmunitionPerMagazineMultiple() == 10,
                "invalid starter weapon reserve ammo multiple should normalize to scaled default");
        require(
                config.getWeaponRules().getWeaponPoolAmmunitionPerMagazineMultiple() == 10,
                "invalid weapon pool reserve ammo multiple should normalize to scaled default");
    }

    private static void armorDefaultsUseRequestedDamageReductions() {
        ZombiesRulesConfig.Armor armor = new ZombiesRulesConfig().getArmor();

        requireClose(0.25D, armor.getLevel1DamageReduction(), "level 1 armor should default to 25% reduction");
        requireClose(0.50D, armor.getLevel2DamageReduction(), "level 2 armor should default to 50% reduction");
        requireClose(0.75D, armor.getLevel3DamageReduction(), "level 3 armor should default to 75% reduction");
        requireClose(0.75D, armor.damageTakenMultiplierForLevel(1), "level 1 armor should take 75% damage");
        requireClose(0.50D, armor.damageTakenMultiplierForLevel(2), "level 2 armor should take 50% damage");
        requireClose(0.25D, armor.damageTakenMultiplierForLevel(3), "level 3 armor should take 25% damage");
    }

    private static void armorValidatorRejectsOutOfRangeReduction() {
        ZombiesRulesConfig config = new ZombiesRulesConfig();
        config.getArmor().setLevel3DamageReduction(1.0D);

        boolean hasIssue = new ZombiesRulesValidator().validate(config).stream()
                .anyMatch(issue -> ZombiesRulesValidator.RULES_INVALID_ARMOR.equals(issue.code()));
        require(hasIssue, "armor damage reduction >= 1 should produce a rules validation issue");
    }

    private static void ultimateMachineDefaultsUseServerconfigLevelDamage() {
        ZombiesRulesConfig.UltimateMachine ultimate = new ZombiesRulesConfig().getUltimateMachine();

        require(ultimate.getMaxUpgradeLevel() == 2, "ultimate machine max level should default to 2");
        require(ultimate.getLevels().containsKey("1"), "ultimate machine should define level 1 in serverconfig");
        require(ultimate.getLevels().containsKey("2"), "ultimate machine should define level 2 in serverconfig");
        require(ultimate.getLevels().get("1").getCost() == 2500, "ultimate level 1 cost should default to 2500");
        require(ultimate.getLevels().get("2").getCost() == 5000, "ultimate level 2 cost should default to 5000");
        requireClose(2.0D, ultimate.getLevels().get("1").getDamageMultiplier(),
                "ultimate level 1 damage should default to +100%");
        requireClose(3.0D, ultimate.getLevels().get("2").getDamageMultiplier(),
                "ultimate level 2 damage should default to +200%");
    }

    private static void ultimateMachineValidatorRejectsIncompleteLevels() {
        ZombiesRulesConfig config = new ZombiesRulesConfig();
        ZombiesRulesConfig.UltimateMachine ultimate = new ZombiesRulesConfig.UltimateMachine();
        ultimate.setMaxUpgradeLevel(2);
        ultimate.setLevels(Map.of("1", new ZombiesRulesConfig.UpgradeLevel(2500, 1.5D)));
        config.setUltimateMachine(ultimate);

        boolean hasIssue = new ZombiesRulesValidator().validate(config).stream()
                .anyMatch(issue -> ZombiesRulesValidator.RULES_INVALID_ULTIMATE_MACHINE.equals(issue.code()));
        require(hasIssue, "ultimate machine rules should require level coverage through maxUpgradeLevel");
    }

    private static void requireClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.0001D) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private ZombiesWeaponDefaultsCompatTest() {
    }
}
