package com.cdp.codpattern.app.match.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record GameModeDefinition(
        String gameType,
        List<String> aliases,
        String displayNameKey,
        String roomHeaderKey,
        String createCommand,
        List<TeamDescriptor> teams,
        ModeFamily family,
        TeamPolicy teamPolicy,
        JoinPolicy joinPolicy,
        LifecycleKind lifecycleKind,
        ScoreboardKind scoreboardKind,
        Set<ModeCapability> capabilities
) {
    public GameModeDefinition {
        Objects.requireNonNull(gameType, "gameType");
        Objects.requireNonNull(displayNameKey, "displayNameKey");
        Objects.requireNonNull(roomHeaderKey, "roomHeaderKey");
        Objects.requireNonNull(createCommand, "createCommand");
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(teamPolicy, "teamPolicy");
        Objects.requireNonNull(joinPolicy, "joinPolicy");
        Objects.requireNonNull(lifecycleKind, "lifecycleKind");
        Objects.requireNonNull(scoreboardKind, "scoreboardKind");

        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        teams = teams == null ? List.of() : List.copyOf(teams);
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }

    public ModeDescriptor descriptor() {
        return new ModeDescriptor(gameType, displayNameKey, roomHeaderKey, createCommand, teams);
    }

    public boolean hasCapability(ModeCapability capability) {
        return capabilities.contains(capability);
    }
}
