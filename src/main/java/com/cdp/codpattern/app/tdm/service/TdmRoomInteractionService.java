package com.cdp.codpattern.app.tdm.service;

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
    private static final String CODE_NOT_SPECTATOR = "NOT_SPECTATOR";
    private static final String CODE_BALANCE_EXCEEDED = "TEAM_BALANCE_EXCEEDED";
    private static final String CODE_UNKNOWN = "UNKNOWN";
    private static final String MID_MATCH_JOIN_DISABLED_MESSAGE = "对局进行中，当前已暂时禁用中途加入";

    private TdmRoomInteractionService() {
    }

    public record JoinResult(boolean success, String mapName, String code, String message) {
    }

    public record LeaveResult(boolean success, String roomName, String code, String message) {
    }

    public static JoinResult joinRoom(ServerPlayer player, String mapName, String teamName) {
        var gateway = FpsMatchGatewayProvider.gateway();
        Optional<CodTdmReadPort> readPortOptional = gateway.findMapReadPortByName(mapName);
        Optional<CodTdmActionPort> actionPortOptional = gateway.findMapActionPortByName(mapName);
        if (readPortOptional.isEmpty() || actionPortOptional.isEmpty()) {
            return failJoin(mapName, CODE_MAP_NOT_FOUND, "地图不存在");
        }

        CodTdmReadPort readPort = readPortOptional.get();
        CodTdmActionPort actionPort = actionPortOptional.get();
        CodTdmConfig config = CodTdmConfig.getConfig();

        if (readPort.containsJoinedPlayer(player.getUUID()) || readPort.containsSpectator(player)) {
            return okJoin(mapName, "ALREADY_JOINED", "");
        }

        boolean isPlaying = readPort.isPlayingPhase();
        boolean isWaiting = readPort.isWaitingPhase();
        if (isPlaying && isMidMatchJoinTemporarilyDisabled()) {
            return failJoin(mapName, CODE_PHASE_LOCKED, MID_MATCH_JOIN_DISABLED_MESSAGE);
        }
        if (isPlaying && !config.isAllowJoinDuringPlaying()) {
            return failJoin(mapName, CODE_PHASE_LOCKED, "对局进行中，当前配置禁止中途加入");
        }
        if (!isPlaying && !isWaiting) {
            return failJoin(mapName, CODE_PHASE_LOCKED, "当前阶段不可加入房间");
        }

        String requestedTeam = normalizeTeam(teamName);
        if (requestedTeam != null && !readPort.hasTeam(requestedTeam)) {
            return failJoin(mapName, CODE_TEAM_NOT_FOUND, "队伍不存在");
        }

        if (isPlaying) {
            /*
             * 对局进行中统一先以旁观状态加入房间：
             * 1) 保持未准备状态；
             * 2) 可在房间内选边；
             * 3) 通过客户端已有 3 秒确认倒计时再入战。
             */
            actionPort.joinSpectator(player);
            actionPort.initializeReadyState(player);
            actionPort.consumeSpectatorPreferredTeam(player);

            int maxTeamDiff = config.getMaxTeamDiff();
            Optional<String> preferredTeam = resolvePlayableTeam(readPort, requestedTeam, maxTeamDiff);
            if (preferredTeam.isPresent()) {
                actionPort.setSpectatorPreferredTeam(player, preferredTeam.get());
            }

            warnIfMissingEndTeleport(player, readPort);
            actionPort.syncToClient();
            return okJoin(mapName, "SPECTATOR", "");
        }

        String targetTeam = requestedTeam;
        if (targetTeam == null) {
            Optional<String> autoTeam = readPort.chooseAutoJoinTeam(config.getMaxTeamDiff());
            if (autoTeam.isEmpty()) {
                return failJoin(mapName, CODE_BALANCE_EXCEEDED, "无法分配队伍：队伍已满或超出人数差限制");
            }
            targetTeam = autoTeam.get();
        } else {
            if (readPort.isTeamFull(targetTeam)) {
                return failJoin(mapName, CODE_TEAM_FULL, "目标队伍已满");
            }
            if (!readPort.canJoinWithBalance(targetTeam, config.getMaxTeamDiff())) {
                return failJoin(mapName, CODE_BALANCE_EXCEEDED, "加入该队伍会超出人数差限制");
            }
        }

        actionPort.joinTeam(targetTeam, player);
        if (!readPort.containsJoinedPlayer(player.getUUID())) {
            return failJoin(mapName, CODE_UNKNOWN, "加入失败，请稍后重试");
        }

        actionPort.initializeReadyState(player);
        warnIfMissingEndTeleport(player, readPort);
        actionPort.syncToClient();
        return okJoin(mapName, "OK", "");
    }

    public static LeaveResult leaveRoom(ServerPlayer player) {
        Optional<String> roomName = FpsMatchGatewayProvider.gateway().leaveCurrentMapIncludingSpectator(player);
        if (roomName.isPresent()) {
            return new LeaveResult(true, roomName.get(), "OK", "");
        }
        return new LeaveResult(false, "", "NOT_IN_ROOM", "当前不在房间内");
    }

    public static JoinResult joinGameFromSpectator(ServerPlayer player, String mapName) {
        if (mapName == null || mapName.isBlank()) {
            return failJoin("", CODE_MAP_NOT_FOUND, "地图不存在");
        }

        var gateway = FpsMatchGatewayProvider.gateway();
        Optional<CodTdmReadPort> readPortOptional = gateway.findMapReadPortByName(mapName);
        Optional<CodTdmActionPort> actionPortOptional = gateway.findMapActionPortByName(mapName);
        if (readPortOptional.isEmpty() || actionPortOptional.isEmpty()) {
            return failJoin(mapName, CODE_MAP_NOT_FOUND, "地图不存在");
        }

        CodTdmReadPort readPort = readPortOptional.get();
        CodTdmActionPort actionPort = actionPortOptional.get();
        if (!readPort.containsSpectator(player)) {
            return failJoin(mapName, CODE_NOT_SPECTATOR, "当前不是旁观状态");
        }
        if (!readPort.isPlayingPhase()) {
            return failJoin(mapName, CODE_PHASE_LOCKED, "当前阶段不可加入游戏");
        }

        if (isMidMatchJoinTemporarilyDisabled()) {
            return failJoin(mapName, CODE_PHASE_LOCKED, MID_MATCH_JOIN_DISABLED_MESSAGE);
        }

        CodTdmConfig config = CodTdmConfig.getConfig();
        if (!config.isAllowJoinDuringPlaying()) {
            return failJoin(mapName, CODE_PHASE_LOCKED, "对局进行中，当前配置禁止中途加入");
        }

        int maxTeamDiff = config.getMaxTeamDiff();
        Optional<String> preferredTeam = actionPort.consumeSpectatorPreferredTeam(player)
                .filter(teamNameCandidate -> canJoinTeam(readPort, teamNameCandidate, maxTeamDiff));
        Optional<String> autoTeam = readPort.chooseAutoJoinTeam(maxTeamDiff);
        String targetTeam = preferredTeam.orElseGet(() -> autoTeam.orElse(null));
        if (targetTeam == null) {
            return failJoin(mapName, CODE_BALANCE_EXCEEDED, "无法加入游戏：队伍已满或超出人数差限制");
        }

        actionPort.joinTeam(targetTeam, player);
        if (!readPort.containsJoinedPlayer(player.getUUID())) {
            return failJoin(mapName, CODE_UNKNOWN, "加入游戏失败，请稍后重试");
        }

        actionPort.initializeReadyState(player);
        actionPort.respawnPlayerNow(player);
        actionPort.syncToClient();
        return okJoin(mapName, "OK", "");
    }

    public static void switchTeam(ServerPlayer player, String teamName) {
        FpsMatchGatewayProvider.gateway()
                .findPlayerTdmActionPort(player)
                .ifPresent(port -> port.switchTeam(player, teamName));
    }

    public static void selectTeamInRoom(ServerPlayer player, String mapName, String teamName) {
        if (mapName == null || mapName.isBlank() || teamName == null || teamName.isBlank()) {
            return;
        }
        var gateway = FpsMatchGatewayProvider.gateway();
        Optional<CodTdmReadPort> readPortOptional = gateway.findMapReadPortByName(mapName);
        Optional<CodTdmActionPort> actionPortOptional = gateway.findMapActionPortByName(mapName);
        if (readPortOptional.isEmpty() || actionPortOptional.isEmpty()) {
            return;
        }
        CodTdmReadPort readPort = readPortOptional.get();
        CodTdmActionPort actionPort = actionPortOptional.get();

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
            actionPort.syncToClient();
            return;
        }

        if (readPort.isPlayingPhase()) {
            if (isMidMatchJoinTemporarilyDisabled()) {
                player.sendSystemMessage(Component.translatable("message.codpattern.room.mid_join_disabled"));
                return;
            }
            // 进行中：仅记录旁观者的预选边，待“加入游戏”倒计时完成后真正入战。
            actionPort.setSpectatorPreferredTeam(player, teamName);
            player.sendSystemMessage(Component.translatable("message.codpattern.team.selected_for_join", teamName));
            return;
        }

        player.sendSystemMessage(Component.translatable("message.codpattern.game.team_switch_locked"));
    }

    public static String setReadyState(ServerPlayer player, boolean ready) {
        Optional<CodTdmActionPort> actionPortOptional = FpsMatchGatewayProvider.gateway()
                .findPlayerTdmActionPort(player);
        if (actionPortOptional.isEmpty()) {
            return "§c未加入 TDM 房间";
        }
        if (actionPortOptional.get().setPlayerReady(player, ready)) {
            return ready ? Component.translatable("message.codpattern.room.ready").getString()
                    : Component.translatable("message.codpattern.room.not_ready").getString();
        }
        return Component.translatable("message.codpattern.room.ready_change_locked").getString();
    }

    public static void initiateStartVote(ServerPlayer player) {
        FpsMatchGatewayProvider.gateway()
                .findPlayerTdmActionPort(player)
                .ifPresent(port -> port.initiateStartVote(player.getUUID()));
    }

    public static void initiateEndVote(ServerPlayer player) {
        FpsMatchGatewayProvider.gateway()
                .findPlayerTdmActionPort(player)
                .ifPresent(port -> port.initiateEndVote(player.getUUID()));
    }

    public static void submitVoteResponse(ServerPlayer player, long voteId, boolean accepted) {
        FpsMatchGatewayProvider.gateway()
                .findPlayerTdmActionPort(player)
                .ifPresent(port -> port.submitVoteResponse(player.getUUID(), voteId, accepted));
    }

    private static JoinResult okJoin(String mapName, String code, String message) {
        return new JoinResult(true, mapName, code, message);
    }

    private static JoinResult failJoin(String mapName, String code, String message) {
        return new JoinResult(false, mapName, code, message);
    }

    private static String normalizeTeam(String team) {
        if (team == null || team.isBlank()) {
            return null;
        }
        return team.trim();
    }

    private static Optional<String> resolvePlayableTeam(CodTdmReadPort readPort, String requestedTeam, int maxTeamDiff) {
        if (requestedTeam != null) {
            if (canJoinTeam(readPort, requestedTeam, maxTeamDiff)) {
                return Optional.of(requestedTeam);
            }
            return Optional.empty();
        }
        return readPort.chooseAutoJoinTeam(maxTeamDiff);
    }

    private static boolean canJoinTeam(CodTdmReadPort readPort, String teamName, int maxTeamDiff) {
        if (teamName == null || teamName.isBlank()) {
            return false;
        }
        if (!readPort.hasTeam(teamName)) {
            return false;
        }
        if (readPort.isTeamFull(teamName)) {
            return false;
        }
        return readPort.canJoinWithBalance(teamName, maxTeamDiff);
    }

    private static void warnIfMissingEndTeleport(ServerPlayer player, CodTdmReadPort readPort) {
        if (!readPort.hasMatchEndTeleportPoint()) {
            player.sendSystemMessage(
                    Component.translatable("message.codpattern.game.warning_no_end_teleport", readPort.mapName()));
        }
    }

    private static boolean isMidMatchJoinTemporarilyDisabled() {
        return true;
    }
}
