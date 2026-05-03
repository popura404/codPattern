package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.ModePlayerValue;
import com.cdp.codpattern.app.zombies.map.ZombiesMapSnapshot;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * MVP1 fixed INITIAL spawn assignment and teleport service.
 */
public final class ZombiesSpawnAssignmentService {
    public ZombiesServiceResult<ZombiesSpawnAssignmentPlan> assign(
            ZombiesMapSnapshot snapshot,
            List<UUID> memberIds
    ) {
        return assignFromSnapshot(snapshot, memberIds);
    }

    public ZombiesServiceResult<ZombiesSpawnAssignmentPlan> assignFromSnapshot(
            ZombiesMapSnapshot snapshot,
            List<UUID> memberIds
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        int initialSpawnCount = initialSpawnCount(snapshot);
        return buildAssignments(initialSpawnCount, List.of(), memberIds);
    }

    public ZombiesServiceResult<ZombiesSpawnAssignmentPlan> assign(
            List<SpawnPointData> initialSpawns,
            List<UUID> memberIds
    ) {
        return assignFromInitialSpawns(initialSpawns, memberIds);
    }

    public ZombiesServiceResult<ZombiesSpawnAssignmentPlan> assignFromInitialSpawns(
            List<SpawnPointData> initialSpawns,
            List<UUID> memberIds
    ) {
        List<SpawnPointData> spawns = normalizeSpawnPoints(initialSpawns);
        return buildAssignments(spawns.size(), spawns, memberIds);
    }

    public ZombiesServiceResult<ZombiesSpawnTeleportSummary> executeTeleport(
            ServerLevel serverLevel,
            ZombiesSpawnAssignmentPlan plan
    ) {
        return executeTeleport(serverLevel, plan == null ? List.of() : plan.assignments());
    }

    public ZombiesServiceResult<ZombiesSpawnTeleportSummary> executeTeleport(
            BaseMap map,
            ZombiesSpawnAssignmentPlan plan
    ) {
        return executeTeleport(map, plan == null ? List.of() : plan.assignments());
    }

    public ZombiesServiceResult<ZombiesSpawnTeleportSummary> executeTeleport(
            BaseMap map,
            Collection<ZombiesSpawnAssignment> assignments
    ) {
        List<ZombiesSpawnAssignment> copiedAssignments = normalizeAssignments(assignments);
        List<ZombiesSpawnTeleportAttempt> attempts = new ArrayList<>(copiedAssignments.size());
        if (map == null || map.getServerLevel() == null) {
            for (ZombiesSpawnAssignment assignment : copiedAssignments) {
                attempts.add(ZombiesSpawnTeleportAttempt.failure(assignment, "missing_map"));
            }
            return teleportResult(new ZombiesSpawnTeleportSummary(attempts));
        }

        for (ZombiesSpawnAssignment assignment : copiedAssignments) {
            try {
                attempts.add(teleportOne(map, assignment));
            } catch (RuntimeException exception) {
                attempts.add(ZombiesSpawnTeleportAttempt.failure(
                        assignment,
                        "exception:" + exception.getClass().getSimpleName()));
            }
        }
        return teleportResult(new ZombiesSpawnTeleportSummary(attempts));
    }

    public ZombiesServiceResult<ZombiesSpawnTeleportSummary> executeTeleport(
            ServerLevel serverLevel,
            Collection<ZombiesSpawnAssignment> assignments
    ) {
        List<ZombiesSpawnAssignment> copiedAssignments = normalizeAssignments(assignments);
        List<ZombiesSpawnTeleportAttempt> attempts = new ArrayList<>(copiedAssignments.size());
        if (serverLevel == null) {
            for (ZombiesSpawnAssignment assignment : copiedAssignments) {
                attempts.add(ZombiesSpawnTeleportAttempt.failure(assignment, "missing_server_level"));
            }
            return teleportResult(new ZombiesSpawnTeleportSummary(attempts));
        }

        for (ZombiesSpawnAssignment assignment : copiedAssignments) {
            try {
                attempts.add(teleportOne(serverLevel, assignment));
            } catch (RuntimeException exception) {
                attempts.add(ZombiesSpawnTeleportAttempt.failure(
                        assignment,
                        "exception:" + exception.getClass().getSimpleName()));
            }
        }
        return teleportResult(new ZombiesSpawnTeleportSummary(attempts));
    }

    private static ZombiesServiceResult<ZombiesSpawnAssignmentPlan> buildAssignments(
            int initialSpawnCount,
            List<SpawnPointData> initialSpawns,
            List<UUID> memberIds
    ) {
        List<UUID> members = normalizeMemberIds(memberIds);
        if (!members.isEmpty() && initialSpawnCount <= 0) {
            return ZombiesServiceResult.failure(
                    ZombiesErrorCode.STARTUP_TELEPORT_FAILED,
                    countParams(members.size(), initialSpawnCount),
                    "Zombies startup teleport assignment failed: missing INITIAL spawns");
        }

        List<ZombiesSpawnAssignment> assignments = new ArrayList<>(members.size());
        for (int memberIndex = 0; memberIndex < members.size(); memberIndex++) {
            int spawnIndex = initialSpawnCount == 0 ? -1 : memberIndex % initialSpawnCount;
            Optional<SpawnPointData> spawnPoint = spawnIndex >= 0 && spawnIndex < initialSpawns.size()
                    ? Optional.of(initialSpawns.get(spawnIndex))
                    : Optional.empty();
            assignments.add(new ZombiesSpawnAssignment(
                    members.get(memberIndex),
                    memberIndex,
                    spawnIndex,
                    spawnPoint));
        }
        return ZombiesServiceResult.success(new ZombiesSpawnAssignmentPlan(initialSpawnCount, assignments));
    }

    private static ZombiesSpawnTeleportAttempt teleportOne(
            ServerLevel serverLevel,
            ZombiesSpawnAssignment assignment
    ) {
        SpawnPointData spawnPoint = assignment.spawnPoint().orElse(null);
        if (spawnPoint == null) {
            return ZombiesSpawnTeleportAttempt.failure(assignment, "missing_spawn_point");
        }
        if (spawnPoint.getDimension() == null || spawnPoint.getPosition() == null) {
            return ZombiesSpawnTeleportAttempt.failure(assignment, "invalid_spawn_point");
        }
        if (!Level.isInSpawnableBounds(spawnPoint.getPosition())) {
            return ZombiesSpawnTeleportAttempt.failure(assignment, "spawn_out_of_bounds");
        }

        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(assignment.playerId());
        if (player == null) {
            return ZombiesSpawnTeleportAttempt.failure(assignment, "player_offline");
        }

        ServerLevel targetLevel = serverLevel.getServer().getLevel(spawnPoint.getDimension());
        if (targetLevel == null) {
            return ZombiesSpawnTeleportAttempt.failure(
                    assignment,
                    "missing_dimension:" + spawnPoint.getDimension().location());
        }
        player.teleportTo(
                targetLevel,
                spawnPoint.getX() + 0.5D,
                spawnPoint.getY(),
                spawnPoint.getZ() + 0.5D,
                spawnPoint.getYaw(),
                0.0F);
        player.setDeltaMovement(player.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
        player.setOnGround(true);
        player.setRespawnPosition(spawnPoint.getDimension(), spawnPoint.getPosition(), spawnPoint.getYaw(), true, false);
        return ZombiesSpawnTeleportAttempt.success(assignment);
    }

    private static ZombiesSpawnTeleportAttempt teleportOne(
            BaseMap map,
            ZombiesSpawnAssignment assignment
    ) {
        SpawnPointData spawnPoint = assignment.spawnPoint().orElse(null);
        if (spawnPoint == null) {
            return ZombiesSpawnTeleportAttempt.failure(assignment, "missing_spawn_point");
        }
        ServerLevel serverLevel = map.getServerLevel();
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(assignment.playerId());
        if (player == null) {
            return ZombiesSpawnTeleportAttempt.failure(assignment, "player_offline");
        }
        if (!map.teleportToPoint(player, spawnPoint)) {
            return ZombiesSpawnTeleportAttempt.failure(assignment, "teleport_rejected");
        }
        player.setRespawnPosition(spawnPoint.getDimension(), spawnPoint.getPosition(), spawnPoint.getYaw(), true, false);
        return ZombiesSpawnTeleportAttempt.success(assignment);
    }

    private static ZombiesServiceResult<ZombiesSpawnTeleportSummary> teleportResult(
            ZombiesSpawnTeleportSummary summary
    ) {
        if (summary.failureCount() == 0) {
            return ZombiesServiceResult.success(summary);
        }
        return new ZombiesServiceResult<>(
                false,
                ZombiesErrorCode.STARTUP_TELEPORT_FAILED,
                teleportParams(summary),
                Optional.of(summary),
                "Zombies startup teleport failed for " + summary.failureCount() + " player(s)");
    }

    private static int initialSpawnCount(ZombiesMapSnapshot snapshot) {
        int count = 0;
        for (ZombiesMapSnapshot.SpawnSnapshot spawn : snapshot.spawns()) {
            if (spawn != null && spawn.initialPlayerSpawn()) {
                count++;
            }
        }
        return count;
    }

    private static List<UUID> normalizeMemberIds(List<UUID> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<UUID> normalized = new LinkedHashSet<>();
        for (UUID memberId : memberIds) {
            if (memberId != null) {
                normalized.add(memberId);
            }
        }
        return List.copyOf(normalized);
    }

    private static List<SpawnPointData> normalizeSpawnPoints(List<SpawnPointData> initialSpawns) {
        if (initialSpawns == null || initialSpawns.isEmpty()) {
            return List.of();
        }
        List<SpawnPointData> normalized = new ArrayList<>(initialSpawns.size());
        for (SpawnPointData initialSpawn : initialSpawns) {
            if (initialSpawn != null) {
                normalized.add(initialSpawn);
            }
        }
        return List.copyOf(normalized);
    }

    private static List<ZombiesSpawnAssignment> normalizeAssignments(
            Collection<ZombiesSpawnAssignment> assignments
    ) {
        if (assignments == null || assignments.isEmpty()) {
            return List.of();
        }
        List<ZombiesSpawnAssignment> normalized = new ArrayList<>(assignments.size());
        for (ZombiesSpawnAssignment assignment : assignments) {
            if (assignment != null) {
                normalized.add(assignment);
            }
        }
        return List.copyOf(normalized);
    }

    private static Map<String, ModePlayerValue> countParams(int playerCount, int initialSpawnCount) {
        Map<String, ModePlayerValue> params = new LinkedHashMap<>();
        params.put("playerCount", ModePlayerValue.ofInt(playerCount));
        params.put("initialSpawnCount", ModePlayerValue.ofInt(initialSpawnCount));
        return params;
    }

    private static Map<String, ModePlayerValue> teleportParams(ZombiesSpawnTeleportSummary summary) {
        Map<String, ModePlayerValue> params = new LinkedHashMap<>();
        params.put("attemptCount", ModePlayerValue.ofInt(summary.attemptCount()));
        params.put("successCount", ModePlayerValue.ofInt(summary.successCount()));
        params.put("failureCount", ModePlayerValue.ofInt(summary.failureCount()));
        return params;
    }

    public record ZombiesSpawnAssignmentPlan(
            int initialSpawnCount,
            List<ZombiesSpawnAssignment> assignments
    ) {
        public ZombiesSpawnAssignmentPlan {
            if (initialSpawnCount < 0) {
                throw new IllegalArgumentException("initialSpawnCount must not be negative");
            }
            assignments = assignments == null ? List.of() : List.copyOf(assignments);
        }

        public int playerCount() {
            return assignments.size();
        }

        public boolean containsSpawnPoints() {
            return assignments.stream().allMatch(assignment -> assignment.spawnPoint().isPresent());
        }
    }

    public record ZombiesSpawnAssignment(
            UUID playerId,
            int memberIndex,
            int spawnIndex,
            Optional<SpawnPointData> spawnPoint
    ) {
        public ZombiesSpawnAssignment {
            Objects.requireNonNull(playerId, "playerId");
            if (memberIndex < 0) {
                throw new IllegalArgumentException("memberIndex must not be negative");
            }
            spawnPoint = spawnPoint == null ? Optional.empty() : spawnPoint;
            if (spawnIndex < 0 && spawnPoint.isPresent()) {
                throw new IllegalArgumentException("spawnIndex must not be negative when spawnPoint is present");
            }
        }

        public static ZombiesSpawnAssignment withoutSpawnPoint(UUID playerId, int memberIndex, int spawnIndex) {
            return new ZombiesSpawnAssignment(playerId, memberIndex, spawnIndex, Optional.empty());
        }

        public static ZombiesSpawnAssignment withSpawnPoint(
                UUID playerId,
                int memberIndex,
                int spawnIndex,
                SpawnPointData spawnPoint
        ) {
            return new ZombiesSpawnAssignment(playerId, memberIndex, spawnIndex, Optional.ofNullable(spawnPoint));
        }
    }

    public record ZombiesSpawnTeleportSummary(List<ZombiesSpawnTeleportAttempt> attempts) {
        public ZombiesSpawnTeleportSummary {
            attempts = attempts == null ? List.of() : List.copyOf(attempts);
        }

        public int attemptCount() {
            return attempts.size();
        }

        public int successCount() {
            int count = 0;
            for (ZombiesSpawnTeleportAttempt attempt : attempts) {
                if (attempt.success()) {
                    count++;
                }
            }
            return count;
        }

        public int failureCount() {
            return attemptCount() - successCount();
        }

        public boolean allSucceeded() {
            return failureCount() == 0;
        }

        public List<UUID> failedPlayerIds() {
            return attempts.stream()
                    .filter(attempt -> !attempt.success())
                    .map(ZombiesSpawnTeleportAttempt::playerId)
                    .toList();
        }
    }

    public record ZombiesSpawnTeleportAttempt(
            UUID playerId,
            int memberIndex,
            int spawnIndex,
            boolean success,
            ZombiesErrorCode code,
            String reason
    ) {
        public ZombiesSpawnTeleportAttempt {
            Objects.requireNonNull(playerId, "playerId");
            if (memberIndex < 0) {
                throw new IllegalArgumentException("memberIndex must not be negative");
            }
            code = code == null ? (success ? ZombiesErrorCode.OK : ZombiesErrorCode.STARTUP_TELEPORT_FAILED) : code;
            reason = Objects.requireNonNullElse(reason, "");
        }

        public static ZombiesSpawnTeleportAttempt success(ZombiesSpawnAssignment assignment) {
            return new ZombiesSpawnTeleportAttempt(
                    assignment.playerId(),
                    assignment.memberIndex(),
                    assignment.spawnIndex(),
                    true,
                    ZombiesErrorCode.OK,
                    "");
        }

        public static ZombiesSpawnTeleportAttempt failure(ZombiesSpawnAssignment assignment, String reason) {
            return new ZombiesSpawnTeleportAttempt(
                    assignment.playerId(),
                    assignment.memberIndex(),
                    assignment.spawnIndex(),
                    false,
                    ZombiesErrorCode.STARTUP_TELEPORT_FAILED,
                    reason);
        }
    }
}
