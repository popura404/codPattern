package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.zombies.map.ZombiesMapSnapshot;
import com.cdp.codpattern.app.zombies.service.ZombiesSpawnAssignmentService.ZombiesSpawnAssignment;
import com.cdp.codpattern.app.zombies.service.ZombiesSpawnAssignmentService.ZombiesSpawnAssignmentPlan;
import com.cdp.codpattern.app.zombies.service.ZombiesSpawnAssignmentService.ZombiesSpawnTeleportSummary;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class ZombiesSpawnAssignmentServiceCompatTest {
    private static final UUID PLAYER_ONE = uuid(1);
    private static final UUID PLAYER_TWO = uuid(2);
    private static final UUID PLAYER_THREE = uuid(3);
    private static final UUID PLAYER_FOUR = uuid(4);

    private ZombiesSpawnAssignmentServiceCompatTest() {
    }

    public static void main(String[] args) {
        emptyMembersWithoutInitialSpawnsSucceedsWithEmptyPlan();
        membersWithoutInitialSpawnsFailStartupTeleport();
        memberIdsAreDeduplicatedInEncounterOrder();
        assignmentsRotateAcrossInitialSpawnCount();
        assignFromSnapshotCountsOnlyInitialPlayerSpawns();
        emptyTeleportPlanSucceeds();
    }

    private static void emptyMembersWithoutInitialSpawnsSucceedsWithEmptyPlan() {
        ZombiesSpawnAssignmentService service = new ZombiesSpawnAssignmentService();

        ZombiesServiceResult<ZombiesSpawnAssignmentPlan> result = service.assignFromSnapshot(snapshot(), List.of());

        require(result.success(), "empty members without INITIAL spawns should succeed");
        require(result.code().equals(ZombiesErrorCode.OK), "empty success should use OK code");
        ZombiesSpawnAssignmentPlan plan = requireValue(result);
        require(plan.initialSpawnCount() == 0, "empty plan should report zero INITIAL spawns");
        require(plan.playerCount() == 0, "empty plan should have zero players");
        require(plan.assignments().isEmpty(), "empty plan should have no assignments");
    }

    private static void membersWithoutInitialSpawnsFailStartupTeleport() {
        ZombiesSpawnAssignmentService service = new ZombiesSpawnAssignmentService();

        ZombiesServiceResult<ZombiesSpawnAssignmentPlan> result = service.assignFromSnapshot(
                snapshot(dynamicPlayerSpawn("dynamic"), zombieSpawn("zombie")),
                List.of(PLAYER_ONE));

        require(!result.success(), "members without INITIAL spawns should fail");
        require(result.code().equals(ZombiesErrorCode.STARTUP_TELEPORT_FAILED),
                "missing INITIAL failure should use STARTUP_TELEPORT_FAILED");
        require(result.value().isEmpty(), "missing INITIAL failure should not return a plan");
    }

    private static void memberIdsAreDeduplicatedInEncounterOrder() {
        ZombiesSpawnAssignmentService service = new ZombiesSpawnAssignmentService();

        ZombiesServiceResult<ZombiesSpawnAssignmentPlan> result = service.assignFromSnapshot(
                snapshot(initialPlayerSpawn("initial-a"), initialPlayerSpawn("initial-b")),
                Arrays.asList(PLAYER_TWO, PLAYER_ONE, PLAYER_TWO, null, PLAYER_THREE, PLAYER_ONE));

        require(result.success(), "members with INITIAL spawns should succeed");
        List<ZombiesSpawnAssignment> assignments = requireValue(result).assignments();
        require(assignments.size() == 3, "duplicates and null member ids should be removed");
        require(assignments.get(0).playerId().equals(PLAYER_TWO), "first unique member should be PLAYER_TWO");
        require(assignments.get(1).playerId().equals(PLAYER_ONE), "second unique member should be PLAYER_ONE");
        require(assignments.get(2).playerId().equals(PLAYER_THREE), "third unique member should be PLAYER_THREE");
        require(assignments.get(0).memberIndex() == 0, "first assignment should have member index 0");
        require(assignments.get(1).memberIndex() == 1, "second assignment should have member index 1");
        require(assignments.get(2).memberIndex() == 2, "third assignment should have member index 2");
    }

    private static void assignmentsRotateAcrossInitialSpawnCount() {
        ZombiesSpawnAssignmentService service = new ZombiesSpawnAssignmentService();

        ZombiesSpawnAssignmentPlan plan = requireValue(service.assignFromSnapshot(
                snapshot(initialPlayerSpawn("initial-a"), initialPlayerSpawn("initial-b"), initialPlayerSpawn("initial-c")),
                List.of(PLAYER_ONE, PLAYER_TWO, PLAYER_THREE, PLAYER_FOUR)));

        require(plan.initialSpawnCount() == 3, "plan should count three INITIAL spawns");
        require(plan.assignments().get(0).spawnIndex() == 0, "first player should use spawn 0");
        require(plan.assignments().get(1).spawnIndex() == 1, "second player should use spawn 1");
        require(plan.assignments().get(2).spawnIndex() == 2, "third player should use spawn 2");
        require(plan.assignments().get(3).spawnIndex() == 0, "fourth player should rotate back to spawn 0");
        require(plan.assignments().stream().allMatch(assignment -> assignment.spawnPoint().isEmpty()),
                "snapshot assignment should not contain concrete SpawnPointData");
    }

    private static void assignFromSnapshotCountsOnlyInitialPlayerSpawns() {
        ZombiesSpawnAssignmentService service = new ZombiesSpawnAssignmentService();

        ZombiesSpawnAssignmentPlan plan = requireValue(service.assignFromSnapshot(
                snapshot(
                        initialPlayerSpawn("initial-a"),
                        dynamicPlayerSpawn("dynamic-a"),
                        zombieSpawn("zombie-a"),
                        initialPlayerSpawn("initial-b"),
                        spawn("zombie-initial-kind", "INITIAL", true)),
                List.of(PLAYER_ONE, PLAYER_TWO, PLAYER_THREE)));

        require(plan.initialSpawnCount() == 2,
                "assignFromSnapshot should count only SpawnSnapshot.initialPlayerSpawn entries");
        require(plan.assignments().get(0).spawnIndex() == 0, "first assignment should use first counted INITIAL spawn");
        require(plan.assignments().get(1).spawnIndex() == 1, "second assignment should use second counted INITIAL spawn");
        require(plan.assignments().get(2).spawnIndex() == 0, "third assignment should rotate by counted INITIAL spawns");
    }

    private static void emptyTeleportPlanSucceeds() {
        ZombiesSpawnAssignmentService service = new ZombiesSpawnAssignmentService();

        ZombiesServiceResult<ZombiesSpawnTeleportSummary> result = service.executeTeleport(
                (net.minecraft.server.level.ServerLevel) null,
                List.of());

        require(result.success(), "empty teleport assignment collection should succeed even without a server level");
        ZombiesSpawnTeleportSummary summary = requireValue(result);
        require(summary.attemptCount() == 0, "empty teleport summary should have zero attempts");
        require(summary.allSucceeded(), "empty teleport summary should be all succeeded");
    }

    private static ZombiesMapSnapshot snapshot(ZombiesMapSnapshot.SpawnSnapshot... spawns) {
        return ZombiesMapSnapshot.of(
                RoomId.of("zombies", "spawn_assignment_compat"),
                "spawn_assignment_compat",
                true,
                List.of(spawns),
                List.of());
    }

    private static ZombiesMapSnapshot.SpawnSnapshot initialPlayerSpawn(String objectId) {
        return spawn(objectId, "INITIAL", false);
    }

    private static ZombiesMapSnapshot.SpawnSnapshot dynamicPlayerSpawn(String objectId) {
        return spawn(objectId, "DYNAMIC", false);
    }

    private static ZombiesMapSnapshot.SpawnSnapshot zombieSpawn(String objectId) {
        return spawn(objectId, "", true);
    }

    private static ZombiesMapSnapshot.SpawnSnapshot spawn(String objectId, String kind, boolean zombieSpawn) {
        return new ZombiesMapSnapshot.SpawnSnapshot(objectId, "spawn", kind, 0, 1.0D, zombieSpawn);
    }

    private static <T> T requireValue(ZombiesServiceResult<T> result) {
        return result.value().orElseThrow(() -> new AssertionError("expected result value"));
    }

    private static UUID uuid(int value) {
        return new UUID(0L, value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
