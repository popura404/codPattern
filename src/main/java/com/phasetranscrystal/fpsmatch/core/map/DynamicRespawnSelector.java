package com.phasetranscrystal.fpsmatch.core.map;

import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointKind;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

final class DynamicRespawnSelector {
    private static final double SCORE_EPSILON = 1.0E-6D;

    private static final Weights DEFAULT_WEIGHTS = new Weights(
            0.85D,
            0.52D,
            0.28D,
            0.08D,
            0.05D,
            0.02D,
            12.0D,
            20.0D,
            8.0D,
            14.0D
    );

    private DynamicRespawnSelector() {
    }

    static Optional<SpawnPointData> selectBestSpawnPoint(ServerPlayer player, BaseTeam team, MapTeams mapTeams) {
        if (player == null || team == null || mapTeams == null) {
            return Optional.empty();
        }

        List<CandidateScore> scores = new ArrayList<>();
        for (SpawnPointData candidate : new LinkedHashSet<>(team.getSpawnPointsData(SpawnPointKind.DYNAMIC_CANDIDATE))) {
            evaluateCandidate(player, team, mapTeams, candidate).ifPresent(scores::add);
        }
        if (scores.isEmpty()) {
            return Optional.empty();
        }

        double bestScore = scores.stream()
                .mapToDouble(CandidateScore::totalScore)
                .max()
                .orElse(Double.NEGATIVE_INFINITY);

        List<SpawnPointData> bestCandidates = scores.stream()
                .filter(score -> Math.abs(score.totalScore() - bestScore) <= SCORE_EPSILON)
                .map(CandidateScore::point)
                .toList();
        if (bestCandidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(bestCandidates.get(ThreadLocalRandom.current().nextInt(bestCandidates.size())));
    }

    private static Optional<CandidateScore> evaluateCandidate(
            ServerPlayer player,
            BaseTeam team,
            MapTeams mapTeams,
            SpawnPointData candidate
    ) {
        if (candidate == null || !Level.isInSpawnableBounds(candidate.getPosition())) {
            return Optional.empty();
        }

        ServerLevel candidateLevel = mapTeams.level.getServer().getLevel(candidate.getDimension());
        if (candidateLevel == null || !SpawnSafetyValidator.isSafe(candidateLevel, player, candidate)) {
            return Optional.empty();
        }

        List<ServerPlayer> enemies = collectEnemies(team, mapTeams, player.getUUID(), candidate.getDimension());
        List<ServerPlayer> teammates = collectTeammates(team, player.getUUID(), candidate.getDimension());

        Vec3 spawnPos = new Vec3(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
        double eyeHeightOffset = player.getEyeY() - player.getY();
        Vec3 spawnEyePos = spawnPos.add(0.0D, eyeHeightOffset, 0.0D);
        Vec3 spawnChestPos = spawnPos.add(0.0D, eyeHeightOffset * 0.6D, 0.0D);

        DistanceStats enemyDistances = distanceStats(enemies, spawnPos);
        DistanceStats teammateDistances = distanceStats(teammates, spawnPos);

        boolean enemyVisible = enemies.stream()
                .anyMatch(enemy -> hasLineOfSight(candidateLevel, enemy, spawnEyePos, spawnChestPos));
        double teammateVisibleRatio = visibilityRatio(candidateLevel, teammates, spawnEyePos, spawnChestPos);

        double nearestEnemyScore = normalizeDistance(enemyDistances.nearestDistance(), DEFAULT_WEIGHTS.nearestEnemyDistanceScale());
        double averageEnemyScore = normalizeDistance(enemyDistances.averageDistance(), DEFAULT_WEIGHTS.averageEnemyDistanceScale());
        double nearestTeammateScore = teammates.isEmpty()
                ? 0.5D
                : normalizeDistance(teammateDistances.nearestDistance(), DEFAULT_WEIGHTS.nearestTeammateDistanceScale());
        double averageTeammateScore = teammates.isEmpty()
                ? 0.5D
                : normalizeDistance(teammateDistances.averageDistance(), DEFAULT_WEIGHTS.averageTeammateDistanceScale());
        double teammateVisibilityScore = teammates.isEmpty() ? 0.5D : 1.0D - teammateVisibleRatio;

        double totalScore = (DEFAULT_WEIGHTS.nearestEnemyWeight() * nearestEnemyScore)
                + (DEFAULT_WEIGHTS.averageEnemyWeight() * averageEnemyScore)
                + (DEFAULT_WEIGHTS.nearestTeammateWeight() * nearestTeammateScore)
                + (DEFAULT_WEIGHTS.averageTeammateWeight() * averageTeammateScore)
                + (DEFAULT_WEIGHTS.teammateVisibilityWeight() * teammateVisibilityScore)
                - (enemyVisible ? DEFAULT_WEIGHTS.enemyVisibilityPenalty() : 0.0D);

        return Optional.of(new CandidateScore(candidate, totalScore));
    }

    private static List<ServerPlayer> collectEnemies(
            BaseTeam team,
            MapTeams mapTeams,
            java.util.UUID playerId,
            net.minecraft.resources.ResourceKey<Level> dimension
    ) {
        List<ServerPlayer> enemies = new ArrayList<>();
        for (BaseTeam candidateTeam : mapTeams.getTeams()) {
            if (candidateTeam == team) {
                continue;
            }
            collectPlayers(candidateTeam, playerId, dimension, enemies);
        }
        return enemies;
    }

    private static List<ServerPlayer> collectTeammates(
            BaseTeam team,
            java.util.UUID playerId,
            net.minecraft.resources.ResourceKey<Level> dimension
    ) {
        List<ServerPlayer> teammates = new ArrayList<>();
        collectPlayers(team, playerId, dimension, teammates);
        return teammates;
    }

    private static void collectPlayers(
            BaseTeam sourceTeam,
            java.util.UUID excludedPlayerId,
            net.minecraft.resources.ResourceKey<Level> dimension,
            List<ServerPlayer> collectedPlayers
    ) {
        for (PlayerData playerData : sourceTeam.getPlayersData()) {
            if (excludedPlayerId.equals(playerData.getOwner())) {
                continue;
            }
            playerData.getPlayer()
                    .filter(player -> isRelevantPlayer(player, dimension))
                    .ifPresent(collectedPlayers::add);
        }
    }

    private static boolean isRelevantPlayer(ServerPlayer player, net.minecraft.resources.ResourceKey<Level> dimension) {
        if (player == null || player.isSpectator() || player.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
            return false;
        }
        return player.isAlive() && dimension.equals(player.level().dimension());
    }

    private static DistanceStats distanceStats(List<ServerPlayer> players, Vec3 spawnPos) {
        if (players.isEmpty()) {
            return DistanceStats.noPlayers();
        }

        double nearestDistance = Double.MAX_VALUE;
        double totalDistance = 0.0D;
        for (ServerPlayer otherPlayer : players) {
            double distance = otherPlayer.position().distanceTo(spawnPos);
            nearestDistance = Math.min(nearestDistance, distance);
            totalDistance += distance;
        }
        return new DistanceStats(nearestDistance, totalDistance / players.size());
    }

    private static double visibilityRatio(ServerLevel level, List<ServerPlayer> players, Vec3 spawnEyePos, Vec3 spawnChestPos) {
        if (players.isEmpty()) {
            return 0.0D;
        }

        int visibleCount = 0;
        for (ServerPlayer otherPlayer : players) {
            if (hasLineOfSight(level, otherPlayer, spawnEyePos, spawnChestPos)) {
                visibleCount++;
            }
        }
        return visibleCount / (double) players.size();
    }

    private static boolean hasLineOfSight(ServerLevel level, ServerPlayer observer, Vec3 spawnEyePos, Vec3 spawnChestPos) {
        Vec3 observerEyePos = observer.getEyePosition();
        return isClearPath(level, observer, observerEyePos, spawnEyePos)
                || isClearPath(level, observer, observerEyePos, spawnChestPos);
    }

    private static boolean isClearPath(ServerLevel level, ServerPlayer observer, Vec3 start, Vec3 end) {
        return level.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                observer
        )).getType() == HitResult.Type.MISS;
    }

    private static double normalizeDistance(double distance, double scale) {
        if (distance == Double.POSITIVE_INFINITY) {
            return 1.0D;
        }
        if (distance <= 0.0D) {
            return 0.0D;
        }
        double normalizedScale = Math.max(0.001D, scale);
        return distance / (distance + normalizedScale);
    }

    private record CandidateScore(SpawnPointData point, double totalScore) {
    }

    private record DistanceStats(double nearestDistance, double averageDistance) {
        private static DistanceStats noPlayers() {
            return new DistanceStats(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        }
    }

    private record Weights(
            double enemyVisibilityPenalty,
            double nearestEnemyWeight,
            double averageEnemyWeight,
            double nearestTeammateWeight,
            double averageTeammateWeight,
            double teammateVisibilityWeight,
            double nearestEnemyDistanceScale,
            double averageEnemyDistanceScale,
            double nearestTeammateDistanceScale,
            double averageTeammateDistanceScale
    ) {
    }
}
