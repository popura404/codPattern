package com.cdp.codpattern.app.tdm.service;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.port.ModeRoomActionPort;
import com.cdp.codpattern.app.match.port.ModeRoomReadPort;
import com.cdp.codpattern.app.tdm.port.CodTdmActionPort;
import com.cdp.codpattern.app.tdm.port.CodTdmReadPort;
import com.cdp.codpattern.compat.fpsmatch.FpsMatchGatewayProvider;
import com.cdp.codpattern.config.tdm.CodTdmConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * TDM room interaction services used by network packet handlers.
 */
public final class TdmRoomInteractionService {
    private static final String CODE_MAP_NOT_FOUND = "MAP_NOT_FOUND";
    private static final String CODE_PHASE_LOCKED = "PHASE_LOCKED";
    private static final String CODE_TEAM_NOT_FOUND = "TEAM_NOT_FOUND";
    private static final String CODE_TEAM_FULL = "TEAM_FULL";
    private static final String CODE_BALANCE_EXCEEDED = "TEAM_BALANCE_EXCEEDED";
    private static final String CODE_UNKNOWN = "UNKNOWN";

    private TdmRoomInteractionService() {
    }

    public record JoinResult(boolean success, String roomKey, String code, String message) {
    }

    public record LeaveResult(boolean success, String roomKey, String code, String message) {
    }

    public static JoinResult joinRoom(ServerPlayer player, String roomKey, String teamName) {
        RoomId roomId = decodeRoomId(roomKey);
        if (roomId == null) {
            return failJoin("", CODE_MAP_NOT_FOUND, "");
        }
        var gateway = FpsMatchGatewayProvider.gateway();
        Optional<ModeRoomReadPort> readPortOptional = gateway.findRoomReadPort(roomId);
        Optional<ModeRoomActionPort> actionPortOptional = gateway.findRoomActionPort(roomId);
        if (readPortOptional.isEmpty() || actionPortOptional.isEmpty()) {
            return failJoin(roomId.encode(), CODE_MAP_NOT_FOUND, "");
        }

        ModeRoomReadPort readPort = readPortOptional.get();
        ModeRoomActionPort actionPort = actionPortOptional.get();
        if (readPort.containsJoinedPlayer(player.getUUID()) || readPort.containsSpectator(player)) {
            return okJoin(roomId.encode(), "ALREADY_JOINED", "");
        }

        if (!readPort.isWaitingPhase()) {
            return failJoin(roomId.encode(), CODE_PHASE_LOCKED, "");
        }

        String requestedTeam = normalizeTeam(teamName);
        if (requestedTeam != null && !readPort.hasTeam(requestedTeam)) {
            return failJoin(roomId.encode(), CODE_TEAM_NOT_FOUND, "");
        }

        String targetTeam = requestedTeam;
        if (targetTeam == null) {
            Optional<String> autoTeam = readPort.chooseAutoJoinTeam(CodTdmConfig.getConfig().getMaxTeamDiff());
            if (autoTeam.isEmpty()) {
                return failJoin(roomId.encode(), CODE_BALANCE_EXCEEDED, "");
            }
            targetTeam = autoTeam.get();
        } else {
            if (readPort.isTeamFull(targetTeam)) {
                return failJoin(roomId.encode(), CODE_TEAM_FULL, "");
            }
            if (!readPort.canJoinWithBalance(targetTeam, CodTdmConfig.getConfig().getMaxTeamDiff())) {
                return failJoin(roomId.encode(), CODE_BALANCE_EXCEEDED, "");
            }
        }

        actionPort.joinTeam(targetTeam, player);
        if (!readPort.containsJoinedPlayer(player.getUUID())) {
            return failJoin(roomId.encode(), CODE_UNKNOWN, "");
        }

        actionPort.initializeReadyState(player);
        RoomFoodLockService.enforce(player);
        warnIfMissingEndTeleport(player, readPort);
        actionPort.syncToClient();
        return okJoin(roomId.encode(), "OK", "");
    }

    public static LeaveResult leaveRoom(ServerPlayer player) {
        var gateway = FpsMatchGatewayProvider.gateway();
        Optional<ModeRoomReadPort> readPortOptional = gateway.findPlayerRoomReadPort(player);
        Optional<ModeRoomActionPort> actionPortOptional = gateway.findPlayerRoomActionPort(player);
        if (readPortOptional.isPresent() && actionPortOptional.isPresent()) {
            String roomKey = readPortOptional.get().roomId().encode();
            actionPortOptional.get().leaveRoom(player);
            return new LeaveResult(true, roomKey, "OK", "");
        }
        return new LeaveResult(false, "", "NOT_IN_ROOM", "");
    }

    public static void switchTeam(ServerPlayer player, String teamName) {
        FpsMatchGatewayProvider.gateway()
                .findPlayerRoomActionPort(player)
                .ifPresent(port -> port.switchTeam(player, teamName));
    }

    public static void selectTeamInRoom(ServerPlayer player, String roomKey, String teamName) {
        RoomId roomId = decodeRoomId(roomKey);
        if (roomId == null || teamName == null || teamName.isBlank()) {
            return;
        }
        var gateway = FpsMatchGatewayProvider.gateway();
        Optional<ModeRoomReadPort> readPortOptional = gateway.findRoomReadPort(roomId);
        Optional<ModeRoomActionPort> actionPortOptional = gateway.findRoomActionPort(roomId);
        if (readPortOptional.isEmpty() || actionPortOptional.isEmpty()) {
            return;
        }
        ModeRoomReadPort readPort = readPortOptional.get();
        ModeRoomActionPort actionPort = actionPortOptional.get();

        // 安全校验：只允许房间内成员（正式队员/旁观）触发队伍操作。
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
        int maxTeamDiff = CodTdmConfig.getConfig().getMaxTeamDiff();
        if (!readPort.canJoinWithBalance(teamName, maxTeamDiff)) {
            player.sendSystemMessage(Component.translatable("message.codpattern.team.join_balance_exceeded"));
            return;
        }

        if (readPort.isWaitingPhase()) {
            actionPort.joinTeam(teamName, player);
            actionPort.initializeReadyState(player);
            RoomFoodLockService.enforce(player);
            actionPort.syncToClient();
            return;
        }

        player.sendSystemMessage(Component.translatable("message.codpattern.game.team_switch_locked"));
    }

    public static Component setReadyState(ServerPlayer player, boolean ready) {
        Optional<ModeRoomActionPort> actionPortOptional = FpsMatchGatewayProvider.gateway()
                .findPlayerRoomActionPort(player);
        if (actionPortOptional.isEmpty()) {
            return Component.translatable("message.codpattern.room.not_joined_tdm");
        }
        if (actionPortOptional.get().setPlayerReady(player, ready)) {
            return ready ? Component.translatable("message.codpattern.room.ready")
                    : Component.translatable("message.codpattern.room.not_ready");
        }
        return Component.translatable("message.codpattern.room.ready_change_locked");
    }

    public static void initiateStartVote(ServerPlayer player) {
        FpsMatchGatewayProvider.gateway()
                .findPlayerRoomActionPort(player)
                .ifPresent(port -> port.initiateStartVote(player.getUUID()));
    }

    public static void initiateEndVote(ServerPlayer player) {
        FpsMatchGatewayProvider.gateway()
                .findPlayerRoomActionPort(player)
                .ifPresent(port -> port.initiateEndVote(player.getUUID()));
    }

    public static void submitVoteResponse(ServerPlayer player, long voteId, boolean accepted) {
        FpsMatchGatewayProvider.gateway()
                .findPlayerRoomActionPort(player)
                .ifPresent(port -> port.submitVoteResponse(player.getUUID(), voteId, accepted));
    }

    private static JoinResult okJoin(String roomKey, String code, String message) {
        return new JoinResult(true, roomKey, code, message);
    }

    private static JoinResult failJoin(String roomKey, String code, String message) {
        return new JoinResult(false, roomKey, code, message);
    }

    private static String normalizeTeam(String team) {
        if (team == null || team.isBlank()) {
            return null;
        }
        return team.trim();
    }

    private static void warnIfMissingEndTeleport(ServerPlayer player, ModeRoomReadPort readPort) {
        if (!readPort.hasMatchEndTeleportPoint()) {
            player.sendSystemMessage(
                    Component.translatable("message.codpattern.game.warning_no_end_teleport", readPort.mapName()));
        }
    }

    private static RoomId decodeRoomId(String roomKey) {
        if (roomKey == null || roomKey.isBlank()) {
            return null;
        }
        try {
            return RoomId.decode(roomKey);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
