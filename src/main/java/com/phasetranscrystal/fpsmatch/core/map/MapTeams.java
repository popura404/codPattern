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

    private final Map<String, BaseTeam> teams = new LinkedHashMap<>();
    private final BaseTeam spectatorTeam;

    public MapTeams(ServerLevel level, BaseMap map) {
        this.level = level;
        this.map = map;
        this.spectatorTeam = addTeam("spectator", -1, false);
        BlockPos center = BlockPos.containing(map.mapArea.getAABB().getCenter());
        this.spectatorTeam.addSpawnPointData(new SpawnPointData(map.getServerLevel().dimension(), center, 0.0F, 0.0F));
    }

    public BaseTeam addTeam(String teamName, int limit, boolean addToSystem) {
        String fixedName = map.getGameType() + "_" + map.getMapName() + "_" + teamName;
        PlayerTeam playerTeam = Objects.requireNonNullElseGet(
                level.getScoreboard().getPlayerTeam(fixedName),
                () -> level.getScoreboard().addPlayerTeam(fixedName)
        );
        BaseTeam team = new BaseTeam(map.getGameType(), map.getMapName(), teamName, limit, playerTeam);
        if (addToSystem) {
            teams.put(teamName, team);
        }
        return team;
    }

    public BaseTeam addTeam(String teamName, int limit) {
        return addTeam(teamName, limit, true);
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
}
