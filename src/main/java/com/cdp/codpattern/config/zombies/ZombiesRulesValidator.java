package com.cdp.codpattern.config.zombies;

import com.cdp.codpattern.app.zombies.service.ZombiesErrorCode;
import com.cdp.codpattern.app.zombies.validation.ZombiesValidationIssue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ZombiesRulesValidator {
    public static final ZombiesErrorCode RULES_INVALID_WEAPON_WALL =
            ZombiesErrorCode.of("rules.invalid_weapon_wall");
    public static final ZombiesErrorCode RULES_INVALID_WEAPON_RULES =
            ZombiesErrorCode.of("rules.invalid_weapon_rules");
    public static final ZombiesErrorCode RULES_INVALID_ULTIMATE_MACHINE =
            ZombiesErrorCode.of("rules.invalid_ultimate_machine");
    public static final ZombiesErrorCode RULES_INVALID_SPAWN_POINT_WEIGHTING =
            ZombiesErrorCode.of("rules.invalid_spawn_point_weighting");
    public static final ZombiesErrorCode RULES_INVALID_ARMOR =
            ZombiesErrorCode.of("rules.invalid_armor");

    private static final Set<String> VALID_RARITIES = Set.of(
            ZombiesRulesConfig.RARITY_COMMON,
            ZombiesRulesConfig.RARITY_RARE,
            ZombiesRulesConfig.RARITY_EPIC);

    public List<ZombiesValidationIssue> validate(ZombiesRulesConfig config) {
        ZombiesRulesConfig resolved = config == null ? new ZombiesRulesConfig() : config;
        List<ZombiesValidationIssue> issues = new ArrayList<>();
        validateArmor(resolved.getArmor(), issues);
        validateWeaponRules(resolved.getWeaponRules(), issues);
        validateWeaponWall(resolved.getWeaponWall(), issues);
        validateUltimateMachine(resolved.getUltimateMachine(), issues);
        validateSpawnPointWeighting(resolved.getSpawnPointWeighting(), issues);
        return List.copyOf(issues);
    }

    private static void validateArmor(
            ZombiesRulesConfig.Armor armor,
            List<ZombiesValidationIssue> issues
    ) {
        if (armor == null) {
            issues.add(ZombiesValidationIssue.error(
                    RULES_INVALID_ARMOR,
                    "armor",
                    "Zombies armor config is missing."));
            return;
        }
        validateDamageReduction(
                armor.getLevel1DamageReduction(),
                "armor.level1DamageReduction",
                "Armor level 1 damage reduction must be finite and in [0, 1).",
                issues);
        validateDamageReduction(
                armor.getLevel2DamageReduction(),
                "armor.level2DamageReduction",
                "Armor level 2 damage reduction must be finite and in [0, 1).",
                issues);
        validateDamageReduction(
                armor.getLevel3DamageReduction(),
                "armor.level3DamageReduction",
                "Armor level 3 damage reduction must be finite and in [0, 1).",
                issues);
    }

    private static void validateSpawnPointWeighting(
            ZombiesRulesConfig.SpawnPointWeighting weighting,
            List<ZombiesValidationIssue> issues
    ) {
        if (weighting == null) {
            issues.add(ZombiesValidationIssue.error(
                    RULES_INVALID_SPAWN_POINT_WEIGHTING,
                    "spawnPointWeighting",
                    "Zombies spawnPointWeighting config is missing."));
            return;
        }
        validatePositiveFinite(
                weighting.getTooCloseDistance(),
                "spawnPointWeighting.tooCloseDistance",
                "tooCloseDistance must be positive and finite.",
                issues);
        validatePositiveFinite(
                weighting.getIdealMinDistance(),
                "spawnPointWeighting.idealMinDistance",
                "idealMinDistance must be positive and finite.",
                issues);
        validatePositiveFinite(
                weighting.getIdealMaxDistance(),
                "spawnPointWeighting.idealMaxDistance",
                "idealMaxDistance must be positive and finite.",
                issues);
        validatePositiveFinite(
                weighting.getFarDistance(),
                "spawnPointWeighting.farDistance",
                "farDistance must be positive and finite.",
                issues);
        if (positiveFinite(weighting.getTooCloseDistance())
                && positiveFinite(weighting.getIdealMinDistance())
                && weighting.getTooCloseDistance() > weighting.getIdealMinDistance()) {
            issues.add(ZombiesValidationIssue.error(
                    RULES_INVALID_SPAWN_POINT_WEIGHTING,
                    "spawnPointWeighting.distanceOrder",
                    "tooCloseDistance must be <= idealMinDistance."));
        }
        if (positiveFinite(weighting.getIdealMinDistance())
                && positiveFinite(weighting.getIdealMaxDistance())
                && weighting.getIdealMinDistance() > weighting.getIdealMaxDistance()) {
            issues.add(ZombiesValidationIssue.error(
                    RULES_INVALID_SPAWN_POINT_WEIGHTING,
                    "spawnPointWeighting.distanceOrder",
                    "idealMinDistance must be <= idealMaxDistance."));
        }
        if (positiveFinite(weighting.getIdealMaxDistance())
                && positiveFinite(weighting.getFarDistance())
                && weighting.getIdealMaxDistance() > weighting.getFarDistance()) {
            issues.add(ZombiesValidationIssue.error(
                    RULES_INVALID_SPAWN_POINT_WEIGHTING,
                    "spawnPointWeighting.distanceOrder",
                    "idealMaxDistance must be <= farDistance."));
        }

        validatePositiveFinite(
                weighting.getMinMultiplier(),
                "spawnPointWeighting.minMultiplier",
                "minMultiplier must be positive and finite.",
                issues);
        validatePositiveFinite(
                weighting.getIdealMultiplier(),
                "spawnPointWeighting.idealMultiplier",
                "idealMultiplier must be positive and finite.",
                issues);
        validatePositiveFinite(
                weighting.getFarMultiplier(),
                "spawnPointWeighting.farMultiplier",
                "farMultiplier must be positive and finite.",
                issues);
        validatePositiveFinite(
                weighting.getMaxMultiplier(),
                "spawnPointWeighting.maxMultiplier",
                "maxMultiplier must be positive and finite.",
                issues);
        if (positiveFinite(weighting.getMinMultiplier())
                && positiveFinite(weighting.getMaxMultiplier())
                && weighting.getMinMultiplier() > weighting.getMaxMultiplier()) {
            issues.add(ZombiesValidationIssue.error(
                    RULES_INVALID_SPAWN_POINT_WEIGHTING,
                    "spawnPointWeighting.multiplierBounds",
                    "minMultiplier must be <= maxMultiplier."));
        }
    }

    private static void validateWeaponRules(
            ZombiesRulesConfig.WeaponRules weaponRules,
            List<ZombiesValidationIssue> issues
    ) {
        if (weaponRules == null) {
            issues.add(ZombiesValidationIssue.error(
                    RULES_INVALID_WEAPON_RULES,
                    "weaponRules",
                    "Zombies weaponRules config is missing."));
            return;
        }
        if (weaponRules.getStarterWeaponAmmunitionPerMagazineMultiple() == null
                || weaponRules.getStarterWeaponAmmunitionPerMagazineMultiple() < 0) {
            issues.add(ZombiesValidationIssue.error(
                    RULES_INVALID_WEAPON_RULES,
                    "weaponRules.starterWeaponAmmunitionPerMagazineMultiple",
                    "Starter weapon ammunition magazine multiple must be a non-negative integer."));
        }
        if (weaponRules.getWeaponPoolAmmunitionPerMagazineMultiple() == null
                || weaponRules.getWeaponPoolAmmunitionPerMagazineMultiple() < 0) {
            issues.add(ZombiesValidationIssue.error(
                    RULES_INVALID_WEAPON_RULES,
                    "weaponRules.weaponPoolAmmunitionPerMagazineMultiple",
                    "Weapon wall pool ammunition magazine multiple must be a non-negative integer."));
        }
    }

    private static void validateWeaponWall(
            ZombiesRulesConfig.WeaponWall weaponWall,
            List<ZombiesValidationIssue> issues
    ) {
        if (weaponWall == null) {
            issues.add(ZombiesValidationIssue.error(
                    RULES_INVALID_WEAPON_WALL,
                    "weaponWall",
                    "Zombies weaponWall config is missing."));
            return;
        }
        if (weaponWall.getRefreshIntervalWaves() == null || weaponWall.getRefreshIntervalWaves() < 1) {
            issues.add(ZombiesValidationIssue.error(
                    RULES_INVALID_WEAPON_WALL,
                    "weaponWall.refreshIntervalWaves",
                    "Weapon wall refreshIntervalWaves must be >= 1."));
        }

        Set<String> seenValidRarities = new LinkedHashSet<>();
        Set<String> duplicateValidRarities = new LinkedHashSet<>();
        int validRaritiesWithGuns = 0;
        for (ZombiesRulesConfig.Rarity rarity : safeRarities(weaponWall)) {
            String rarityId = normalizeRarityId(rarity == null ? "" : rarity.getId());
            if (!VALID_RARITIES.contains(rarityId)) {
                issues.add(ZombiesValidationIssue.warning(
                        RULES_INVALID_WEAPON_WALL,
                        "weaponWall.rarities." + rarityId,
                        "Weapon wall rarity id '" + rarityId + "' is ignored; only common, rare, and epic are supported."));
                continue;
            }
            if (!seenValidRarities.add(rarityId)) {
                duplicateValidRarities.add(rarityId);
            }
            validateRarity(rarity, rarityId, issues);
            if (hasValidGun(rarity)) {
                validRaritiesWithGuns++;
            }
        }
        for (String duplicate : duplicateValidRarities) {
            issues.add(ZombiesValidationIssue.error(
                    RULES_INVALID_WEAPON_WALL,
                    "weaponWall.rarities." + duplicate,
                    "Weapon wall rarity '" + duplicate + "' is duplicated."));
        }
        if (seenValidRarities.isEmpty()) {
            issues.add(ZombiesValidationIssue.error(
                    RULES_INVALID_WEAPON_WALL,
                    "weaponWall.rarities",
                    "Weapon wall config must contain at least one valid rarity."));
        }
        if (validRaritiesWithGuns <= 0) {
            issues.add(ZombiesValidationIssue.error(
                    RULES_INVALID_WEAPON_WALL,
                    "weaponWall.rarities.guns",
                    "Weapon wall config must contain at least one valid gun in a supported rarity."));
        }
    }

    private static void validateUltimateMachine(
            ZombiesRulesConfig.UltimateMachine ultimateMachine,
            List<ZombiesValidationIssue> issues
    ) {
        if (ultimateMachine == null) {
            issues.add(ZombiesValidationIssue.error(
                    RULES_INVALID_ULTIMATE_MACHINE,
                    "ultimateMachine",
                    "Zombies ultimateMachine config is missing."));
            return;
        }
        Integer maxUpgradeLevel = ultimateMachine.getMaxUpgradeLevel();
        if (maxUpgradeLevel == null || maxUpgradeLevel <= 0) {
            issues.add(ZombiesValidationIssue.error(
                    RULES_INVALID_ULTIMATE_MACHINE,
                    "ultimateMachine.maxUpgradeLevel",
                    "Ultimate machine maxUpgradeLevel must be positive."));
            return;
        }

        Set<Integer> configuredLevels = new LinkedHashSet<>();
        for (Map.Entry<String, ZombiesRulesConfig.UpgradeLevel> entry : safeUpgradeLevels(ultimateMachine).entrySet()) {
            int level = parsePositiveLevel(entry.getKey());
            if (level < 0) {
                issues.add(ZombiesValidationIssue.error(
                        RULES_INVALID_ULTIMATE_MACHINE,
                        "ultimateMachine.levels",
                        "Ultimate machine level keys must be positive integers."));
            } else {
                configuredLevels.add(level);
            }
            ZombiesRulesConfig.UpgradeLevel levelData = entry.getValue();
            if (levelData == null || levelData.getCost() == null || levelData.getCost() < 0) {
                issues.add(ZombiesValidationIssue.error(
                        RULES_INVALID_ULTIMATE_MACHINE,
                        "ultimateMachine.levels." + entry.getKey() + ".cost",
                        "Ultimate machine level cost must be non-negative."));
            }
            if (levelData == null
                    || levelData.getDamageMultiplier() == null
                    || !Double.isFinite(levelData.getDamageMultiplier())
                    || levelData.getDamageMultiplier() <= 0.0D) {
                issues.add(ZombiesValidationIssue.error(
                        RULES_INVALID_ULTIMATE_MACHINE,
                        "ultimateMachine.levels." + entry.getKey() + ".damageMultiplier",
                        "Ultimate machine level damageMultiplier must be positive and finite."));
            }
        }
        for (int level = 1; level <= maxUpgradeLevel; level++) {
            if (!configuredLevels.contains(level)) {
                issues.add(ZombiesValidationIssue.error(
                        RULES_INVALID_ULTIMATE_MACHINE,
                        "ultimateMachine.levels",
                        "Ultimate machine levels must cover 1..maxUpgradeLevel."));
                break;
            }
        }
    }

    private static void validateRarity(
            ZombiesRulesConfig.Rarity rarity,
            String rarityId,
            List<ZombiesValidationIssue> issues
    ) {
        String subject = "weaponWall.rarities." + rarityId;
        if (rarity == null) {
            issues.add(ZombiesValidationIssue.error(
                    RULES_INVALID_WEAPON_WALL,
                    subject,
                    "Weapon wall rarity entry is missing."));
            return;
        }
        if (rarity.getPrice() == null || rarity.getPrice() < 0) {
            issues.add(ZombiesValidationIssue.error(
                    RULES_INVALID_WEAPON_WALL,
                    subject + ".price",
                    "Weapon wall rarity price must be non-negative."));
        }
        if (rarity.getDamageMultiplier() == null
                || !Double.isFinite(rarity.getDamageMultiplier())
                || rarity.getDamageMultiplier() <= 0.0D) {
            issues.add(ZombiesValidationIssue.error(
                    RULES_INVALID_WEAPON_WALL,
                    subject + ".damageMultiplier",
                    "Weapon wall rarity damageMultiplier must be positive and finite."));
        }
        if (!finite(rarity.getInitialWeight())) {
            issues.add(ZombiesValidationIssue.error(
                    RULES_INVALID_WEAPON_WALL,
                    subject + ".initialWeight",
                    "Weapon wall rarity initialWeight must be finite."));
        }
        if (!finite(rarity.getWeightDeltaPerRefresh())) {
            issues.add(ZombiesValidationIssue.error(
                    RULES_INVALID_WEAPON_WALL,
                    subject + ".weightDeltaPerRefresh",
                    "Weapon wall rarity weightDeltaPerRefresh must be finite."));
        }
        if (!finite(rarity.getMinWeight()) || !finite(rarity.getMaxWeight())) {
            issues.add(ZombiesValidationIssue.error(
                    RULES_INVALID_WEAPON_WALL,
                    subject + ".weightBounds",
                    "Weapon wall rarity minWeight and maxWeight must be finite."));
        }
        int validGuns = 0;
        for (ZombiesRulesConfig.GunWeight gun : safeGuns(rarity)) {
            String gunId = Objects.requireNonNullElse(gun == null ? "" : gun.getGunId(), "").trim();
            double weight = gun == null || gun.getWeight() == null ? 0.0D : gun.getWeight();
            if (gunId.isBlank()) {
                issues.add(ZombiesValidationIssue.warning(
                        RULES_INVALID_WEAPON_WALL,
                        subject + ".guns",
                        "Weapon wall gun entry with empty gunId is ignored."));
                continue;
            }
            if (!Double.isFinite(weight) || weight <= 0.0D) {
                issues.add(ZombiesValidationIssue.warning(
                        RULES_INVALID_WEAPON_WALL,
                        subject + ".guns." + gunId,
                        "Weapon wall gun '" + gunId + "' is ignored because its weight must be > 0."));
                continue;
            }
            validGuns++;
        }
        if (validGuns <= 0) {
            issues.add(ZombiesValidationIssue.error(
                    RULES_INVALID_WEAPON_WALL,
                    subject + ".guns",
                    "Weapon wall rarity '" + rarityId + "' has no valid guns."));
        }
    }

    private static boolean hasValidGun(ZombiesRulesConfig.Rarity rarity) {
        for (ZombiesRulesConfig.GunWeight gun : safeGuns(rarity)) {
            String gunId = Objects.requireNonNullElse(gun == null ? "" : gun.getGunId(), "").trim();
            double weight = gun == null || gun.getWeight() == null ? 0.0D : gun.getWeight();
            if (!gunId.isBlank() && Double.isFinite(weight) && weight > 0.0D) {
                return true;
            }
        }
        return false;
    }

    public static boolean supportedRarityId(String rarityId) {
        return VALID_RARITIES.contains(normalizeRarityId(rarityId));
    }

    private static List<ZombiesRulesConfig.Rarity> safeRarities(ZombiesRulesConfig.WeaponWall weaponWall) {
        return weaponWall == null || weaponWall.getRarities() == null ? List.of() : weaponWall.getRarities();
    }

    private static List<ZombiesRulesConfig.GunWeight> safeGuns(ZombiesRulesConfig.Rarity rarity) {
        return rarity == null || rarity.getGuns() == null ? List.of() : rarity.getGuns();
    }

    private static Map<String, ZombiesRulesConfig.UpgradeLevel> safeUpgradeLevels(
            ZombiesRulesConfig.UltimateMachine ultimateMachine
    ) {
        return ultimateMachine == null || ultimateMachine.getLevels() == null ? Map.of() : ultimateMachine.getLevels();
    }

    private static int parsePositiveLevel(String value) {
        try {
            int level = Integer.parseInt(Objects.requireNonNullElse(value, "").trim());
            return level > 0 ? level : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String normalizeRarityId(String rarityId) {
        return Objects.requireNonNullElse(rarityId, "").trim().toLowerCase(Locale.ROOT);
    }

    private static boolean finite(Double value) {
        return value != null && Double.isFinite(value);
    }

    private static boolean positiveFinite(Double value) {
        return value != null && Double.isFinite(value) && value > 0.0D;
    }

    private static void validateDamageReduction(
            Double value,
            String subject,
            String message,
            List<ZombiesValidationIssue> issues
    ) {
        if (value == null || !Double.isFinite(value) || value < 0.0D || value >= 1.0D) {
            issues.add(ZombiesValidationIssue.error(
                    RULES_INVALID_ARMOR,
                    subject,
                    message));
        }
    }

    private static void validatePositiveFinite(
            Double value,
            String subject,
            String message,
            List<ZombiesValidationIssue> issues
    ) {
        if (!positiveFinite(value)) {
            issues.add(ZombiesValidationIssue.error(
                    RULES_INVALID_SPAWN_POINT_WEIGHTING,
                    subject,
                    message));
        }
    }
}
