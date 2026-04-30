package com.cdp.codpattern.app.tdm.service;

import com.cdp.codpattern.app.match.service.ModeRoomInteractionService;
import com.cdp.codpattern.compat.fpsmatch.FpsMatchGatewayProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Legacy TDM facade retained for older call sites.
 */
public final class TdmRoomInteractionService {
    private TdmRoomInteractionService() {
    }

    public record JoinResult(boolean success, String roomKey, String code, String message) {
    }

    public record LeaveResult(boolean success, String roomKey, String code, String message) {
    }

    public static JoinResult joinRoom(ServerPlayer player, String roomKey, String teamName) {
        ModeRoomInteractionService.JoinResult result = ModeRoomInteractionService.joinRoom(player, roomKey, teamName);
        return new JoinResult(result.success(), result.roomKey(), result.code(), result.message());
    }

    public static LeaveResult leaveRoom(ServerPlayer player) {
        ModeRoomInteractionService.LeaveResult result = ModeRoomInteractionService.leaveRoom(player);
        return new LeaveResult(result.success(), result.roomKey(), result.code(), result.message());
    }

    public static void switchTeam(ServerPlayer player, String teamName) {
        if (player == null) {
            return;
        }
        FpsMatchGatewayProvider.gateway()
                .findPlayerRoomLifecyclePort(player)
                .ifPresent(port -> ModeRoomInteractionService.selectTeamInRoom(
                        player,
                        port.roomId().encode(),
                        teamName));
    }

    public static void selectTeamInRoom(ServerPlayer player, String roomKey, String teamName) {
        ModeRoomInteractionService.selectTeamInRoom(player, roomKey, teamName);
    }

    public static Component setReadyState(ServerPlayer player, boolean ready) {
        return ModeRoomInteractionService.setReadyState(player, ready);
    }

    public static void initiateStartVote(ServerPlayer player) {
        ModeRoomInteractionService.initiateStartVote(player);
    }

    public static void initiateEndVote(ServerPlayer player) {
        ModeRoomInteractionService.initiateEndVote(player);
    }

    public static void submitVoteResponse(ServerPlayer player, long voteId, boolean accepted) {
        ModeRoomInteractionService.submitVoteResponse(player, voteId, accepted);
    }
}
