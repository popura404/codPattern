package com.cdp.codpattern.app.match.service;

import com.cdp.codpattern.app.match.model.JoinRoomRequest;
import com.cdp.codpattern.app.match.model.JoinRoomResult;
import com.cdp.codpattern.app.match.model.LeaveRoomResult;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.port.ModePlayerRuntimeStatePort;
import com.cdp.codpattern.app.match.port.ModeRoomLifecyclePort;
import com.cdp.codpattern.app.match.port.ReadyStatePort;
import com.cdp.codpattern.app.match.port.TeamRoomPort;
import com.cdp.codpattern.app.match.port.VoteControlPort;
import com.cdp.codpattern.compat.fpsmatch.FpsMatchGateway;
import com.cdp.codpattern.compat.fpsmatch.FpsMatchGatewayProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public final class ModeRoomInteractionService {
    private static final String CODE_MAP_NOT_FOUND = "MAP_NOT_FOUND";
    private static final String CODE_NOT_IN_ROOM = "NOT_IN_ROOM";

    private ModeRoomInteractionService() {
    }

    public record JoinResult(boolean success, String roomKey, String code, String message) {
    }

    public record LeaveResult(boolean success, String roomKey, String code, String message) {
    }

    public static JoinResult joinRoom(ServerPlayer player, String roomKey, String teamName) {
        RoomId roomId = decodeRoomId(roomKey);
        if (roomId == null) {
            return new JoinResult(false, "", CODE_MAP_NOT_FOUND, "");
        }

        FpsMatchGateway gateway = FpsMatchGatewayProvider.gateway();
        Optional<ModeRoomLifecyclePort> lifecyclePort = gateway.findRoomLifecyclePort(roomId);
        if (lifecyclePort.isEmpty()) {
            return new JoinResult(false, roomId.encode(), CODE_MAP_NOT_FOUND, "");
        }
        return fromResult(lifecyclePort.get().join(player, joinRequest(teamName)));
    }

    public static LeaveResult leaveRoom(ServerPlayer player) {
        FpsMatchGateway gateway = FpsMatchGatewayProvider.gateway();
        Optional<ModeRoomLifecyclePort> lifecyclePort = gateway.findPlayerRoomLifecyclePort(player);
        if (lifecyclePort.isEmpty()) {
            return new LeaveResult(false, "", CODE_NOT_IN_ROOM, "");
        }
        Optional<ModePlayerRuntimeStatePort> playerStatePort = gateway.findPlayerStatePort(player);
        LeaveResult result = fromResult(lifecyclePort.get().leave(player));
        if (result.success() && player != null) {
            playerStatePort.ifPresent(port -> port.clearPlayerState(player.getUUID()));
        }
        return result;
    }

    public static void selectTeamInRoom(ServerPlayer player, String roomKey, String teamName) {
        RoomId roomId = decodeRoomId(roomKey);
        if (player == null || roomId == null || teamName == null || teamName.isBlank()) {
            return;
        }

        FpsMatchGateway gateway = FpsMatchGatewayProvider.gateway();
        Optional<TeamRoomPort> teamPort = gateway.findRoomTeamPort(roomId);
        if (teamPort.isEmpty()) {
            return;
        }
        if (!teamPort.get().hasTeam(teamName)) {
            player.sendSystemMessage(Component.translatable("message.codpattern.team.not_found", teamName));
            return;
        }
        teamPort.get().switchTeam(player, teamName);
    }

    public static Component setReadyState(ServerPlayer player, boolean ready) {
        FpsMatchGateway gateway = FpsMatchGatewayProvider.gateway();
        Optional<ReadyStatePort> readyPort = gateway.findPlayerReadyStatePort(player);
        if (readyPort.isEmpty()) {
            return Component.translatable("message.codpattern.room.not_joined_tdm");
        }
        if (readyPort.get().setPlayerReady(player, ready)) {
            return ready ? Component.translatable("message.codpattern.room.ready")
                    : Component.translatable("message.codpattern.room.not_ready");
        }
        return Component.translatable("message.codpattern.room.ready_change_locked");
    }

    public static void initiateStartVote(ServerPlayer player) {
        if (player == null) {
            return;
        }
        FpsMatchGateway gateway = FpsMatchGatewayProvider.gateway();
        Optional<VoteControlPort> votePort = gateway.findPlayerVoteControlPort(player);
        if (votePort.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.codpattern.room.not_in_room"));
            return;
        }
        votePort.get().initiateStartVote(player.getUUID());
    }

    public static void initiateEndVote(ServerPlayer player) {
        FpsMatchGateway gateway = FpsMatchGatewayProvider.gateway();
        gateway.findPlayerVoteControlPort(player)
                .ifPresent(port -> port.initiateEndVote(player.getUUID()));
    }

    public static void submitVoteResponse(ServerPlayer player, long voteId, boolean accepted) {
        FpsMatchGateway gateway = FpsMatchGatewayProvider.gateway();
        gateway.findPlayerVoteControlPort(player)
                .ifPresent(port -> port.submitVoteResponse(player.getUUID(), voteId, accepted));
    }

    private static JoinRoomRequest joinRequest(String teamName) {
        if (teamName == null || teamName.isBlank()) {
            return JoinRoomRequest.autoTeam();
        }
        return JoinRoomRequest.team(teamName.trim());
    }

    private static JoinResult fromResult(JoinRoomResult result) {
        RoomId roomId = result == null ? null : result.roomId();
        return new JoinResult(
                result != null && result.success(),
                roomId == null ? "" : roomId.encode(),
                result == null ? CODE_MAP_NOT_FOUND : result.code(),
                result == null ? "" : result.message());
    }

    private static LeaveResult fromResult(LeaveRoomResult result) {
        RoomId roomId = result == null ? null : result.roomId();
        return new LeaveResult(
                result != null && result.success(),
                roomId == null ? "" : roomId.encode(),
                result == null ? CODE_NOT_IN_ROOM : result.code(),
                result == null ? "" : result.message());
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
