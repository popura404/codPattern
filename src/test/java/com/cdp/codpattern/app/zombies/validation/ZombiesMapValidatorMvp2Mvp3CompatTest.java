package com.cdp.codpattern.app.zombies.validation;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.zombies.map.ZombiesMapSnapshot;

import java.util.List;
import java.util.Map;

public final class ZombiesMapValidatorMvp2Mvp3CompatTest {
    private static final RoomId ROOM_ID = RoomId.of("zombies", "validator_mvp2_mvp3_compat");

    private ZombiesMapValidatorMvp2Mvp3CompatTest() {
    }

    public static void main(String[] args) {
        mvp2WeaponWallHighestRarityWithoutCandidateFails();
        mvp3RequiresPowerSodaWithoutPowerSwitchFails();
        mvp3RequiresPowerUltimateWithoutPowerSwitchFails();
        mvp3NoPowerSwitchFails();
        mvp3MultiplePowerSwitchesFail();
        mvp3SinglePowerSwitchSucceeds();
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
                snapshot(List.of(), List.of(), List.of(), List.of()));

        require(report.hasErrors(), "MVP3 map without power switch should fail");
        requireIssue(report, "map.missing_power_switch");
    }

    private static void mvp3MultiplePowerSwitchesFail() {
        ZombiesMapValidationReport report = validate(
                ZombiesMapValidationProfile.MVP3_FULL_INITIAL,
                snapshot(
                        List.of(),
                        List.of(powerSwitch("power-1"), powerSwitch("power-2")),
                        List.of(),
                        List.of()));

        require(report.hasErrors(), "MVP3 map with multiple power switches should fail");
        requireIssue(report, "map.multiple_power_switches");
    }

    private static void mvp3SinglePowerSwitchSucceeds() {
        ZombiesMapValidationReport report = validate(
                ZombiesMapValidationProfile.MVP3_FULL_INITIAL,
                snapshot(List.of(), List.of(powerSwitch("power-1")), List.of(), List.of()));

        require(report.valid(), "MVP3 map with one power switch and valid M1 base should pass: " + issueCodes(report));
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
        return ZombiesMapSnapshot.of(
                ROOM_ID,
                ROOM_ID.mapName(),
                true,
                List.of(initialSpawn(), zombieSpawn()),
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
        return new ZombiesMapSnapshot.SpawnSnapshot("initial-1", "spawn", "INITIAL", 0, 0.0D, false);
    }

    private static ZombiesMapSnapshot.SpawnSnapshot zombieSpawn() {
        return new ZombiesMapSnapshot.SpawnSnapshot("zombie-1", "zombieSpawn", "", 1, 1.0D, true);
    }

    private static ZombiesMapSnapshot.PowerSwitchSnapshot powerSwitch(String objectId) {
        return new ZombiesMapSnapshot.PowerSwitchSnapshot(
                objectId,
                "powerSwitch",
                0,
                "codpattern:zombies_power_switch");
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
