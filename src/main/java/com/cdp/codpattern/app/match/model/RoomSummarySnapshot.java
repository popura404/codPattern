package com.cdp.codpattern.app.match.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record RoomSummarySnapshot(
        RoomId roomId,
        String lifecycleStateKey,
        int playerCount,
        int maxPlayers,
        int remainingTimeTicks,
        List<RoomSummaryMetric> metrics,
        List<TeamSummarySnapshot> teams,
        Optional<ModeClientPayload> modePayload
) {
    public RoomSummarySnapshot {
        Objects.requireNonNull(roomId, "roomId");
        lifecycleStateKey = lifecycleStateKey == null ? "" : lifecycleStateKey;
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
        teams = teams == null ? List.of() : List.copyOf(teams);
        modePayload = modePayload == null ? Optional.empty() : modePayload;
    }
}
