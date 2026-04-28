package com.cdp.codpattern.app.match.model;

import java.util.Objects;

public record TeamSummarySnapshot(
        String teamName,
        String displayNameKey,
        String shortNameKey,
        int playerCount,
        int score,
        int accentColor
) {
    public TeamSummarySnapshot {
        Objects.requireNonNull(teamName, "teamName");
        Objects.requireNonNull(displayNameKey, "displayNameKey");
        Objects.requireNonNull(shortNameKey, "shortNameKey");
    }
}
