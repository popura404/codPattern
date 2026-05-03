package com.cdp.codpattern.app.zombies.validation;

import com.cdp.codpattern.app.zombies.map.ZombiesMapSnapshot;
import com.cdp.codpattern.app.zombies.map.ZombiesMatchSnapshot;
import com.cdp.codpattern.app.zombies.service.ZombiesErrorCode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ZombiesMapValidator {
    private static final ZombiesErrorCode MAP_MISSING_GROUP_ONE_ZOMBIE_SPAWN =
            ZombiesErrorCode.of("map.missing_group_1_zombie_spawn");
    private static final ZombiesErrorCode MAP_DYNAMIC_PLAYER_SPAWN_UNSUPPORTED =
            ZombiesErrorCode.of("map.dynamic_player_spawn_unsupported");
    private static final ZombiesErrorCode MAP_DUPLICATE_OBJECT_ID =
            ZombiesErrorCode.of("map.duplicate_object_id");
    private static final ZombiesErrorCode MAP_INVALID_BARRIER =
            ZombiesErrorCode.of("map.invalid_barrier");
    private static final ZombiesErrorCode MAP_INVALID_WEAPON_WALL =
            ZombiesErrorCode.of("map.invalid_weapon_wall");
    private static final ZombiesErrorCode MAP_WEAPON_WALL_MISSING_TOP_RARITY_CANDIDATE =
            ZombiesErrorCode.of("map.weapon_wall_missing_top_rarity_candidate");
    private static final ZombiesErrorCode MAP_INVALID_AMMO_BOX =
            ZombiesErrorCode.of("map.invalid_ammo_box");
    private static final ZombiesErrorCode MAP_INVALID_ARMOR_STATION =
            ZombiesErrorCode.of("map.invalid_armor_station");
    private static final ZombiesErrorCode MAP_MISSING_POWER_SWITCH =
            ZombiesErrorCode.of("map.missing_power_switch");
    private static final ZombiesErrorCode MAP_MULTIPLE_POWER_SWITCHES =
            ZombiesErrorCode.of("map.multiple_power_switches");
    private static final ZombiesErrorCode MAP_INVALID_POWER_SWITCH =
            ZombiesErrorCode.of("map.invalid_power_switch");
    private static final ZombiesErrorCode MAP_REQUIRES_POWER_WITHOUT_SWITCH =
            ZombiesErrorCode.of("map.requires_power_without_switch");
    private static final ZombiesErrorCode MAP_INVALID_SODA_MACHINE =
            ZombiesErrorCode.of("map.invalid_soda_machine");
    private static final ZombiesErrorCode MAP_INVALID_ULTIMATE_MACHINE =
            ZombiesErrorCode.of("map.invalid_ultimate_machine");

    private final ZombiesMapValidationProfile profile;

    public ZombiesMapValidator() {
        this(ZombiesMapValidationProfile.MVP1_MINIMAL);
    }

    public ZombiesMapValidator(ZombiesMapValidationProfile profile) {
        this.profile = profile == null ? ZombiesMapValidationProfile.MVP1_MINIMAL : profile;
    }

    public ZombiesMapValidationReport validate(ZombiesMatchSnapshot matchSnapshot) {
        Objects.requireNonNull(matchSnapshot, "matchSnapshot");
        return validate(matchSnapshot.mapSnapshot());
    }

    public ZombiesMapValidationReport validate(ZombiesMapSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        List<ZombiesValidationIssue> issues = validateSnapshotRules(snapshot);
        return new ZombiesMapValidationReport(snapshot.roomId(), profile.key(), issues);
    }

    public ZombiesMapValidationReport validate(
            ZombiesMapValidationContributor.ZombiesMapValidationContext context
    ) {
        Objects.requireNonNull(context, "context");
        ZombiesMapSnapshot snapshot = ZombiesMapSnapshot.fromContributorContext(context);
        List<ZombiesValidationIssue> issues = validateSnapshotRules(snapshot);
        for (ZombiesMapValidationContributor contributor : profile.contributors()) {
            List<ZombiesMapValidationContributor.ZombiesValidationIssue> contributed = contributor.validate(context);
            if (contributed == null) {
                continue;
            }
            contributed.stream()
                    .filter(Objects::nonNull)
                    .map(ZombiesValidationIssue::fromContributorIssue)
                    .forEach(issues::add);
        }
        return new ZombiesMapValidationReport(context.roomId(), profile.key(), issues);
    }

    private List<ZombiesValidationIssue> validateSnapshotRules(ZombiesMapSnapshot snapshot) {
        List<ZombiesValidationIssue> issues = new ArrayList<>();
        if (profile.requireEndTeleportPoint() && !snapshot.hasEndTeleportPoint()) {
            issues.add(ZombiesValidationIssue.error(
                    ZombiesErrorCode.MAP_MISSING_ENDTP,
                    "endtp",
                    "Zombies map requires a match end teleport point."));
        }
        if (profile.requireInitialPlayerSpawn()
                && snapshot.spawns().stream().noneMatch(ZombiesMapSnapshot.SpawnSnapshot::initialPlayerSpawn)) {
            issues.add(ZombiesValidationIssue.error(
                    ZombiesErrorCode.MAP_MISSING_INITIAL_SPAWN,
                    "spawn.INITIAL",
                    "Zombies map requires at least one INITIAL player spawn."));
        }
        if (profile.requireGroupOneZombieSpawn()
                && snapshot.spawns().stream().noneMatch(ZombiesMapValidator::validGroupOneZombieSpawn)) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_MISSING_GROUP_ONE_ZOMBIE_SPAWN,
                    "zombie_spawn.group_1",
                    "Zombies map requires at least one group=1 zombie spawn with positive weight."));
        }
        if (profile.rejectDynamicPlayerSpawns()) {
            snapshot.spawns().stream()
                    .filter(ZombiesMapSnapshot.SpawnSnapshot::dynamicPlayerSpawn)
                    .map(spawn -> ZombiesValidationIssue.error(
                            MAP_DYNAMIC_PLAYER_SPAWN_UNSUPPORTED,
                            subject("spawn", spawn.objectId(), spawn.featureKey()),
                            "Zombies MVP1 maps do not support dynamic player spawn kinds."))
                    .forEach(issues::add);
        }
        if (profile.requireUniqueObjectIds()) {
            addDuplicateObjectIdIssues(snapshot, issues);
        }
        if (profile.validatePurchases()) {
            addPurchaseIssues(snapshot, issues);
        }
        if (profile.validateFullInitial()) {
            addFullInitialIssues(snapshot, issues);
        }
        return issues;
    }

    private static boolean validGroupOneZombieSpawn(ZombiesMapSnapshot.SpawnSnapshot spawn) {
        return spawn.zombieSpawn() && spawn.group() == 1 && spawn.weight() > 0.0D;
    }

    private static void addDuplicateObjectIdIssues(
            ZombiesMapSnapshot snapshot,
            List<ZombiesValidationIssue> issues
    ) {
        Map<String, String> seenSubjects = new LinkedHashMap<>();
        for (ZombiesMapSnapshot.SpawnSnapshot spawn : snapshot.spawns()) {
            addObjectId("spawn", spawn.objectId(), spawn.featureKey(), seenSubjects, issues);
        }
        for (ZombiesMapSnapshot.BarrierSnapshot barrier : snapshot.barriers()) {
            addObjectId("barrier", barrier.objectId(), barrier.featureKey(), seenSubjects, issues);
        }
        for (ZombiesMapSnapshot.WeaponWallSnapshot weaponWall : snapshot.weaponWalls()) {
            addObjectId("weapon_wall", weaponWall.objectId(), weaponWall.featureKey(), seenSubjects, issues);
        }
        for (ZombiesMapSnapshot.AmmoBoxSnapshot ammoBox : snapshot.ammoBoxes()) {
            addObjectId("ammo_box", ammoBox.objectId(), ammoBox.featureKey(), seenSubjects, issues);
        }
        for (ZombiesMapSnapshot.ArmorStationSnapshot armorStation : snapshot.armorStations()) {
            addObjectId("armor_station", armorStation.objectId(), armorStation.featureKey(), seenSubjects, issues);
        }
        for (ZombiesMapSnapshot.PowerSwitchSnapshot powerSwitch : snapshot.powerSwitches()) {
            addObjectId("power_switch", powerSwitch.objectId(), powerSwitch.featureKey(), seenSubjects, issues);
        }
        for (ZombiesMapSnapshot.SodaMachineSnapshot sodaMachine : snapshot.sodaMachines()) {
            addObjectId("soda_machine", sodaMachine.objectId(), sodaMachine.featureKey(), seenSubjects, issues);
        }
        for (ZombiesMapSnapshot.UltimateMachineSnapshot ultimateMachine : snapshot.ultimateMachines()) {
            addObjectId("ultimate_machine", ultimateMachine.objectId(), ultimateMachine.featureKey(), seenSubjects, issues);
        }
        for (ZombiesMapSnapshot.ObjectIdSnapshot object : snapshot.extraObjects()) {
            addObjectId("object", object.objectId(), object.featureKey(), seenSubjects, issues);
        }
    }

    private static void addPurchaseIssues(
            ZombiesMapSnapshot snapshot,
            List<ZombiesValidationIssue> issues
    ) {
        for (ZombiesMapSnapshot.BarrierSnapshot barrier : snapshot.barriers()) {
            String subject = subject("barrier", barrier.objectId(), barrier.featureKey());
            if (barrier.group() >= 2 && barrier.cost() < 0) {
                issues.add(ZombiesValidationIssue.error(
                        MAP_INVALID_BARRIER,
                        subject,
                        "Barrier groups unlocked by purchases require non-negative cost."));
            }
        }
        for (ZombiesMapSnapshot.WeaponWallSnapshot weaponWall : snapshot.weaponWalls()) {
            addWeaponWallIssues(weaponWall, issues);
        }
        for (ZombiesMapSnapshot.AmmoBoxSnapshot ammoBox : snapshot.ammoBoxes()) {
            String subject = subject("ammo_box", ammoBox.objectId(), ammoBox.featureKey());
            for (Map.Entry<String, Integer> entry : ammoBox.pricesByWeaponLevel().entrySet()) {
                if (Objects.requireNonNullElse(entry.getKey(), "").trim().isEmpty()) {
                    issues.add(ZombiesValidationIssue.error(
                            MAP_INVALID_AMMO_BOX,
                            subject,
                            "Ammo box price table contains an empty weapon level key."));
                }
                if (entry.getValue() == null || entry.getValue() < 0) {
                    issues.add(ZombiesValidationIssue.error(
                            MAP_INVALID_AMMO_BOX,
                            subject,
                            "Ammo box prices must be non-negative."));
                }
            }
        }
        for (ZombiesMapSnapshot.ArmorStationSnapshot armorStation : snapshot.armorStations()) {
            String subject = subject("armor_station", armorStation.objectId(), armorStation.featureKey());
            if (armorStation.armorLevel() <= 0 || armorStation.armorLevel() > 3) {
                issues.add(ZombiesValidationIssue.error(
                        MAP_INVALID_ARMOR_STATION,
                        subject,
                        "Armor station level must be 1, 2, or 3."));
            }
            if (armorStation.buyCost() < 0) {
                issues.add(ZombiesValidationIssue.error(
                        MAP_INVALID_ARMOR_STATION,
                        subject,
                        "Armor station cost must be non-negative."));
            }
            if (!Double.isFinite(armorStation.damageTakenMultiplier())
                    || armorStation.damageTakenMultiplier() <= 0.0D
                    || armorStation.damageTakenMultiplier() > 1.0D) {
                issues.add(ZombiesValidationIssue.error(
                        MAP_INVALID_ARMOR_STATION,
                        subject,
                        "Armor station damageTakenMultiplier must be in (0, 1]."));
            }
        }
    }

    private static void addWeaponWallIssues(
            ZombiesMapSnapshot.WeaponWallSnapshot weaponWall,
            List<ZombiesValidationIssue> issues
    ) {
        String subject = subject("weapon_wall", weaponWall.objectId(), weaponWall.featureKey());
        if (weaponWall.weaponLevel() <= 0) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_INVALID_WEAPON_WALL,
                    subject,
                    "Weapon wall weaponLevel must be positive."));
        }
        if (!Double.isFinite(weaponWall.levelDamageMultiplier())
                || weaponWall.levelDamageMultiplier() <= 0.0D) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_INVALID_WEAPON_WALL,
                    subject,
                    "Weapon wall levelDamageMultiplier must be positive."));
        }
        if (weaponWall.price() < 0) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_INVALID_WEAPON_WALL,
                    subject,
                    "Weapon wall price must be non-negative."));
        }
        for (Integer refreshWave : weaponWall.refreshWaves()) {
            if (refreshWave == null || refreshWave <= 0) {
                issues.add(ZombiesValidationIssue.error(
                        MAP_INVALID_WEAPON_WALL,
                        subject,
                        "Weapon wall refresh waves must be positive."));
            }
        }

        Map<String, Integer> rarityRanks = new LinkedHashMap<>();
        Set<String> duplicateRarityIds = new HashSet<>();
        if (weaponWall.rarityPools().isEmpty()) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_INVALID_WEAPON_WALL,
                    subject,
                    "Weapon wall requires at least one rarity pool."));
        }
        for (ZombiesMapSnapshot.RarityPoolSnapshot pool : weaponWall.rarityPools()) {
            if (pool.id().isBlank()) {
                issues.add(ZombiesValidationIssue.error(
                        MAP_INVALID_WEAPON_WALL,
                        subject,
                        "Weapon wall rarity pool id must be non-empty."));
            } else if (rarityRanks.putIfAbsent(pool.id(), pool.rank()) != null) {
                duplicateRarityIds.add(pool.id());
            }
            if (pool.rank() < 0) {
                issues.add(ZombiesValidationIssue.error(
                        MAP_INVALID_WEAPON_WALL,
                        subject,
                        "Weapon wall rarity rank must be non-negative."));
            }
            if (!Double.isFinite(pool.baseWeight()) || pool.baseWeight() < 0.0D) {
                issues.add(ZombiesValidationIssue.error(
                        MAP_INVALID_WEAPON_WALL,
                        subject,
                        "Weapon wall rarity baseWeight must be non-negative."));
            }
            if (!Double.isFinite(pool.waveFactor()) || pool.waveFactor() < 0.0D) {
                issues.add(ZombiesValidationIssue.error(
                        MAP_INVALID_WEAPON_WALL,
                        subject,
                        "Weapon wall rarity waveFactor must be non-negative."));
            }
        }
        for (String rarityId : duplicateRarityIds) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_INVALID_WEAPON_WALL,
                    subject,
                    "Weapon wall rarity pool id '" + rarityId + "' is duplicated."));
        }

        if (weaponWall.weapons().isEmpty()) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_INVALID_WEAPON_WALL,
                    subject,
                    "Weapon wall requires at least one weapon candidate."));
        }
        for (ZombiesMapSnapshot.WeaponCandidateSnapshot candidate : weaponWall.weapons()) {
            if (candidate.gunId().isBlank()) {
                issues.add(ZombiesValidationIssue.error(
                        MAP_INVALID_WEAPON_WALL,
                        subject,
                        "Weapon wall weapon candidate gunId must be non-empty."));
            }
            for (Map.Entry<String, Double> entry : candidate.weightsByRarity().entrySet()) {
                String rarityId = Objects.requireNonNullElse(entry.getKey(), "").trim();
                Double weight = entry.getValue();
                if (rarityId.isEmpty()) {
                    issues.add(ZombiesValidationIssue.error(
                            MAP_INVALID_WEAPON_WALL,
                            subject,
                            "Weapon wall weapon candidate contains an empty rarity id."));
                } else if (!rarityRanks.isEmpty() && !rarityRanks.containsKey(rarityId)) {
                    issues.add(ZombiesValidationIssue.error(
                            MAP_INVALID_WEAPON_WALL,
                            subject,
                            "Weapon wall weapon candidate references unknown rarity '" + rarityId + "'."));
                }
                if (weight == null || !Double.isFinite(weight) || weight < 0.0D) {
                    issues.add(ZombiesValidationIssue.error(
                            MAP_INVALID_WEAPON_WALL,
                            subject,
                            "Weapon wall candidate weights must be non-negative."));
                }
            }
        }

        addTopRarityCandidateIssue(weaponWall, rarityRanks, subject, issues);
    }

    private static void addTopRarityCandidateIssue(
            ZombiesMapSnapshot.WeaponWallSnapshot weaponWall,
            Map<String, Integer> rarityRanks,
            String subject,
            List<ZombiesValidationIssue> issues
    ) {
        if (rarityRanks.isEmpty() || weaponWall.weapons().isEmpty()) {
            return;
        }
        int highestRank = Integer.MIN_VALUE;
        for (Integer rank : rarityRanks.values()) {
            highestRank = Math.max(highestRank, rank);
        }
        Set<String> highestRarityIds = new LinkedHashSet<>();
        for (Map.Entry<String, Integer> entry : rarityRanks.entrySet()) {
            if (entry.getValue() == highestRank) {
                highestRarityIds.add(entry.getKey());
            }
        }
        boolean hasCandidate = false;
        for (ZombiesMapSnapshot.WeaponCandidateSnapshot candidate : weaponWall.weapons()) {
            for (String rarityId : highestRarityIds) {
                Double weight = candidate.weightsByRarity().get(rarityId);
                if (weight != null && Double.isFinite(weight) && weight > 0.0D) {
                    hasCandidate = true;
                    break;
                }
            }
            if (hasCandidate) {
                break;
            }
        }
        if (!hasCandidate) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_WEAPON_WALL_MISSING_TOP_RARITY_CANDIDATE,
                    subject,
                    "Weapon wall highest-rank rarity must have at least one candidate with positive weight."));
        }
    }

    private static void addFullInitialIssues(
            ZombiesMapSnapshot snapshot,
            List<ZombiesValidationIssue> issues
    ) {
        int powerSwitchCount = snapshot.powerSwitches().size();
        if (powerSwitchCount == 0) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_MISSING_POWER_SWITCH,
                    "power_switch",
                    "MVP3 zombies maps require exactly one power switch."));
        } else if (powerSwitchCount > 1) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_MULTIPLE_POWER_SWITCHES,
                    "power_switch",
                    "MVP3 zombies maps allow exactly one power switch."));
        }

        for (ZombiesMapSnapshot.PowerSwitchSnapshot powerSwitch : snapshot.powerSwitches()) {
            if (powerSwitch.cost() < 0) {
                issues.add(ZombiesValidationIssue.error(
                        MAP_INVALID_POWER_SWITCH,
                        subject("power_switch", powerSwitch.objectId(), powerSwitch.featureKey()),
                        "Power switch cost must be non-negative."));
            }
        }

        boolean hasPowerSwitch = powerSwitchCount > 0;
        for (ZombiesMapSnapshot.SodaMachineSnapshot sodaMachine : snapshot.sodaMachines()) {
            addSodaMachineIssues(sodaMachine, hasPowerSwitch, issues);
        }
        for (ZombiesMapSnapshot.UltimateMachineSnapshot ultimateMachine : snapshot.ultimateMachines()) {
            addUltimateMachineIssues(ultimateMachine, hasPowerSwitch, issues);
        }
    }

    private static void addSodaMachineIssues(
            ZombiesMapSnapshot.SodaMachineSnapshot sodaMachine,
            boolean hasPowerSwitch,
            List<ZombiesValidationIssue> issues
    ) {
        String subject = subject("soda_machine", sodaMachine.objectId(), sodaMachine.featureKey());
        if (sodaMachine.requiresPower() && !hasPowerSwitch) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_REQUIRES_POWER_WITHOUT_SWITCH,
                    subject,
                    "Soda machine requires power but the map has no power switch."));
        }
        if (sodaMachine.buffId().isBlank()) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_INVALID_SODA_MACHINE,
                    subject,
                    "Soda machine buffId must be non-empty."));
        }
        if (sodaMachine.cost() < 0) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_INVALID_SODA_MACHINE,
                    subject,
                    "Soda machine cost must be non-negative."));
        }
    }

    private static void addUltimateMachineIssues(
            ZombiesMapSnapshot.UltimateMachineSnapshot ultimateMachine,
            boolean hasPowerSwitch,
            List<ZombiesValidationIssue> issues
    ) {
        String subject = subject("ultimate_machine", ultimateMachine.objectId(), ultimateMachine.featureKey());
        if (ultimateMachine.requiresPower() && !hasPowerSwitch) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_REQUIRES_POWER_WITHOUT_SWITCH,
                    subject,
                    "Ultimate machine requires power but the map has no power switch."));
        }
        if (ultimateMachine.maxUpgradeLevel() <= 0) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_INVALID_ULTIMATE_MACHINE,
                    subject,
                    "Ultimate machine maxUpgradeLevel must be positive."));
            return;
        }

        Set<Integer> configuredLevels = new HashSet<>();
        for (Map.Entry<String, ZombiesMapSnapshot.UltimateLevelSnapshot> entry : ultimateMachine.levels().entrySet()) {
            int level = parsePositiveLevel(entry.getKey());
            if (level < 0) {
                issues.add(ZombiesValidationIssue.error(
                        MAP_INVALID_ULTIMATE_MACHINE,
                        subject,
                        "Ultimate machine level keys must be positive integers."));
            } else {
                configuredLevels.add(level);
            }
            ZombiesMapSnapshot.UltimateLevelSnapshot levelData = entry.getValue();
            if (levelData == null || levelData.cost() < 0) {
                issues.add(ZombiesValidationIssue.error(
                        MAP_INVALID_ULTIMATE_MACHINE,
                        subject,
                        "Ultimate machine level cost must be non-negative."));
            }
            if (levelData == null || !Double.isFinite(levelData.damageMultiplier())
                    || levelData.damageMultiplier() <= 0.0D) {
                issues.add(ZombiesValidationIssue.error(
                        MAP_INVALID_ULTIMATE_MACHINE,
                        subject,
                        "Ultimate machine level damageMultiplier must be positive."));
            }
        }
        for (int level = 1; level <= ultimateMachine.maxUpgradeLevel(); level++) {
            if (!configuredLevels.contains(level)) {
                issues.add(ZombiesValidationIssue.error(
                        MAP_INVALID_ULTIMATE_MACHINE,
                        subject,
                        "Ultimate machine levels must cover 1..maxUpgradeLevel."));
                break;
            }
        }
    }

    private static int parsePositiveLevel(String value) {
        try {
            int level = Integer.parseInt(Objects.requireNonNullElse(value, "").trim());
            return level > 0 ? level : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static void addObjectId(
            String type,
            String objectId,
            String featureKey,
            Map<String, String> seenSubjects,
            List<ZombiesValidationIssue> issues
    ) {
        if (objectId == null || objectId.isBlank()) {
            return;
        }
        String subject = subject(type, objectId, featureKey);
        String previous = seenSubjects.putIfAbsent(objectId, subject);
        if (previous != null) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_DUPLICATE_OBJECT_ID,
                    subject,
                    "Duplicate objectId '" + objectId + "' also used by " + previous + "."));
        }
    }

    private static String subject(String type, String objectId, String featureKey) {
        String id = Objects.requireNonNullElse(objectId, "").trim();
        if (!id.isEmpty()) {
            return type + "." + id;
        }
        String feature = Objects.requireNonNullElse(featureKey, "").trim();
        return feature.isEmpty() ? type : type + "." + feature;
    }

    public ZombiesMapValidationProfile profile() {
        return profile;
    }
}
