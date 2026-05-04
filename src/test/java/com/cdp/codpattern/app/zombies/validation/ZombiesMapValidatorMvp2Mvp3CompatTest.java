package com.cdp.codpattern.app.zombies.validation;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.zombies.map.ZombiesMapSnapshot;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Map;

public final class ZombiesMapValidatorMvp2Mvp3CompatTest {
    private static final RoomId ROOM_ID = RoomId.of("zombies", "validator_mvp2_mvp3_compat");
    private static final String MAP_DIMENSION = "minecraft:overworld";
    private static final String OTHER_DIMENSION = "minecraft:the_nether";
    private static final ZombiesMapSnapshot.BoundsSnapshot MAP_BOUNDS =
            new ZombiesMapSnapshot.BoundsSnapshot(new BlockPos(0, 0, 0), new BlockPos(20, 20, 20));

    private ZombiesMapValidatorMvp2Mvp3CompatTest() {
    }

    public static void main(String[] args) {
        mvp2WeaponWallHighestRarityWithoutCandidateFails();
        mvp2WeaponWallNegativeMaxReserveAmmoFails();
        mvp3RequiresPowerSodaWithoutPowerSwitchFails();
        mvp3RequiresPowerUltimateWithoutPowerSwitchFails();
        mvp3NoPowerSwitchFails();
        mvp3MultiplePowerSwitchesFail();
        mvp3MissingSodaMachineFails();
        mvp3MissingUltimateMachineFails();
        mvp3InvalidSodaBuffFails();
        mvp3InvalidPowerSwitchIdentifierFails();
        mvp3UltimateMissingLevelFails();
        mvp3UltimateInvalidDamageMultiplierFails();
        mvp3SpawnMissingLocationFails();
        mvp3RequiredObjectMissingLocationFails();
        mvp3RequiredObjectCrossDimensionFails();
        mvp3RequiredObjectOutOfBoundsFails();
        mvp3SpawnOutOfBoundsFails();
        mvp3FullInitialSnapshotSucceeds();
    }

    private static void mvp2WeaponWallHighestRarityWithoutCandidateFails() {
        ZombiesMapSnapshot.WeaponWallSnapshot wall = new ZombiesMapSnapshot.WeaponWallSnapshot(
                "wall-1",
                "weaponWall",
                1,
                1.0D,
                500,
                List.of(),
                List.of(
                        new ZombiesMapSnapshot.RarityPoolSnapshot("common", 1, 1.0D, 0.0D),
                        new ZombiesMapSnapshot.RarityPoolSnapshot("rare", 2, 1.0D, 0.0D)),
                List.of(new ZombiesMapSnapshot.WeaponCandidateSnapshot(
                        "tacz:ak47",
                        Map.of("common", 1.0D))));

        ZombiesMapValidationReport report = validate(
                ZombiesMapValidationProfile.MVP2_PURCHASES,
                snapshot(List.of(wall), List.of(), List.of(), List.of()));

        require(report.hasErrors(), "MVP2 weapon wall without top-rarity candidate should fail");
        requireIssue(report, "map.weapon_wall_missing_top_rarity_candidate");
    }

    private static void mvp2WeaponWallNegativeMaxReserveAmmoFails() {
        ZombiesMapSnapshot.WeaponWallSnapshot wall = new ZombiesMapSnapshot.WeaponWallSnapshot(
                "wall-1",
                "weaponWall",
                1,
                1.0D,
                500,
                -1,
                List.of(),
                List.of(new ZombiesMapSnapshot.RarityPoolSnapshot("common", 1, 1.0D, 0.0D)),
                List.of(new ZombiesMapSnapshot.WeaponCandidateSnapshot(
                        "tacz:ak47",
                        Map.of("common", 1.0D))));

        ZombiesMapValidationReport report = validate(
                ZombiesMapValidationProfile.MVP2_PURCHASES,
                snapshot(List.of(wall), List.of(), List.of(), List.of()));

        require(report.hasErrors(), "MVP2 weapon wall with negative maxReserveAmmo should fail");
        requireIssue(report, "map.invalid_weapon_wall");
    }

    private static void mvp3RequiresPowerSodaWithoutPowerSwitchFails() {
        ZombiesMapSnapshot.SodaMachineSnapshot soda = new ZombiesMapSnapshot.SodaMachineSnapshot(
                "soda-1",
                "sodaMachine",
                "quick_revive",
                1500,
                true);

        ZombiesMapValidationReport report = validate(
                ZombiesMapValidationProfile.MVP3_FULL_INITIAL,
                snapshot(List.of(), List.of(), List.of(soda), List.of()));

        require(report.hasErrors(), "MVP3 requiresPower soda without power switch should fail");
        requireIssue(report, "map.requires_power_without_switch");
        requireIssue(report, "map.missing_power_switch");
    }

    private static void mvp3RequiresPowerUltimateWithoutPowerSwitchFails() {
        ZombiesMapSnapshot.UltimateMachineSnapshot ultimate = new ZombiesMapSnapshot.UltimateMachineSnapshot(
                "ultimate-1",
                "ultimateMachine",
                2,
                Map.of(
                        "1", new ZombiesMapSnapshot.UltimateLevelSnapshot(2500, 1.25D),
                        "2", new ZombiesMapSnapshot.UltimateLevelSnapshot(5000, 1.5D)),
                true);

        ZombiesMapValidationReport report = validate(
                ZombiesMapValidationProfile.MVP3_FULL_INITIAL,
                snapshot(List.of(), List.of(), List.of(), List.of(ultimate)));

        require(report.hasErrors(), "MVP3 requiresPower ultimate without power switch should fail");
        requireIssue(report, "map.requires_power_without_switch");
        requireIssue(report, "map.missing_power_switch");
    }

    private static void mvp3NoPowerSwitchFails() {
        ZombiesMapValidationReport report = validate(
                ZombiesMapValidationProfile.MVP3_FULL_INITIAL,
                snapshot(List.of(), List.of(), List.of(validSoda()), List.of(validUltimate())));

        require(report.hasErrors(), "MVP3 map without power switch should fail");
        requireIssue(report, "map.missing_power_switch");
    }

    private static void mvp3MultiplePowerSwitchesFail() {
        ZombiesMapValidationReport report = validate(
                ZombiesMapValidationProfile.MVP3_FULL_INITIAL,
                snapshot(
                        List.of(),
                        List.of(powerSwitch("power-1"), powerSwitch("power-2")),
                        List.of(validSoda()),
                        List.of(validUltimate())));

        require(report.hasErrors(), "MVP3 map with multiple power switches should fail");
        requireIssue(report, "map.multiple_power_switches");
    }

    private static void mvp3MissingSodaMachineFails() {
        ZombiesMapValidationReport report = validate(
                ZombiesMapValidationProfile.MVP3_FULL_INITIAL,
                snapshot(List.of(), List.of(powerSwitch("power-1")), List.of(), List.of(validUltimate())));

        require(report.hasErrors(), "MVP3 full initial map without soda machine should fail");
        requireIssue(report, "map.missing_soda_machine");
    }

    private static void mvp3MissingUltimateMachineFails() {
        ZombiesMapValidationReport report = validate(
                ZombiesMapValidationProfile.MVP3_FULL_INITIAL,
                snapshot(List.of(), List.of(powerSwitch("power-1")), List.of(validSoda()), List.of()));

        require(report.hasErrors(), "MVP3 full initial map without ultimate machine should fail");
        requireIssue(report, "map.missing_ultimate_machine");
    }

    private static void mvp3InvalidSodaBuffFails() {
        ZombiesMapSnapshot.SodaMachineSnapshot soda = new ZombiesMapSnapshot.SodaMachineSnapshot(
                "soda-1",
                "sodaMachine",
                "quick_revive",
                1500,
                true);
        ZombiesMapValidationReport report = validate(
                ZombiesMapValidationProfile.MVP3_FULL_INITIAL,
                snapshot(List.of(), List.of(powerSwitch("power-1")), List.of(soda), List.of(validUltimate())));

        require(report.hasErrors(), "MVP3 soda machine with unsupported buff should fail");
        requireIssue(report, "map.invalid_soda_machine");
    }

    private static void mvp3InvalidPowerSwitchIdentifierFails() {
        ZombiesMapSnapshot.PowerSwitchSnapshot powerSwitch = new ZombiesMapSnapshot.PowerSwitchSnapshot(
                "power-1",
                "lever",
                0,
                "minecraft:lever");
        ZombiesMapValidationReport report = validate(
                ZombiesMapValidationProfile.MVP3_FULL_INITIAL,
                snapshot(List.of(), List.of(powerSwitch), List.of(validSoda()), List.of(validUltimate())));

        require(report.hasErrors(), "MVP3 power switch with invalid feature/block identifier should fail");
        requireIssue(report, "map.invalid_power_switch");
    }

    private static void mvp3UltimateMissingLevelFails() {
        ZombiesMapSnapshot.UltimateMachineSnapshot ultimate = new ZombiesMapSnapshot.UltimateMachineSnapshot(
                "ultimate-1",
                "ultimateMachine",
                3,
                Map.of(
                        "1", new ZombiesMapSnapshot.UltimateLevelSnapshot(1200, 1.25D),
                        "3", new ZombiesMapSnapshot.UltimateLevelSnapshot(5000, 2.0D)),
                true);
        ZombiesMapValidationReport report = validate(
                ZombiesMapValidationProfile.MVP3_FULL_INITIAL,
                snapshot(List.of(), List.of(powerSwitch("power-1")), List.of(validSoda()), List.of(ultimate)));

        require(report.hasErrors(), "MVP3 ultimate machine missing a configured level should fail");
        requireIssue(report, "map.invalid_ultimate_machine");
    }

    private static void mvp3UltimateInvalidDamageMultiplierFails() {
        ZombiesMapSnapshot.UltimateMachineSnapshot ultimate = new ZombiesMapSnapshot.UltimateMachineSnapshot(
                "ultimate-1",
                "ultimateMachine",
                2,
                Map.of(
                        "1", new ZombiesMapSnapshot.UltimateLevelSnapshot(1200, 1.25D),
                        "2", new ZombiesMapSnapshot.UltimateLevelSnapshot(2500, Double.NaN)),
                true);
        ZombiesMapValidationReport report = validate(
                ZombiesMapValidationProfile.MVP3_FULL_INITIAL,
                snapshot(List.of(), List.of(powerSwitch("power-1")), List.of(validSoda()), List.of(ultimate)));

        require(report.hasErrors(), "MVP3 ultimate machine with invalid damage multiplier should fail");
        requireIssue(report, "map.invalid_ultimate_machine");
    }

    private static void mvp3SpawnMissingLocationFails() {
        ZombiesMapValidationReport report = validate(
                ZombiesMapValidationProfile.MVP3_FULL_INITIAL,
                snapshot(
                        List.of(
                                new ZombiesMapSnapshot.SpawnSnapshot(
                                        "initial-1", "spawn", "INITIAL", 0, 0.0D, false),
                                zombieSpawn()),
                        List.of(),
                        List.of(powerSwitch("power-1")),
                        List.of(validSoda()),
                        List.of(validUltimate())));

        require(report.hasErrors(), "MVP3 spawn without dimension/position should fail");
        requireIssue(report, "map.object_missing_location");
    }

    private static void mvp3RequiredObjectMissingLocationFails() {
        ZombiesMapValidationReport report = validate(
                ZombiesMapValidationProfile.MVP3_FULL_INITIAL,
                snapshot(
                        List.of(),
                        List.of(new ZombiesMapSnapshot.PowerSwitchSnapshot(
                                "power-1",
                                "powerSwitch",
                                0,
                                "codpattern:zombies_power_switch")),
                        List.of(validSoda()),
                        List.of(validUltimate())));

        require(report.hasErrors(), "MVP3 required object without dimension/position should fail");
        requireIssue(report, "map.object_missing_location");
    }

    private static void mvp3RequiredObjectCrossDimensionFails() {
        ZombiesMapSnapshot.SodaMachineSnapshot soda = new ZombiesMapSnapshot.SodaMachineSnapshot(
                "soda-1",
                "sodaMachine",
                "double_health",
                1500,
                true,
                OTHER_DIMENSION,
                new BlockPos(4, 1, 4));

        ZombiesMapValidationReport report = validate(
                ZombiesMapValidationProfile.MVP3_FULL_INITIAL,
                snapshot(List.of(), List.of(powerSwitch("power-1")), List.of(soda), List.of(validUltimate())));

        require(report.hasErrors(), "MVP3 required object in another dimension should fail");
        requireIssue(report, "map.object_dimension_mismatch");
    }

    private static void mvp3RequiredObjectOutOfBoundsFails() {
        ZombiesMapSnapshot.PowerSwitchSnapshot powerSwitch = new ZombiesMapSnapshot.PowerSwitchSnapshot(
                "power-1",
                "powerSwitch",
                0,
                "codpattern:zombies_power_switch",
                MAP_DIMENSION,
                new BlockPos(99, 1, 3));

        ZombiesMapValidationReport report = validate(
                ZombiesMapValidationProfile.MVP3_FULL_INITIAL,
                snapshot(List.of(), List.of(powerSwitch), List.of(validSoda()), List.of(validUltimate())));

        require(report.hasErrors(), "MVP3 required object outside map bounds should fail");
        requireIssue(report, "map.object_out_of_bounds");
    }

    private static void mvp3SpawnOutOfBoundsFails() {
        ZombiesMapValidationReport report = validate(
                ZombiesMapValidationProfile.MVP3_FULL_INITIAL,
                snapshot(
                        List.of(initialSpawn(), zombieSpawn("zombie-1", new BlockPos(2, 1, 99))),
                        List.of(),
                        List.of(powerSwitch("power-1")),
                        List.of(validSoda()),
                        List.of(validUltimate())));

        require(report.hasErrors(), "MVP3 spawn outside map bounds should fail");
        requireIssue(report, "map.object_out_of_bounds");
    }

    private static void mvp3FullInitialSnapshotSucceeds() {
        ZombiesMapValidationReport report = validate(
                ZombiesMapValidationProfile.MVP3_FULL_INITIAL,
                snapshot(
                        List.of(),
                        List.of(powerSwitch("power-1")),
                        List.of(validSoda()),
                        List.of(validUltimate())));

        require(report.valid(), "MVP3 full initial snapshot should pass: " + issueCodes(report));
    }

    private static ZombiesMapValidationReport validate(
            ZombiesMapValidationProfile profile,
            ZombiesMapSnapshot snapshot
    ) {
        return new ZombiesMapValidator(profile).validate(snapshot);
    }

    private static ZombiesMapSnapshot snapshot(
            List<ZombiesMapSnapshot.WeaponWallSnapshot> weaponWalls,
            List<ZombiesMapSnapshot.PowerSwitchSnapshot> powerSwitches,
            List<ZombiesMapSnapshot.SodaMachineSnapshot> sodaMachines,
            List<ZombiesMapSnapshot.UltimateMachineSnapshot> ultimateMachines
    ) {
        return snapshot(
                List.of(initialSpawn(), zombieSpawn()),
                weaponWalls,
                powerSwitches,
                sodaMachines,
                ultimateMachines);
    }

    private static ZombiesMapSnapshot snapshot(
            List<ZombiesMapSnapshot.SpawnSnapshot> spawns,
            List<ZombiesMapSnapshot.WeaponWallSnapshot> weaponWalls,
            List<ZombiesMapSnapshot.PowerSwitchSnapshot> powerSwitches,
            List<ZombiesMapSnapshot.SodaMachineSnapshot> sodaMachines,
            List<ZombiesMapSnapshot.UltimateMachineSnapshot> ultimateMachines
    ) {
        return ZombiesMapSnapshot.of(
                ROOM_ID,
                ROOM_ID.mapName(),
                true,
                MAP_DIMENSION,
                MAP_BOUNDS,
                spawns,
                List.of(),
                weaponWalls,
                List.of(),
                List.of(),
                powerSwitches,
                sodaMachines,
                ultimateMachines,
                List.of());
    }

    private static ZombiesMapSnapshot.SpawnSnapshot initialSpawn() {
        return new ZombiesMapSnapshot.SpawnSnapshot(
                "initial-1",
                "spawn",
                "INITIAL",
                0,
                0.0D,
                false,
                MAP_DIMENSION,
                new BlockPos(1, 1, 1));
    }

    private static ZombiesMapSnapshot.SpawnSnapshot zombieSpawn() {
        return zombieSpawn("zombie-1", new BlockPos(2, 1, 2));
    }

    private static ZombiesMapSnapshot.SpawnSnapshot zombieSpawn(String objectId, BlockPos pos) {
        return new ZombiesMapSnapshot.SpawnSnapshot(
                objectId,
                "zombieSpawn",
                "",
                1,
                1.0D,
                true,
                MAP_DIMENSION,
                pos);
    }

    private static ZombiesMapSnapshot.PowerSwitchSnapshot powerSwitch(String objectId) {
        return new ZombiesMapSnapshot.PowerSwitchSnapshot(
                objectId,
                "powerSwitch",
                0,
                "codpattern:zombies_power_switch",
                MAP_DIMENSION,
                new BlockPos(3, 1, 3));
    }

    private static ZombiesMapSnapshot.SodaMachineSnapshot validSoda() {
        return new ZombiesMapSnapshot.SodaMachineSnapshot(
                "soda-1",
                "sodaMachine",
                "double_health",
                1500,
                true,
                MAP_DIMENSION,
                new BlockPos(4, 1, 4));
    }

    private static ZombiesMapSnapshot.UltimateMachineSnapshot validUltimate() {
        return new ZombiesMapSnapshot.UltimateMachineSnapshot(
                "ultimate-1",
                "ultimateMachine",
                3,
                Map.of(
                        "1", new ZombiesMapSnapshot.UltimateLevelSnapshot(1200, 1.25D),
                        "2", new ZombiesMapSnapshot.UltimateLevelSnapshot(2500, 1.5D),
                        "3", new ZombiesMapSnapshot.UltimateLevelSnapshot(5000, 2.0D)),
                true,
                MAP_DIMENSION,
                new BlockPos(5, 1, 5));
    }

    private static void requireIssue(ZombiesMapValidationReport report, String code) {
        require(report.issues().stream().anyMatch(issue -> code.equals(issue.code().key())),
                "expected issue " + code + ", got " + issueCodes(report));
    }

    private static String issueCodes(ZombiesMapValidationReport report) {
        return report.issues().stream()
                .map(issue -> issue.code().key())
                .toList()
                .toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
