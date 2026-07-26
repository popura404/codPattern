package com.cdp.codpattern.app.teammatch;

import com.cdp.codpattern.app.match.ModeRoomHandle;
import com.cdp.codpattern.app.match.model.JoinRoomRequest;
import com.cdp.codpattern.app.match.model.JoinRoomResult;
import com.cdp.codpattern.app.match.model.LeaveRoomResult;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.port.ModeCombatEventPort;
import com.cdp.codpattern.app.match.port.ModeMapEditPort;
import com.cdp.codpattern.app.match.port.ModeRoomLifecyclePort;
import com.cdp.codpattern.app.match.port.ModeRosterPort;
import com.cdp.codpattern.app.match.port.TeamRoomPort;
import com.cdp.codpattern.app.tdm.port.CodTdmActionPort;
import com.cdp.codpattern.app.tdm.port.CodTdmReadPort;
import com.cdp.codpattern.app.tdm.service.RoomFoodLockService;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointKind;
import com.phasetranscrystal.fpsmatch.core.data.SpawnSelectionReason;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** Shared high-level room/runtime composition for Frontline and Team Deathmatch. */
public final class TeamMatchRuntime {
    private static final String CODE_PHASE_LOCKED = "PHASE_LOCKED";
    private static final String CODE_TEAM_NOT_FOUND = "TEAM_NOT_FOUND";
    private static final String CODE_TEAM_FULL = "TEAM_FULL";
    private static final String CODE_BALANCE_EXCEEDED = "TEAM_BALANCE_EXCEEDED";
    private static final String CODE_UNKNOWN = "UNKNOWN";

    private final TeamMatchPolicy policy;
    private final CodTdmReadPort readPort;
    private final CodTdmActionPort actionPort;

    public TeamMatchRuntime(
            TeamMatchPolicy policy,
            CodTdmReadPort readPort,
            CodTdmActionPort actionPort
    ) {
        this.policy = java.util.Objects.requireNonNull(policy, "policy");
        this.readPort = java.util.Objects.requireNonNull(readPort, "readPort");
        this.actionPort = java.util.Objects.requireNonNull(actionPort, "actionPort");
    }

    public TeamMatchPolicy policy() {
        return policy;
    }

    public String gameType() {
        return policy.gameType();
    }

    public ModeRoomHandle createRoomHandle(ModeCombatEventPort combatEvents, ModeMapEditPort mapEditPort) {
        return ModeRoomHandle.builder(
                        readPort.roomId(),
                        readPort,
                        lifecyclePort())
                .withAction(actionPort)
                .withTeam(teamPort())
                .withReady(actionPort)
                .withVote(actionPort)
                .withCombatEvents(combatEvents)
                .withRoster(rosterPort())
                .withMapEdit(mapEditPort)
                .build();
    }

    public boolean teleportPlayerToSpawnPoint(
            SpawnSelectionReason reason,
            Function<SpawnPointKind, Boolean> teleportAttempt
    ) {
        if (teleportAttempt == null) {
            return false;
        }
        for (SpawnPointKind kind : policy.spawnSelectionOrder(reason)) {
            if (Boolean.TRUE.equals(teleportAttempt.apply(kind))) {
                return true;
            }
        }
        return false;
    }

    private ModeRoomLifecyclePort lifecyclePort() {
        return new ModeRoomLifecyclePort() {
            @Override
            public RoomId roomId() {
                return readPort.roomId();
            }

            @Override
            public String gameType() {
                return readPort.gameType();
            }

            @Override
            public String mapName() {
                return readPort.mapName();
            }

            @Override
            public String modeDisplayNameKey() {
                return readPort.modeDisplayNameKey();
            }

            @Override
            public JoinRoomResult join(ServerPlayer player, JoinRoomRequest request) {
                if (player == null) {
                    return JoinRoomResult.failure(roomId(), "PLAYER_MISSING", "");
                }
                if (readPort.containsJoinedPlayer(player.getUUID()) || readPort.containsSpectator(player)) {
                    return JoinRoomResult.success(roomId(), "ALREADY_JOINED");
                }
                if (!readPort.isWaitingPhase()) {
                    return JoinRoomResult.failure(roomId(), CODE_PHASE_LOCKED, "");
                }

                JoinRoomRequest resolvedRequest = request == null ? JoinRoomRequest.autoTeam() : request;
                if (resolvedRequest.spectator()) {
                    actionPort.joinSpectator(player);
                    actionPort.syncToClient();
                    return JoinRoomResult.success(roomId(), "OK");
                }

                String requestedTeam = normalizeTeam(resolvedRequest.preferredTeamName().orElse(null));
                if (requestedTeam != null && !readPort.hasTeam(requestedTeam)) {
                    return JoinRoomResult.failure(roomId(), CODE_TEAM_NOT_FOUND, "");
                }

                int maxTeamDifference = policy.configuration().maxTeamDifference();
                String targetTeam = requestedTeam;
                if (targetTeam == null) {
                    Optional<String> autoTeam = readPort.chooseAutoJoinTeam(maxTeamDifference);
                    if (autoTeam.isEmpty()) {
                        return JoinRoomResult.failure(roomId(), CODE_BALANCE_EXCEEDED, "");
                    }
                    targetTeam = autoTeam.get();
                } else {
                    if (readPort.isTeamFull(targetTeam)) {
                        return JoinRoomResult.failure(roomId(), CODE_TEAM_FULL, "");
                    }
                    if (!readPort.canJoinWithBalance(targetTeam, maxTeamDifference)) {
                        return JoinRoomResult.failure(roomId(), CODE_BALANCE_EXCEEDED, "");
                    }
                }

                actionPort.joinTeam(targetTeam, player);
                if (!readPort.containsJoinedPlayer(player.getUUID())) {
                    return JoinRoomResult.failure(roomId(), CODE_UNKNOWN, "");
                }

                actionPort.initializeReadyState(player);
                RoomFoodLockService.enforce(player);
                warnIfMissingEndTeleport(player);
                actionPort.syncToClient();
                return JoinRoomResult.success(roomId(), "OK");
            }

            @Override
            public LeaveRoomResult leave(ServerPlayer player) {
                if (player == null) {
                    return LeaveRoomResult.failure(roomId(), "PLAYER_MISSING", "");
                }
                actionPort.leaveRoom(player);
                return LeaveRoomResult.success(roomId(), "OK");
            }

            @Override
            public void syncToClient() {
                actionPort.syncToClient();
            }
        };
    }

    private TeamRoomPort teamPort() {
        return new TeamRoomPort() {
            @Override
            public List<com.cdp.codpattern.app.match.model.TeamDescriptor> teamDescriptors() {
                return readPort.teamDescriptors();
            }

            @Override
            public Map<String, Integer> teamPlayerCountsSnapshot() {
                return readPort.getTeamPlayerCountsSnapshot();
            }

            @Override
            public boolean hasTeam(String teamName) {
                return readPort.hasTeam(teamName);
            }

            @Override
            public boolean isTeamFull(String teamName) {
                return readPort.isTeamFull(teamName);
            }

            @Override
            public Optional<String> findTeamNameByPlayer(ServerPlayer player) {
                return readPort.findTeamNameByPlayer(player);
            }

            @Override
            public void switchTeam(ServerPlayer player, String teamName) {
                if (player == null || teamName == null || teamName.isBlank()) {
                    return;
                }
                boolean inJoinedTeam = readPort.containsJoinedPlayer(player.getUUID());
                boolean inSpectator = readPort.containsSpectator(player);
                if (!inJoinedTeam && !inSpectator) {
                    return;
                }
                if (inJoinedTeam) {
                    actionPort.switchTeam(player, teamName);
                    return;
                }
                if (!readPort.hasTeam(teamName)) {
                    player.sendSystemMessage(Component.translatable("message.codpattern.team.not_found", teamName));
                    return;
                }
                if (readPort.isTeamFull(teamName)) {
                    player.sendSystemMessage(Component.translatable("message.codpattern.team.full"));
                    return;
                }
                int maxTeamDifference = policy.configuration().maxTeamDifference();
                if (!readPort.canJoinWithBalance(teamName, maxTeamDifference)) {
                    player.sendSystemMessage(Component.translatable("message.codpattern.team.join_balance_exceeded"));
                    return;
                }
                if (!readPort.isWaitingPhase()) {
                    player.sendSystemMessage(Component.translatable("message.codpattern.game.team_switch_locked"));
                    return;
                }

                actionPort.joinTeam(teamName, player);
                actionPort.initializeReadyState(player);
                RoomFoodLockService.enforce(player);
                actionPort.syncToClient();
            }
        };
    }

    private ModeRosterPort rosterPort() {
        return new ModeRosterPort() {
            @Override
            public void requestRosterResync(ServerPlayer player) {
                actionPort.requestRosterResync(player);
            }

            @Override
            public void requestRosterPreview(ServerPlayer player) {
                actionPort.requestRosterPreview(player);
            }
        };
    }

    private static String normalizeTeam(String team) {
        if (team == null || team.isBlank()) {
            return null;
        }
        return team.trim();
    }

    private void warnIfMissingEndTeleport(ServerPlayer player) {
        if (!readPort.hasMatchEndTeleportPoint()) {
            player.sendSystemMessage(
                    Component.translatable("message.codpattern.game.warning_no_end_teleport", readPort.mapName()));
        }
    }
}
