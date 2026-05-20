package com.cdp.codpattern.config.zombies;

import com.cdp.codpattern.app.zombies.service.ZombiesErrorCode;
import com.cdp.codpattern.app.zombies.validation.ZombiesValidationIssue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class ZombiesRulesValidator {
    public static final ZombiesErrorCode RULES_INVALID_WEAPON_WALL =
            ZombiesErrorCode.of("rules.invalid_weapon_wall");
    public static final ZombiesErrorCode RULES_INVALID_WEAPON_RULES =
            ZombiesErrorCode.of("rules.invalid_weapon_rules");
    public static final ZombiesErrorCode RULES_INVALID_SPAWN_POINT_WEIGHTING =
            ZombiesErrorCode.of("rules.invalid_spawn_point_weighting");

    private static final Set<String> VALID_RARITIES = Set.of(
            ZombiesRulesConfig.RARITY_COMMON,
            ZombiesRulesConfig.RARITY_RARE,
            ZombiesRulesConfig.RARITY_EPIC);

    public List<ZombiesValidationIssue> validate(ZombiesRulesConfig config) {
        ZombiesRulesConfig resolved = config == null ? new ZombiesRulesConfig() : config;
        List<ZombiesValidationIssue> issues = new ArrayList<>();
        validateWeaponRules(resolved.getWeaponRules(), issues);
        validateWeaponWall(resolved.getWeaponWall(), issues);
        validateSpawnPointWeighting(resolved.getSpawnPointWeighting(), issues);
        return List.copyOf(issues);
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

    private static String normalizeRarityId(String rarityId) {
        return Objects.requireNonNullElse(rarityId, "").trim().toLowerCase(Locale.ROOT);
    }

    private static boolean finite(Double value) {
        return value != null && Double.isFinite(value);
    }

    private static boolean positiveFinite(Double value) {
        return value != null && Double.isFinite(value) && value > 0.0D;
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
