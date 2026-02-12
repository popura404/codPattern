package com.cdp.codpattern.app.tdm.service;

import com.phasetranscrystal.fpsmatch.core.map.BaseTeam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public final class TeamBalanceService {
    private TeamBalanceService() {
    }

    public static Optional<String> chooseAutoJoinTeam(
            List<BaseTeam> teams,
            Predicate<String> teamFullChecker,
            int maxTeamDiff
    ) {
        List<BaseTeam> sortedTeams = new ArrayList<>(teams);
        sortedTeams.sort(Comparator.comparingInt(team -> team.getPlayerList().size()));
        for (BaseTeam team : sortedTeams) {
            if (teamFullChecker.test(team.name)) {
                continue;
            }
            if (canJoinWithBalance(teams, team.name, maxTeamDiff)) {
                return Optional.of(team.name);
            }
        }
        return Optional.empty();
    }

    public static boolean canJoinWithBalance(List<BaseTeam> teams, String joiningTeam, int maxTeamDiff) {
        if (maxTeamDiff < 0) {
            return true;
        }
        int minPlayers = Integer.MAX_VALUE;
        int maxPlayers = Integer.MIN_VALUE;
        for (BaseTeam team : teams) {
            int size = team.getPlayerList().size();
            if (team.name.equals(joiningTeam)) {
                size += 1;
            }
            minPlayers = Math.min(minPlayers, size);
            maxPlayers = Math.max(maxPlayers, size);
        }
        if (minPlayers == Integer.MAX_VALUE || maxPlayers == Integer.MIN_VALUE) {
            return true;
        }
        return (maxPlayers - minPlayers) <= maxTeamDiff;
    }

    public static boolean canSwitchWithBalance(
            List<BaseTeam> teams,
            String currentTeam,
            String targetTeam,
            int maxTeamDiff
    ) {
        if (maxTeamDiff < 0) {
            return true;
        }
        int minPlayers = Integer.MAX_VALUE;
        int maxPlayers = Integer.MIN_VALUE;
        for (BaseTeam team : teams) {
            int size = team.getPlayerList().size();
            if (team.name.equals(currentTeam)) {
                size = Math.max(0, size - 1);
            }
            if (team.name.equals(targetTeam)) {
                size += 1;
            }
            minPlayers = Math.min(minPlayers, size);
            maxPlayers = Math.max(maxPlayers, size);
        }
        if (minPlayers == Integer.MAX_VALUE || maxPlayers == Integer.MIN_VALUE) {
            return true;
        }
        return (maxPlayers - minPlayers) <= maxTeamDiff;
    }
}
