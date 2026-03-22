package com.phasetranscrystal.fpsmatch.core.map;

import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        if (spawnPointsData.isEmpty()) {
            return false;
        }
        if (spawnPointsData.size() < players.size()) {
            return false;
        }
        List<SpawnPointData> shuffled = new ArrayList<>(spawnPointsData);
        Collections.shuffle(shuffled);
        int index = 0;
        for (PlayerData data : players.values()) {
            data.setSpawnPointsData(shuffled.get(index++));
        }
        return true;
    }

    public void addSpawnPointData(SpawnPointData data) {
        spawnPointsData.add(data);
    }

    public void addAllSpawnPointData(List<SpawnPointData> data) {
        spawnPointsData.addAll(data);
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
