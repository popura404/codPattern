package com.cdp.codpattern.app.match.model;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.port.ModeRoomReadPort;
import com.cdp.codpattern.app.match.port.ModeRoomSummaryPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RoomSummarySnapshots {
    private RoomSummarySnapshots() {
    }

    public static RoomSummarySnapshot fromSummaryPort(ModeRoomSummaryPort summaryPort) {
        if (summaryPort instanceof ModeRoomReadPort readPort) {
            return fromReadPort(readPort);
        }
        return new RoomSummarySnapshot(
                summaryPort.roomId(),
                summaryPort.lifecycleStateKey(),
                summaryPort.playerCount(),
                summaryPort.maxPlayers(),
                summaryPort.remainingTimeTicks(),
                summaryPort.metrics(),
                GameModeRegistry.capabilities(summaryPort.gameType()),
                List.of(),
                Optional.empty());
    }

    public static RoomSummarySnapshot fromReadPort(ModeRoomReadPort readPort) {
        Map<String, Integer> teamPlayerCounts = readPort.getTeamPlayerCountsSnapshot();
        Map<String, Integer> teamScores = readPort.getTeamScoresSnapshot();
        List<TeamSummarySnapshot> teams = new ArrayList<>();
        for (TeamDescriptor descriptor : readPort.teamDescriptors()) {
            teams.add(new TeamSummarySnapshot(
                    descriptor.teamName(),
                    descriptor.displayNameKey(),
                    descriptor.shortNameKey(),
                    teamPlayerCounts.getOrDefault(descriptor.teamName(), 0),
                    teamScores.getOrDefault(descriptor.teamName(), 0),
                    descriptor.accentColor()));
        }
        for (Map.Entry<String, Integer> entry : teamPlayerCounts.entrySet()) {
            boolean alreadyAdded = teams.stream()
                    .anyMatch(team -> team.teamName().equals(entry.getKey()));
            if (!alreadyAdded) {
                teams.add(new TeamSummarySnapshot(
                        entry.getKey(),
                        entry.getKey(),
                        entry.getKey(),
                        entry.getValue(),
                        teamScores.getOrDefault(entry.getKey(), 0),
                        0xFFB8C2CC));
            }
        }
        return new RoomSummarySnapshot(
                readPort.roomId(),
                readPort.lifecycleStateKey(),
                readPort.playerCount(),
                readPort.maxPlayers(),
                readPort.remainingTimeTicks(),
                readPort.metrics(),
                GameModeRegistry.capabilities(readPort.gameType()),
                teams,
                Optional.empty());
    }
}
