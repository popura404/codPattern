package com.phasetranscrystal.fpsmatch.core.map;

import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
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
    private final List<SpawnPointData> spawnPointsData = new ArrayList<>();
    private int scores;

    public BaseTeam(String gameType, String mapName, String name, int playerLimit, PlayerTeam playerTeam) {
        this.gameType = gameType;
        this.mapName = mapName;
        this.name = name;
        this.playerLimit = playerLimit;
        this.playerTeam = playerTeam;
    }

    public void join(ServerPlayer player) {
        if (hasPlayer(player.getUUID())) {
            return;
        }
        player.getScoreboard().addPlayerToTeam(player.getScoreboardName(), playerTeam);
        PlayerData data = new PlayerData(player);
        data.setLiving(true);
        players.put(player.getUUID(), data);
        assignNextSpawnPoint(player.getUUID());
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
        return assignNextSpawnPoints();
    }

    public void addSpawnPointData(SpawnPointData data) {
        spawnPointsData.add(data);
    }

    public boolean addSpawnPointDataIfAbsent(SpawnPointData data) {
        if (data == null || spawnPointsData.contains(data)) {
            return false;
        }
        spawnPointsData.add(data);
        return true;
    }

    public void addAllSpawnPointData(List<SpawnPointData> data) {
        spawnPointsData.addAll(data);
    }

    public boolean removeSpawnPointData(SpawnPointData data) {
        return spawnPointsData.remove(data);
    }

    public Optional<SpawnPointData> removeSpawnPointData(int index) {
        if (index < 0 || index >= spawnPointsData.size()) {
            return Optional.empty();
        }
        return Optional.of(spawnPointsData.remove(index));
    }

    public void resetSpawnPointData() {
        spawnPointsData.clear();
    }

    public void setAllSpawnPointData(List<SpawnPointData> spawnPointsData) {
        this.spawnPointsData.clear();
        this.spawnPointsData.addAll(spawnPointsData);
    }

    public List<SpawnPointData> getSpawnPointsData() {
        return new ArrayList<>(spawnPointsData);
    }

    public void clearPlayerSpawnPointAssignments() {
        players.values().forEach(playerData -> playerData.setSpawnPointsData(null));
    }

    public Optional<SpawnPointData> assignNextSpawnPoint(UUID playerId) {
        PlayerData playerData = players.get(playerId);
        if (playerData == null) {
            return Optional.empty();
        }

        List<SpawnPointData> uniquePoints = getUniqueSpawnPoints();
        if (uniquePoints.isEmpty()) {
            return Optional.empty();
        }

        SpawnPointData previousPoint = playerData.getSpawnPointsData();
        Set<SpawnPointData> reservedByTeammates = new HashSet<>();
        for (PlayerData teammate : players.values()) {
            if (playerId.equals(teammate.getOwner())) {
                continue;
            }
            SpawnPointData point = teammate.getSpawnPointsData();
            if (point != null) {
                reservedByTeammates.add(point);
            }
        }

        List<SpawnPointData> exclusivePoints = uniquePoints.stream()
                .filter(point -> !reservedByTeammates.contains(point))
                .toList();

        SpawnPointData nextPoint = pickSpawnPoint(exclusivePoints, previousPoint)
                .or(() -> pickSpawnPoint(uniquePoints, previousPoint))
                .orElse(null);
        if (nextPoint == null) {
            return Optional.empty();
        }

        playerData.setSpawnPointsData(nextPoint);
        return Optional.of(nextPoint);
    }

    public boolean assignNextSpawnPoints() {
        if (spawnPointsData.isEmpty()) {
            return false;
        }

        boolean success = true;
        for (UUID playerId : new ArrayList<>(players.keySet())) {
            success &= assignNextSpawnPoint(playerId).isPresent();
        }
        return success;
    }

    private List<SpawnPointData> getUniqueSpawnPoints() {
        return new ArrayList<>(new LinkedHashSet<>(spawnPointsData));
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
}
