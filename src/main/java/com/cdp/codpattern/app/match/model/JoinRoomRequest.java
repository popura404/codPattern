package com.cdp.codpattern.app.match.model;

import java.util.Optional;

public record JoinRoomRequest(
        Optional<String> preferredTeamName,
        boolean spectator
) {
    public JoinRoomRequest {
        preferredTeamName = preferredTeamName == null ? Optional.empty() : preferredTeamName;
    }

    public static JoinRoomRequest autoTeam() {
        return new JoinRoomRequest(Optional.empty(), false);
    }

    public static JoinRoomRequest team(String teamName) {
        return new JoinRoomRequest(Optional.ofNullable(teamName).filter(name -> !name.isBlank()), false);
    }

    public static JoinRoomRequest spectatorOnly() {
        return new JoinRoomRequest(Optional.empty(), true);
    }
}
