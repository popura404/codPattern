package com.cdp.codpattern.app.match.model;

import java.util.List;

public record ModeDescriptor(
        String gameType,
        String displayNameKey,
        String roomHeaderKey,
        String createCommand,
        List<TeamDescriptor> teams
) {
    public ModeDescriptor {
        teams = List.copyOf(teams);
    }
}
