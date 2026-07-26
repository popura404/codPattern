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
    @Deprecated(forRemoval = false, since = "mode-split-phase1")
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

    @Deprecated(forRemoval = false, since = "mode-split-phase1")
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

    @Deprecated(forRemoval = false, since = "mode-split-phase1")
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

    @Deprecated(forRemoval = false, since = "mode-split-phase1")
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

    @Deprecated(forRemoval = false, since = "mode-split-phase1")
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

    public static Builder builder(
            RoomId roomId,
            ModeRoomSummaryPort summaryPort,
            ModeRoomLifecyclePort lifecyclePort
    ) {
        return new Builder(roomId, summaryPort, lifecyclePort);
    }

    public static final class Builder {
        private final RoomId roomId;
        private final ModeRoomSummaryPort summaryPort;
        private final ModeRoomLifecyclePort lifecyclePort;
        private ModeRoomActionPort actionPort;
        private TeamRoomPort teamPort;
        private ReadyStatePort readyPort;
        private VoteControlPort votePort;
        private ModeCombatEventPort combatEventPort;
        private ModeRosterPort rosterPort;
        private ModeMapEditPort mapEditPort;
        private ModeKitDistributionPort kitDistributionPort;
        private ModeEntityCombatEventPort entityCombatEventPort;
        private ModeEntityLifecyclePort entityLifecyclePort;
        private ModeRoomTickPort tickPort;
        private ModeRuntimeStatePort runtimeStatePort;
        private ModeInteractableObjectPort interactableObjectPort;
        private ModePlayerRuntimeStatePort playerRuntimeStatePort;
        private ModeRespawnPolicyPort respawnPolicyPort;

        private Builder(
                RoomId roomId,
                ModeRoomSummaryPort summaryPort,
                ModeRoomLifecyclePort lifecyclePort
        ) {
            this.roomId = Objects.requireNonNull(roomId, "roomId");
            this.summaryPort = Objects.requireNonNull(summaryPort, "summaryPort");
            this.lifecyclePort = Objects.requireNonNull(lifecyclePort, "lifecyclePort");
        }

        public Builder withAction(ModeRoomActionPort port) {
            actionPort = port;
            return this;
        }

        public Builder withTeam(TeamRoomPort port) {
            teamPort = port;
            return this;
        }

        public Builder withReady(ReadyStatePort port) {
            readyPort = port;
            return this;
        }

        public Builder withVote(VoteControlPort port) {
            votePort = port;
            return this;
        }

        public Builder withCombatEvents(ModeCombatEventPort port) {
            combatEventPort = port;
            return this;
        }

        public Builder withRoster(ModeRosterPort port) {
            rosterPort = port;
            return this;
        }

        public Builder withMapEdit(ModeMapEditPort port) {
            mapEditPort = port;
            return this;
        }

        public Builder withKitDistribution(ModeKitDistributionPort port) {
            kitDistributionPort = port;
            return this;
        }

        public Builder withEntityCombatEvents(ModeEntityCombatEventPort port) {
            entityCombatEventPort = port;
            return this;
        }

        public Builder withEntityLifecycle(ModeEntityLifecyclePort port) {
            entityLifecyclePort = port;
            return this;
        }

        public Builder withTick(ModeRoomTickPort port) {
            tickPort = port;
            return this;
        }

        public Builder withRuntimeState(ModeRuntimeStatePort port) {
            runtimeStatePort = port;
            return this;
        }

        public Builder withInteractableObjects(ModeInteractableObjectPort port) {
            interactableObjectPort = port;
            return this;
        }

        public Builder withPlayerRuntimeState(ModePlayerRuntimeStatePort port) {
            playerRuntimeStatePort = port;
            return this;
        }

        public Builder withRespawnPolicy(ModeRespawnPolicyPort port) {
            respawnPolicyPort = port;
            return this;
        }

        @SuppressWarnings("deprecation")
        public ModeRoomHandle build() {
            return new ModeRoomHandle(
                    roomId,
                    summaryPort,
                    lifecyclePort,
                    Optional.ofNullable(actionPort),
                    Optional.ofNullable(teamPort),
                    Optional.ofNullable(readyPort),
                    Optional.ofNullable(votePort),
                    Optional.ofNullable(combatEventPort),
                    Optional.ofNullable(rosterPort),
                    Optional.ofNullable(mapEditPort),
                    Optional.ofNullable(kitDistributionPort),
                    Optional.ofNullable(entityCombatEventPort),
                    Optional.ofNullable(entityLifecyclePort),
                    Optional.ofNullable(tickPort),
                    Optional.ofNullable(runtimeStatePort),
                    Optional.ofNullable(interactableObjectPort),
                    Optional.ofNullable(playerRuntimeStatePort),
                    Optional.ofNullable(respawnPolicyPort));
        }
    }
}
