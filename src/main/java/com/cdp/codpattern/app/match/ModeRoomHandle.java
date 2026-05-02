package com.cdp.codpattern.app.match;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.port.ModeCombatEventPort;
import com.cdp.codpattern.app.match.port.ModeEntityCombatEventPort;
import com.cdp.codpattern.app.match.port.ModeEntityLifecyclePort;
import com.cdp.codpattern.app.match.port.ModeInteractableObjectPort;
import com.cdp.codpattern.app.match.port.ModeKitDistributionPort;
import com.cdp.codpattern.app.match.port.ModeMapEditPort;
import com.cdp.codpattern.app.match.port.ModePlayerRuntimeStatePort;
import com.cdp.codpattern.app.match.port.ModeRespawnPolicyPort;
import com.cdp.codpattern.app.match.port.ModeRoomActionPort;
import com.cdp.codpattern.app.match.port.ModeRoomLifecyclePort;
import com.cdp.codpattern.app.match.port.ModeRosterPort;
import com.cdp.codpattern.app.match.port.ModeRoomSummaryPort;
import com.cdp.codpattern.app.match.port.ModeRoomTickPort;
import com.cdp.codpattern.app.match.port.ModeRuntimeStatePort;
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
        Optional<ModeCombatEventPort> combatEventPort,
        Optional<ModeRosterPort> rosterPort,
        Optional<ModeMapEditPort> mapEditPort,
        Optional<ModeKitDistributionPort> kitDistributionPort,
        Optional<ModeEntityCombatEventPort> entityCombatEventPort,
        Optional<ModeEntityLifecyclePort> entityLifecyclePort,
        Optional<ModeRoomTickPort> tickPort,
        Optional<ModeRuntimeStatePort> runtimeStatePort,
        Optional<ModeInteractableObjectPort> interactableObjectPort,
        Optional<ModePlayerRuntimeStatePort> playerRuntimeStatePort,
        Optional<ModeRespawnPolicyPort> respawnPolicyPort
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
        rosterPort = rosterPort == null ? Optional.empty() : rosterPort;
        mapEditPort = mapEditPort == null ? Optional.empty() : mapEditPort;
        kitDistributionPort = kitDistributionPort == null ? Optional.empty() : kitDistributionPort;
        entityCombatEventPort = entityCombatEventPort == null ? Optional.empty() : entityCombatEventPort;
        entityLifecyclePort = entityLifecyclePort == null ? Optional.empty() : entityLifecyclePort;
        tickPort = tickPort == null ? Optional.empty() : tickPort;
        runtimeStatePort = runtimeStatePort == null ? Optional.empty() : runtimeStatePort;
        interactableObjectPort = interactableObjectPort == null ? Optional.empty() : interactableObjectPort;
        playerRuntimeStatePort = playerRuntimeStatePort == null ? Optional.empty() : playerRuntimeStatePort;
        respawnPolicyPort = respawnPolicyPort == null ? Optional.empty() : respawnPolicyPort;
    }

    public ModeRoomHandle(
            RoomId roomId,
            ModeRoomSummaryPort summaryPort,
            ModeRoomLifecyclePort lifecyclePort,
            Optional<ModeRoomActionPort> actionPort,
            Optional<TeamRoomPort> teamPort,
            Optional<ReadyStatePort> readyPort,
            Optional<VoteControlPort> votePort,
            Optional<ModeCombatEventPort> combatEventPort,
            Optional<ModeRosterPort> rosterPort,
            Optional<ModeMapEditPort> mapEditPort,
            Optional<ModeKitDistributionPort> kitDistributionPort
    ) {
        this(
                roomId,
                summaryPort,
                lifecyclePort,
                actionPort,
                teamPort,
                readyPort,
                votePort,
                combatEventPort,
                rosterPort,
                mapEditPort,
                kitDistributionPort,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
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
                combatEventPort,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    public ModeRoomHandle(
            RoomId roomId,
            ModeRoomSummaryPort summaryPort,
            ModeRoomActionPort actionPort,
            Optional<TeamRoomPort> teamPort,
            Optional<ReadyStatePort> readyPort,
            Optional<VoteControlPort> votePort,
            Optional<ModeCombatEventPort> combatEventPort,
            Optional<ModeMapEditPort> mapEditPort
    ) {
        this(
                roomId,
                summaryPort,
                actionPort,
                Optional.ofNullable(actionPort),
                teamPort,
                readyPort,
                votePort,
                combatEventPort,
                Optional.empty(),
                mapEditPort,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    public ModeRoomHandle(
            RoomId roomId,
            ModeRoomSummaryPort summaryPort,
            ModeRoomActionPort actionPort,
            Optional<TeamRoomPort> teamPort,
            Optional<ReadyStatePort> readyPort,
            Optional<VoteControlPort> votePort,
            Optional<ModeCombatEventPort> combatEventPort,
            Optional<ModeMapEditPort> mapEditPort,
            Optional<ModeKitDistributionPort> kitDistributionPort
    ) {
        this(
                roomId,
                summaryPort,
                actionPort,
                Optional.ofNullable(actionPort),
                teamPort,
                readyPort,
                votePort,
                combatEventPort,
                Optional.empty(),
                mapEditPort,
                kitDistributionPort,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }
}
