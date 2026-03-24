package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.model.CodTdmTeamPersistenceSnapshot;
import com.cdp.codpattern.app.tdm.service.TeamBalanceService;
import com.cdp.codpattern.app.tdm.service.TeamPlayerSnapshotService;
import com.phasetranscrystal.fpsmatch.core.map.BaseTeam;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class CodTdmMapTeamViews {
    private CodTdmMapTeamViews() {
    }

    static Optional<String> findTeamNameByPlayer(CodTdmMap map, ServerPlayer player) {
        return map.getMapTeams().getTeamByPlayer(player).map(team -> team.name);
    }

    static List<ServerPlayer> joinedPlayers(CodTdmMap map) {
        List<ServerPlayer> players = new ArrayList<>();
        map.getMapTeams().getJoinedPlayers().forEach(pd -> pd.getPlayer().ifPresent(players::add));
        return players;
    }

    static List<ServerPlayer> spectatorPlayers(CodTdmMap map) {
        List<ServerPlayer> players = new ArrayList<>();
        for (UUID playerId : map.getMapTeams().getSpecPlayers()) {
            if (map.getServerLevel().getPlayerByUUID(playerId) instanceof ServerPlayer player) {
                players.add(player);
            }
        }
        return players;
    }

    static Map<String, Integer> teamPlayerCountsSnapshot(CodTdmMap map) {
        Map<String, Integer> teamPlayerCounts = new HashMap<>();
        for (BaseTeam team : map.getMapTeams().getTeams()) {
            teamPlayerCounts.put(team.name, team.getPlayerList().size());
        }
        return teamPlayerCounts;
    }

    static int maxPlayerCapacity(CodTdmMap map) {
        int maxPlayers = 0;
        for (BaseTeam team : map.getMapTeams().getTeams()) {
            maxPlayers += team.getPlayerLimit();
        }
        return maxPlayers;
    }

    static List<TeamBalanceService.TeamSnapshot> teamBalanceSnapshots(CodTdmMap map) {
        List<TeamBalanceService.TeamSnapshot> snapshots = new ArrayList<>();
        for (BaseTeam team : map.getMapTeams().getTeams()) {
            snapshots.add(new TeamBalanceService.TeamSnapshot(team.name, team.getPlayerList().size()));
        }
        return snapshots;
    }

    static List<TeamPlayerSnapshotService.TeamRoster> teamRosters(CodTdmMap map) {
        List<TeamPlayerSnapshotService.TeamRoster> rosters = new ArrayList<>();
        for (BaseTeam team : map.getMapTeams().getTeams()) {
            rosters.add(new TeamPlayerSnapshotService.TeamRoster(team.name, new ArrayList<>(team.getPlayerList())));
        }
        return rosters;
    }

    static List<CodTdmTeamPersistenceSnapshot> teamPersistenceSnapshots(CodTdmMap map) {
        List<CodTdmTeamPersistenceSnapshot> snapshots = new ArrayList<>();
        for (BaseTeam team : map.getMapTeams().getTeams()) {
            snapshots.add(new CodTdmTeamPersistenceSnapshot(
                    team.name,
                    team.getPlayerLimit(),
                    team.getSpawnProfile()
            ));
        }
        return snapshots;
    }

    static List<String> randomizeAllTeamSpawnsAndCollectMissingTeams(CodTdmMap map) {
        List<String> missingTeams = new ArrayList<>();
        for (BaseTeam team : map.getMapTeams().getTeams()) {
            if (!team.randomSpawnPoints()) {
                missingTeams.add(team.name);
            }
        }
        return missingTeams;
    }
}
