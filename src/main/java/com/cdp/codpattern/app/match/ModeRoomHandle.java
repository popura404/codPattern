package com.cdp.codpattern.app.match;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.port.ModeCombatEventPort;
import com.cdp.codpattern.app.match.port.ModeRoomActionPort;
import com.cdp.codpattern.app.match.port.ModeRoomLifecyclePort;
import com.cdp.codpattern.app.match.port.ModeRoomSummaryPort;
import com.cdp.codpattern.app.match.port.ReadyStatePort;
import com.cdp.codpattern.app.match.port.TeamRoomPort;
import com.cdp.codpattern.app.match.port.VoteControlPort;

import java.util.Objects;
import java.util.Optional;

public record ModeRoomHandle(
        RoomId roomId,
        ModeRoomSummaryPort summaryPort,
        ModeRoomLifecyclePort lifecyclePort,
        Optional<ModeRoomActionPort> actionPort,
        Optional<TeamRoomPort> teamPort,
        Optional<ReadyStatePort> readyPort,
        Optional<VoteControlPort> votePort,
        Optional<ModeCombatEventPort> combatEventPort
) {
    public ModeRoomHandle {
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(summaryPort, "summaryPort");
        Objects.requireNonNull(lifecyclePort, "lifecyclePort");
        actionPort = actionPort == null ? Optional.empty() : actionPort;
        teamPort = teamPort == null ? Optional.empty() : teamPort;
        readyPort = readyPort == null ? Optional.empty() : readyPort;
        votePort = votePort == null ? Optional.empty() : votePort;
        combatEventPort = combatEventPort == null ? Optional.empty() : combatEventPort;
    }

    public ModeRoomHandle(
            RoomId roomId,
            ModeRoomSummaryPort summaryPort,
            ModeRoomActionPort actionPort,
            Optional<TeamRoomPort> teamPort,
            Optional<ReadyStatePort> readyPort,
            Optional<VoteControlPort> votePort,
            Optional<ModeCombatEventPort> combatEventPort
    ) {
        this(
                roomId,
                summaryPort,
                actionPort,
                Optional.ofNullable(actionPort),
                teamPort,
                readyPort,
                votePort,
                combatEventPort);
    }
}
