package com.phasetranscrystal.fpsmatch.core.map;

import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointKind;
import com.phasetranscrystal.fpsmatch.core.data.TeamSpawnProfile;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

public class BaseTeam {
    public final String name;
    public final String gameType;
    public final String mapName;

    private final int playerLimit;
    private final PlayerTeam playerTeam;
    private final Map<UUID, PlayerData> players = new LinkedHashMap<>();
    private final EnumMap<SpawnPointKind, List<SpawnPointData>> spawnPointsData = new EnumMap<>(SpawnPointKind.class);
    private int scores;

    public BaseTeam(String gameType, String mapName, String name, int playerLimit, PlayerTeam playerTeam) {
        this.gameType = gameType;
        this.mapName = mapName;
        this.name = name;
        this.playerLimit = playerLimit;
        this.playerTeam = playerTeam;
        for (SpawnPointKind kind : SpawnPointKind.values()) {
            spawnPointsData.put(kind, new ArrayList<>());
        }
    }

    public void join(ServerPlayer player) {
        if (hasPlayer(player.getUUID())) {
            return;
        }
        player.getScoreboard().addPlayerToTeam(player.getScoreboardName(), playerTeam);
        PlayerData data = new PlayerData(player);
        data.setLiving(true);
        players.put(player.getUUID(), data);
        assignNextSpawnPoint(player.getUUID(), SpawnPointKind.INITIAL);
    }

    public void leave(ServerPlayer player) {
        if (!hasPlayer(player.getUUID())) {
            return;
        }
        delPlayer(player.getUUID());
        player.getScoreboard().removePlayerFromTeam(player.getScoreboardName());
    }

    public void delPlayer(UUID uuid) {
        players.remove(uuid);
    }

    public Optional<PlayerData> getPlayerData(UUID uuid) {
        return Optional.ofNullable(players.get(uuid));
    }

    public List<PlayerData> getPlayersData() {
        return new ArrayList<>(players.values());
    }

    public List<UUID> getPlayerList() {
        return new ArrayList<>(players.keySet());
    }

    public boolean hasPlayer(UUID uuid) {
        return players.containsKey(uuid);
    }

    public boolean randomSpawnPoints() {
        return assignNextSpawnPoints(SpawnPointKind.INITIAL);
    }

    public void addSpawnPointData(SpawnPointData data) {
        if (data == null) {
            return;
        }
        spawnPoints(data.getKind()).add(data);
    }

    public boolean addSpawnPointDataIfAbsent(SpawnPointData data) {
        if (data == null || spawnPoints(data.getKind()).contains(data)) {
            return false;
        }
        spawnPoints(data.getKind()).add(data);
        return true;
    }

    public void addAllSpawnPointData(List<SpawnPointData> data) {
        if (data == null) {
            return;
        }
        data.forEach(this::addSpawnPointData);
    }

    public void setSpawnProfile(TeamSpawnProfile profile) {
        resetSpawnPointData();
        if (profile == null) {
            return;
        }
        addAllSpawnPointData(profile.initialSpawnPoints());
        addAllSpawnPointData(profile.dynamicSpawnCandidates());
    }

    public boolean removeSpawnPointData(SpawnPointData data) {
        if (data == null) {
            return false;
        }
        return spawnPoints(data.getKind()).remove(data);
    }

    public Optional<SpawnPointData> removeSpawnPointData(int index) {
        return removeSpawnPointData(SpawnPointKind.INITIAL, index);
    }

    public Optional<SpawnPointData> removeSpawnPointData(SpawnPointKind kind, int index) {
        List<SpawnPointData> points = spawnPoints(kind);
        if (index < 0 || index >= points.size()) {
            return Optional.empty();
        }
        return Optional.of(points.remove(index));
    }

    public void resetSpawnPointData() {
        for (List<SpawnPointData> points : spawnPointsData.values()) {
            points.clear();
        }
    }

    public void resetSpawnPointData(SpawnPointKind kind) {
        spawnPoints(kind).clear();
    }

    public void setAllSpawnPointData(List<SpawnPointData> spawnPointsData) {
        resetSpawnPointData(SpawnPointKind.INITIAL);
        if (spawnPointsData != null) {
            spawnPointsData.forEach(point -> addSpawnPointData(point == null ? null : point.withKind(SpawnPointKind.INITIAL)));
        }
    }

    public List<SpawnPointData> getSpawnPointsData() {
        return getSpawnPointsData(SpawnPointKind.INITIAL);
    }

    public List<SpawnPointData> getSpawnPointsData(SpawnPointKind kind) {
        return new ArrayList<>(spawnPoints(kind));
    }

    public TeamSpawnProfile getSpawnProfile() {
        return new TeamSpawnProfile(
                getSpawnPointsData(SpawnPointKind.INITIAL),
                getSpawnPointsData(SpawnPointKind.DYNAMIC_CANDIDATE)
        );
    }

    public void clearPlayerSpawnPointAssignments() {
        players.values().forEach(playerData -> playerData.setSpawnPointsData(null));
    }

    public Optional<SpawnPointData> assignNextSpawnPoint(UUID playerId) {
        return assignNextSpawnPoint(playerId, SpawnPointKind.INITIAL);
    }

    public Optional<SpawnPointData> assignNextSpawnPoint(UUID playerId, SpawnPointKind kind) {
        Optional<SpawnPointData> nextPoint = selectSpawnPoint(playerId, kind);
        nextPoint.ifPresent(point -> {
            PlayerData playerData = players.get(playerId);
            if (playerData != null) {
                playerData.setSpawnPointsData(point);
            }
        });
        return nextPoint;
    }

    public Optional<SpawnPointData> selectSpawnPoint(UUID playerId, SpawnPointKind kind) {
        PlayerData playerData = players.get(playerId);
        if (playerData == null) {
            return Optional.empty();
        }

        List<SpawnPointData> uniquePoints = getUniqueSpawnPoints(kind);
        if (uniquePoints.isEmpty()) {
            return Optional.empty();
        }

        SpawnPointData previousPoint = playerData.getLastSpawnPoint();
        if (previousPoint == null) {
            previousPoint = playerData.getSpawnPointsData();
        }
        final SpawnPointData previousPointFinal = previousPoint;
        Set<SpawnPointData> reservedByTeammates = new HashSet<>();
        for (PlayerData teammate : players.values()) {
            if (playerId.equals(teammate.getOwner())) {
                continue;
            }
            SpawnPointData point = teammate.getSpawnPointsData();
            if (point == null && kind == SpawnPointKind.DYNAMIC_CANDIDATE) {
                point = teammate.getLastSpawnPoint();
            }
            if (point != null) {
                reservedByTeammates.add(point);
            }
        }

        List<SpawnPointData> exclusivePoints = uniquePoints.stream()
                .filter(point -> !reservedByTeammates.contains(point))
                .toList();

        SpawnPointData nextPoint = pickSpawnPoint(exclusivePoints, previousPointFinal)
                .or(() -> pickSpawnPoint(uniquePoints, previousPointFinal))
                .orElse(null);
        if (nextPoint == null) {
            return Optional.empty();
        }

        return Optional.of(nextPoint);
    }

    public boolean assignNextSpawnPoints() {
        return assignNextSpawnPoints(SpawnPointKind.INITIAL);
    }

    public boolean assignNextSpawnPoints(SpawnPointKind kind) {
        if (spawnPoints(kind).isEmpty()) {
            return false;
        }

        boolean success = true;
        for (UUID playerId : new ArrayList<>(players.keySet())) {
            success &= assignNextSpawnPoint(playerId, kind).isPresent();
        }
        return success;
    }

    public void markSpawnUsed(UUID playerId, SpawnPointData point) {
        PlayerData playerData = players.get(playerId);
        if (playerData == null) {
            return;
        }
        playerData.setLastSpawnPoint(point);
        if (Objects.equals(playerData.getSpawnPointsData(), point)) {
            playerData.setSpawnPointsData(null);
        }
    }

    private List<SpawnPointData> getUniqueSpawnPoints(SpawnPointKind kind) {
        return new ArrayList<>(new LinkedHashSet<>(spawnPoints(kind)));
    }

    private Optional<SpawnPointData> pickSpawnPoint(List<SpawnPointData> points, SpawnPointData previousPoint) {
        if (points.isEmpty()) {
            return Optional.empty();
        }

        List<SpawnPointData> candidates = points;
        if (previousPoint != null && points.size() > 1) {
            List<SpawnPointData> withoutPrevious = points.stream()
                    .filter(point -> !Objects.equals(point, previousPoint))
                    .toList();
            if (!withoutPrevious.isEmpty()) {
                candidates = withoutPrevious;
            }
        }

        return Optional.of(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
    }

    public int getPlayerLimit() {
        return playerLimit;
    }

    public int getPlayerCount() {
        return players.size();
    }

    public PlayerTeam getPlayerTeam() {
        return playerTeam;
    }

    public int getScores() {
        return scores;
    }

    public void setScores(int scores) {
        this.scores = scores;
    }

    private List<SpawnPointData> spawnPoints(SpawnPointKind kind) {
        return spawnPointsData.computeIfAbsent(
                kind == null ? SpawnPointKind.INITIAL : kind,
                ignored -> new ArrayList<>()
        );
    }
}
