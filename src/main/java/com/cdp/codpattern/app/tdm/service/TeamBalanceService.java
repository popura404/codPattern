package com.cdp.codpattern.app.tdm.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public final class TeamBalanceService {
    public record TeamSnapshot(String name, int playerCount) {
    }

    private TeamBalanceService() {
    }

    public static Optional<String> chooseAutoJoinTeam(
            List<TeamSnapshot> teams,
            Predicate<String> teamFullChecker,
            int maxTeamDiff
    ) {
        List<TeamSnapshot> sortedTeams = new ArrayList<>(teams);
        sortedTeams.sort(Comparator.comparingInt(TeamSnapshot::playerCount));
        for (TeamSnapshot team : sortedTeams) {
            if (teamFullChecker.test(team.name())) {
                continue;
            }
            if (canJoinWithBalance(teams, team.name(), maxTeamDiff)) {
                return Optional.of(team.name());
            }
        }
        return Optional.empty();
    }

    public static boolean canJoinWithBalance(List<TeamSnapshot> teams, String joiningTeam, int maxTeamDiff) {
        if (maxTeamDiff < 0) {
            return true;
        }
        int minPlayers = Integer.MAX_VALUE;
        int maxPlayers = Integer.MIN_VALUE;
        for (TeamSnapshot team : teams) {
            int size = team.playerCount();
            if (team.name().equals(joiningTeam)) {
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
            List<TeamSnapshot> teams,
            String currentTeam,
            String targetTeam,
            int maxTeamDiff
    ) {
        if (maxTeamDiff < 0) {
            return true;
        }
        int minPlayers = Integer.MAX_VALUE;
        int maxPlayers = Integer.MIN_VALUE;
        for (TeamSnapshot team : teams) {
            int size = team.playerCount();
            if (team.name().equals(currentTeam)) {
                size = Math.max(0, size - 1);
            }
            if (team.name().equals(targetTeam)) {
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
