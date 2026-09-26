package com.phasetranscrystal.fpsmatch.core.map;

import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class MapTeams {
    protected final ServerLevel level;
    protected final BaseMap map;
    private final String constructionGameType;

    private final Map<String, BaseTeam> teams = new LinkedHashMap<>();
    private final java.util.Set<String> createdScoreboardTeams = new java.util.LinkedHashSet<>();
    private final BaseTeam spectatorTeam;

    public MapTeams(ServerLevel level, BaseMap map) {
        this(level, map, null);
    }

    public MapTeams(ServerLevel level, BaseMap map, String gameType) {
        this.level = level;
        this.map = map;
        this.constructionGameType = gameType;
        this.spectatorTeam = addTeam("spectator", -1, false);
        BlockPos center = BlockPos.containing(map.mapArea.getAABB().getCenter());
        this.spectatorTeam.addSpawnPointData(new SpawnPointData(map.getServerLevel().dimension(), center, 0.0F, 0.0F));
    }

    public BaseTeam addTeam(String teamName, int limit, boolean addToSystem) {
        String gameType = constructionGameType != null ? constructionGameType : map.getGameType();
        String fixedName = gameType + "_" + map.getMapName() + "_" + teamName;
        PlayerTeam playerTeam = level.getScoreboard().getPlayerTeam(fixedName);
        if (playerTeam == null) {
            com.cdp.codpattern.app.match.management.MapMutationResources.beforeCreateScoreboardTeam(fixedName);
            playerTeam = level.getScoreboard().addPlayerTeam(fixedName);
            createdScoreboardTeams.add(fixedName);
        } else {
            com.cdp.codpattern.app.match.management.MapMutationResources.reusedScoreboardTeam(fixedName);
        }
        BaseTeam team = new BaseTeam(gameType, map.getMapName(), teamName, limit, playerTeam);
        if (addToSystem) {
            teams.put(teamName, team);
        }
        return team;
    }

    public BaseTeam addTeam(String teamName, int limit) {
        return addTeam(teamName, limit, true);
    }

    /** Removes only teams acquired by this instance, after all players have left. */
    public void retireCreatedScoreboardTeams() {
        if (!getJoinedPlayersWithSpec().isEmpty()) throw new IllegalStateException("Map still has players");
        for (String name : createdScoreboardTeamNames()) {
            PlayerTeam team = level.getScoreboard().getPlayerTeam(name);
            if (team == null) continue;
            if (!team.getPlayers().isEmpty()) throw new IllegalStateException("Scoreboard team still has players: " + name);
            level.getScoreboard().removePlayerTeam(team);
        }
        createdScoreboardTeams.clear();
    }

    public java.util.Set<String> createdScoreboardTeamNames() {
        java.util.Set<String> exclusive = new java.util.HashSet<>(createdScoreboardTeams);
        if (com.phasetranscrystal.fpsmatch.core.FPSMCore.initialized()) {
            com.phasetranscrystal.fpsmatch.core.FPSMCore.getInstance().getAllMaps().values().stream()
                    .flatMap(java.util.Collection::stream).filter(other -> other != map).forEach(other -> {
                        var roster = other.getMapTeams();
                        roster.getTeams().forEach(team -> exclusive.remove(team.getPlayerTeam().getName()));
                        exclusive.remove(roster.getSpectatorTeam().getPlayerTeam().getName());
                    });
        }
        return java.util.Set.copyOf(exclusive);
    }

    public Optional<BaseTeam> getTeamByPlayer(Player player) {
        return teams.values().stream()
                .filter(team -> team.hasPlayer(player.getUUID()))
                .findFirst();
    }

    public Optional<BaseTeam> getTeamByName(String teamName) {
        return Optional.ofNullable(teams.get(teamName));
    }

    public List<PlayerData> getJoinedPlayers() {
        List<PlayerData> data = new ArrayList<>();
        teams.values().forEach(team -> data.addAll(team.getPlayersData()));
        return data;
    }

    public List<UUID> getJoinedUUID() {
        List<UUID> data = new ArrayList<>();
        teams.values().forEach(team -> data.addAll(team.getPlayerList()));
        return data;
    }

    public List<UUID> getJoinedPlayersWithSpec() {
        List<UUID> data = getJoinedUUID();
        data.addAll(spectatorTeam.getPlayerList());
        return data;
    }

    public List<UUID> getSpecPlayers() {
        return spectatorTeam.getPlayerList();
    }

    public boolean checkTeam(String teamName) {
        return teams.containsKey(teamName);
    }

    public boolean testTeamIsFull(String teamName) {
        BaseTeam team = teams.get(teamName);
        return team != null && team.getPlayerLimit() != -1 && team.getPlayerCount() >= team.getPlayerLimit();
    }

    public List<BaseTeam> getTeams() {
        return new ArrayList<>(teams.values());
    }

    public BaseTeam getSpectatorTeam() {
        return spectatorTeam;
    }

    public void joinTeam(String teamName, ServerPlayer player) {
        BaseTeam team = teams.get(teamName);
        if (team == null || testTeamIsFull(teamName)) {
            return;
        }
        leaveTeam(player);
        team.join(player);
    }

    public void leaveTeam(ServerPlayer player) {
        if (spectatorTeam.hasPlayer(player.getUUID())) {
            spectatorTeam.leave(player);
            return;
        }
        teams.values().forEach(team -> team.leave(player));
    }

    public boolean removePlayer(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        if (removePlayerFromTeam(spectatorTeam, playerId)) {
            return true;
        }
        for (BaseTeam team : teams.values()) {
            if (removePlayerFromTeam(team, playerId)) {
                return true;
            }
        }
        return false;
    }

    public List<UUID> removeOfflinePlayers() {
        List<UUID> removedPlayers = new ArrayList<>();
        removeOfflinePlayersFromTeam(spectatorTeam, removedPlayers);
        teams.values().forEach(team -> removeOfflinePlayersFromTeam(team, removedPlayers));
        return removedPlayers;
    }

    private void removeOfflinePlayersFromTeam(BaseTeam team, List<UUID> removedPlayers) {
        for (PlayerData playerData : new ArrayList<>(team.getPlayersData())) {
            if (playerData == null || playerData.isOnline()) {
                continue;
            }
            if (removePlayerFromTeam(team, playerData.getOwner())) {
                removedPlayers.add(playerData.getOwner());
            }
        }
    }

    private boolean removePlayerFromTeam(BaseTeam team, UUID playerId) {
        if (team == null || playerId == null) {
            return false;
        }
        Optional<PlayerData> playerDataOptional = team.getPlayerData(playerId);
        if (playerDataOptional.isEmpty()) {
            return false;
        }
        PlayerData playerData = playerDataOptional.get();
        team.delPlayer(playerId);
        String scoreboardName = playerData.scoreboardName();
        if (!scoreboardName.isBlank()) {
            level.getScoreboard().removePlayerFromTeam(scoreboardName, team.getPlayerTeam());
        }
        return true;
    }
}
