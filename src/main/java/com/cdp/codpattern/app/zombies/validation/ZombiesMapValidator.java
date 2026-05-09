package com.cdp.codpattern.app.zombies.validation;

import com.cdp.codpattern.app.zombies.map.ZombiesMapSnapshot;
import com.cdp.codpattern.app.zombies.map.ZombiesMatchSnapshot;
import com.cdp.codpattern.app.zombies.service.ZombiesErrorCode;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
    private static final ZombiesErrorCode MAP_OBJECT_MISSING_LOCATION =
            ZombiesErrorCode.of("map.object_missing_location");
    private static final ZombiesErrorCode MAP_OBJECT_DIMENSION_MISMATCH =
            ZombiesErrorCode.of("map.object_dimension_mismatch");
    private static final ZombiesErrorCode MAP_OBJECT_OUT_OF_BOUNDS =
            ZombiesErrorCode.of("map.object_out_of_bounds");
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
    private static final ZombiesErrorCode MAP_INVALID_POWER_SWITCH =
            ZombiesErrorCode.of("map.invalid_power_switch");
    private static final ZombiesErrorCode MAP_MISSING_SODA_MACHINE =
            ZombiesErrorCode.of("map.missing_soda_machine");
    private static final ZombiesErrorCode MAP_INVALID_SODA_MACHINE =
            ZombiesErrorCode.of("map.invalid_soda_machine");
    private static final ZombiesErrorCode MAP_MISSING_ULTIMATE_MACHINE =
            ZombiesErrorCode.of("map.missing_ultimate_machine");
    private static final ZombiesErrorCode MAP_INVALID_ULTIMATE_MACHINE =
            ZombiesErrorCode.of("map.invalid_ultimate_machine");
    private static final ZombiesErrorCode MAP_MISSING_VALID_WAVES =
            ZombiesErrorCode.of("map.missing_valid_waves");
    private static final ZombiesErrorCode MAP_MISSING_WEAPON_WALL =
            ZombiesErrorCode.of("map.missing_weapon_wall");
    private static final ZombiesErrorCode MAP_MISSING_AMMO_BOX =
            ZombiesErrorCode.of("map.missing_ammo_box");
    private static final ZombiesErrorCode MAP_MISSING_ARMOR_STATION =
            ZombiesErrorCode.of("map.missing_armor_station");
    private static final ZombiesErrorCode MAP_MISSING_BARRIER =
            ZombiesErrorCode.of("map.missing_barrier");
    private static final ZombiesErrorCode MAP_BARRIER_GROUP_WITHOUT_ZOMBIE_SPAWN =
            ZombiesErrorCode.of("map.barrier_group_without_zombie_spawn");
    private static final Set<String> POWER_SWITCH_FEATURE_KEYS = Set.of(
            "powerswitch",
            "power_switch",
            "zombies_power_switch",
            "codpattern:zombies_power_switch");
    private static final Set<String> POWER_SWITCH_BLOCKS = Set.of("codpattern:zombies_power_switch");
    private static final Set<String> MVP3_INITIAL_BUFF_IDS = Set.of(
            "double_health",
            "speed_boost",
            "reactive_explosion",
            "double_ammo",
            "score_multiplier",
            "headshot_damage");
    private static final int MIN_GENERATION_COORDINATE = -30_000_000;
    private static final int MAX_GENERATION_COORDINATE = 30_000_000;

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
        if (ZombiesMapValidationProfile.MVP1_MINIMAL_KEY.equals(profile.key())
                && snapshot.weaponWalls().stream().noneMatch(wall ->
                        !wall.refreshWaves().isEmpty()
                                && wall.refreshWaves().stream().anyMatch(wave -> wave != null && wave > 0))) {
            issues.add(ZombiesValidationIssue.warning(
                    MAP_MISSING_VALID_WAVES,
                    "weapon_wall",
                    "Zombies map should have at least one weapon wall with valid refresh waves (non-empty with positive values)."));
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
        if (snapshot.weaponWalls().isEmpty()) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_MISSING_WEAPON_WALL,
                    "weapon_wall",
                    "Zombies map requires at least one weapon wall."));
        }
        if (snapshot.ammoBoxes().isEmpty()) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_MISSING_AMMO_BOX,
                    "ammo_box",
                    "Zombies map requires at least one ammo box."));
        }
        if (snapshot.armorStations().isEmpty()) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_MISSING_ARMOR_STATION,
                    "armor_station",
                    "Zombies map requires at least one armor station."));
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
        if (weaponWall.maxReserveAmmo() < 0) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_INVALID_WEAPON_WALL,
                    subject,
                    "Weapon wall maxReserveAmmo must be non-negative."));
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
        addFullInitialLocationIssues(snapshot, issues);

        for (ZombiesMapSnapshot.PowerSwitchSnapshot powerSwitch : snapshot.powerSwitches()) {
            addPowerSwitchIssues(powerSwitch, issues);
        }

        for (ZombiesMapSnapshot.BarrierSnapshot barrier : snapshot.barriers()) {
            String subject = subject("barrier", barrier.objectId(), barrier.featureKey());
            if (barrier.group() >= 2 && barrier.cost() < 0) {
                issues.add(ZombiesValidationIssue.error(
                        MAP_INVALID_BARRIER,
                        subject,
                        "Barrier groups unlocked by purchases require non-negative cost."));
            }
        }
        if (snapshot.barriers().isEmpty()) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_MISSING_BARRIER,
                    "barrier",
                    "MVP3 zombies maps require at least one barrier."));
        }
        Set<Integer> zombieSpawnGroups = new HashSet<>();
        for (ZombiesMapSnapshot.SpawnSnapshot spawn : snapshot.spawns()) {
            if (spawn.zombieSpawn()) {
                zombieSpawnGroups.add(spawn.group());
            }
        }
        for (ZombiesMapSnapshot.BarrierSnapshot barrier : snapshot.barriers()) {
            if (!zombieSpawnGroups.contains(barrier.group())) {
                issues.add(ZombiesValidationIssue.warning(
                        MAP_BARRIER_GROUP_WITHOUT_ZOMBIE_SPAWN,
                        subject("barrier", barrier.objectId(), barrier.featureKey()),
                        "Barrier group " + barrier.group() + " has no corresponding zombie spawn with matching group."));
            }
        }

        if (snapshot.sodaMachines().isEmpty()) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_MISSING_SODA_MACHINE,
                    "soda_machine",
                    "MVP3 zombies maps require at least one soda machine."));
        }
        for (ZombiesMapSnapshot.SodaMachineSnapshot sodaMachine : snapshot.sodaMachines()) {
            addSodaMachineIssues(sodaMachine, issues);
        }
        if (snapshot.ultimateMachines().isEmpty()) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_MISSING_ULTIMATE_MACHINE,
                    "ultimate_machine",
                    "MVP3 zombies maps require at least one ultimate machine."));
        }
        for (ZombiesMapSnapshot.UltimateMachineSnapshot ultimateMachine : snapshot.ultimateMachines()) {
            addUltimateMachineIssues(ultimateMachine, issues);
        }
    }

    private static void addFullInitialLocationIssues(
            ZombiesMapSnapshot snapshot,
            List<ZombiesValidationIssue> issues
    ) {
        String expectedDimensionId = expectedDimensionId(snapshot);
        ZombiesMapSnapshot.BoundsSnapshot mapBounds = snapshot.mapBounds();

        for (ZombiesMapSnapshot.SpawnSnapshot spawn : snapshot.spawns()) {
            addRequiredLocationIssues(
                    subject("spawn", spawn.objectId(), spawn.featureKey()),
                    spawn.dimensionId(),
                    spawn.pos(),
                    expectedDimensionId,
                    mapBounds,
                    issues);
        }
        for (ZombiesMapSnapshot.BarrierSnapshot barrier : snapshot.barriers()) {
            addOptionalLocationIssues(
                    subject("barrier", barrier.objectId(), barrier.featureKey()),
                    barrier.dimensionId(),
                    barrier.pos(),
                    expectedDimensionId,
                    mapBounds,
                    issues);
        }
        for (ZombiesMapSnapshot.WeaponWallSnapshot weaponWall : snapshot.weaponWalls()) {
            addOptionalLocationIssues(
                    subject("weapon_wall", weaponWall.objectId(), weaponWall.featureKey()),
                    weaponWall.dimensionId(),
                    weaponWall.pos(),
                    expectedDimensionId,
                    mapBounds,
                    issues);
        }
        for (ZombiesMapSnapshot.AmmoBoxSnapshot ammoBox : snapshot.ammoBoxes()) {
            addOptionalLocationIssues(
                    subject("ammo_box", ammoBox.objectId(), ammoBox.featureKey()),
                    ammoBox.dimensionId(),
                    ammoBox.pos(),
                    expectedDimensionId,
                    mapBounds,
                    issues);
        }
        for (ZombiesMapSnapshot.ArmorStationSnapshot armorStation : snapshot.armorStations()) {
            addOptionalLocationIssues(
                    subject("armor_station", armorStation.objectId(), armorStation.featureKey()),
                    armorStation.dimensionId(),
                    armorStation.pos(),
                    expectedDimensionId,
                    mapBounds,
                    issues);
        }
        for (ZombiesMapSnapshot.PowerSwitchSnapshot powerSwitch : snapshot.powerSwitches()) {
            addRequiredLocationIssues(
                    subject("power_switch", powerSwitch.objectId(), powerSwitch.featureKey()),
                    powerSwitch.dimensionId(),
                    powerSwitch.pos(),
                    expectedDimensionId,
                    mapBounds,
                    issues);
        }
        for (ZombiesMapSnapshot.SodaMachineSnapshot sodaMachine : snapshot.sodaMachines()) {
            addRequiredLocationIssues(
                    subject("soda_machine", sodaMachine.objectId(), sodaMachine.featureKey()),
                    sodaMachine.dimensionId(),
                    sodaMachine.pos(),
                    expectedDimensionId,
                    mapBounds,
                    issues);
        }
        for (ZombiesMapSnapshot.UltimateMachineSnapshot ultimateMachine : snapshot.ultimateMachines()) {
            addRequiredLocationIssues(
                    subject("ultimate_machine", ultimateMachine.objectId(), ultimateMachine.featureKey()),
                    ultimateMachine.dimensionId(),
                    ultimateMachine.pos(),
                    expectedDimensionId,
                    mapBounds,
                    issues);
        }
        for (ZombiesMapSnapshot.ObjectIdSnapshot object : snapshot.extraObjects()) {
            addOptionalLocationIssues(
                    subject("object", object.objectId(), object.featureKey()),
                    object.dimensionId(),
                    object.pos(),
                    expectedDimensionId,
                    mapBounds,
                    issues);
        }
    }

    private static void addRequiredLocationIssues(
            String subject,
            String dimensionId,
            BlockPos pos,
            String expectedDimensionId,
            ZombiesMapSnapshot.BoundsSnapshot mapBounds,
            List<ZombiesValidationIssue> issues
    ) {
        if (normalizeKey(dimensionId).isEmpty() || pos == null) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_OBJECT_MISSING_LOCATION,
                    subject,
                    "MVP3 zombies map entries require non-empty dimension and position."));
        }
        addLocationConsistencyIssues(subject, dimensionId, pos, expectedDimensionId, mapBounds, issues);
    }

    private static void addOptionalLocationIssues(
            String subject,
            String dimensionId,
            BlockPos pos,
            String expectedDimensionId,
            ZombiesMapSnapshot.BoundsSnapshot mapBounds,
            List<ZombiesValidationIssue> issues
    ) {
        boolean hasDimension = !normalizeKey(dimensionId).isEmpty();
        boolean hasPosition = pos != null;
        if (!hasDimension && !hasPosition) {
            return;
        }
        if (!hasDimension || !hasPosition) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_OBJECT_MISSING_LOCATION,
                    subject,
                    "Located zombies map entries require both dimension and position."));
        }
        addLocationConsistencyIssues(subject, dimensionId, pos, expectedDimensionId, mapBounds, issues);
    }

    private static void addLocationConsistencyIssues(
            String subject,
            String dimensionId,
            BlockPos pos,
            String expectedDimensionId,
            ZombiesMapSnapshot.BoundsSnapshot mapBounds,
            List<ZombiesValidationIssue> issues
    ) {
        String normalizedDimensionId = normalizeKey(dimensionId);
        String normalizedExpectedDimensionId = normalizeKey(expectedDimensionId);
        if (!normalizedDimensionId.isEmpty()
                && !normalizedExpectedDimensionId.isEmpty()
                && !normalizedDimensionId.equals(normalizedExpectedDimensionId)) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_OBJECT_DIMENSION_MISMATCH,
                    subject,
                    "Zombies map entry dimension must match the map and spawn dimension."));
        }
        if (pos == null) {
            return;
        }
        if (!insideGenerationBoundary(pos)) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_OBJECT_OUT_OF_BOUNDS,
                    subject,
                    "Zombies map entry position is outside the supported generation boundary."));
        }
        if (mapBounds != null && !mapBounds.contains(pos)) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_OBJECT_OUT_OF_BOUNDS,
                    subject,
                    "Zombies map entry position must be inside the map bounds."));
        }
    }

    private static String expectedDimensionId(ZombiesMapSnapshot snapshot) {
        String mapDimensionId = normalizeKey(snapshot.mapDimensionId());
        if (!mapDimensionId.isEmpty()) {
            return mapDimensionId;
        }
        for (ZombiesMapSnapshot.SpawnSnapshot spawn : snapshot.spawns()) {
            if (spawn.initialPlayerSpawn() && !normalizeKey(spawn.dimensionId()).isEmpty()) {
                return normalizeKey(spawn.dimensionId());
            }
        }
        for (ZombiesMapSnapshot.SpawnSnapshot spawn : snapshot.spawns()) {
            if (spawn.zombieSpawn() && !normalizeKey(spawn.dimensionId()).isEmpty()) {
                return normalizeKey(spawn.dimensionId());
            }
        }
        for (ZombiesMapSnapshot.SpawnSnapshot spawn : snapshot.spawns()) {
            if (!normalizeKey(spawn.dimensionId()).isEmpty()) {
                return normalizeKey(spawn.dimensionId());
            }
        }
        for (ZombiesMapSnapshot.PowerSwitchSnapshot powerSwitch : snapshot.powerSwitches()) {
            if (!normalizeKey(powerSwitch.dimensionId()).isEmpty()) {
                return normalizeKey(powerSwitch.dimensionId());
            }
        }
        for (ZombiesMapSnapshot.SodaMachineSnapshot sodaMachine : snapshot.sodaMachines()) {
            if (!normalizeKey(sodaMachine.dimensionId()).isEmpty()) {
                return normalizeKey(sodaMachine.dimensionId());
            }
        }
        for (ZombiesMapSnapshot.UltimateMachineSnapshot ultimateMachine : snapshot.ultimateMachines()) {
            if (!normalizeKey(ultimateMachine.dimensionId()).isEmpty()) {
                return normalizeKey(ultimateMachine.dimensionId());
            }
        }
        return "";
    }

    private static boolean insideGenerationBoundary(BlockPos pos) {
        return pos.getX() >= MIN_GENERATION_COORDINATE
                && pos.getX() <= MAX_GENERATION_COORDINATE
                && pos.getZ() >= MIN_GENERATION_COORDINATE
                && pos.getZ() <= MAX_GENERATION_COORDINATE;
    }

    private static void addPowerSwitchIssues(
            ZombiesMapSnapshot.PowerSwitchSnapshot powerSwitch,
            List<ZombiesValidationIssue> issues
    ) {
        String subject = subject("power_switch", powerSwitch.objectId(), powerSwitch.featureKey());
        if (powerSwitch.cost() < 0) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_INVALID_POWER_SWITCH,
                    subject,
                    "Power switch cost must be non-negative."));
        }
        if (powerSwitch.featureKey().isBlank() || !POWER_SWITCH_FEATURE_KEYS.contains(normalizeKey(powerSwitch.featureKey()))) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_INVALID_POWER_SWITCH,
                    subject,
                    "Power switch featureKey must identify a zombies power switch."));
        }
        if (powerSwitch.block().isBlank() || !POWER_SWITCH_BLOCKS.contains(normalizeKey(powerSwitch.block()))) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_INVALID_POWER_SWITCH,
                    subject,
                    "Power switch block must be codpattern:zombies_power_switch."));
        }
    }

    private static void addSodaMachineIssues(
            ZombiesMapSnapshot.SodaMachineSnapshot sodaMachine,
            List<ZombiesValidationIssue> issues
    ) {
        String subject = subject("soda_machine", sodaMachine.objectId(), sodaMachine.featureKey());
        if (sodaMachine.buffId().isBlank()) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_INVALID_SODA_MACHINE,
                    subject,
                    "Soda machine buffId must be non-empty."));
        } else if (!MVP3_INITIAL_BUFF_IDS.contains(normalizeKey(sodaMachine.buffId()))) {
            issues.add(ZombiesValidationIssue.error(
                    MAP_INVALID_SODA_MACHINE,
                    subject,
                    "Soda machine buffId is not in the MVP3 initial allowed set."));
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
            List<ZombiesValidationIssue> issues
    ) {
        String subject = subject("ultimate_machine", ultimateMachine.objectId(), ultimateMachine.featureKey());
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

    private static String normalizeKey(String value) {
        return Objects.requireNonNullElse(value, "").trim().toLowerCase(Locale.ROOT);
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
