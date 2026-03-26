package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.config.tdm.CodTdmConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

final class CodTdmTeamMembershipCoordinator {
    private static final int RECONNECT_GRACE_TICKS = 180 * 20;

    private final CodTdmTeamMembershipPort port;
    private final Consumer<ServerPlayer> leaveFromMapAction;
    private final Runnable resetGameAction;
    private final CodTdmLeaveRoomEffects leaveRoomEffects;

    CodTdmTeamMembershipCoordinator(
            CodTdmTeamMembershipPort port,
            Consumer<ServerPlayer> leaveFromMapAction,
            Runnable resetGameAction
    ) {
        this.port = port;
        this.leaveFromMapAction = leaveFromMapAction;
        this.resetGameAction = resetGameAction;
        this.leaveRoomEffects = new CodTdmLeaveRoomEffects(port.serverLevel(), port::mapName);
    }

    void leaveRoom(ServerPlayer player) {
        UUID playerId = player.getUUID();
        port.clearTransientPlayerState(playerId);
        port.clearPlayerCombatStats(playerId);
        port.removePlayerFromVote(playerId);
        port.clearSpectatorPreferredTeam(playerId);
        port.clearDisconnected(playerId);

        leaveRoomEffects.handleTeleportResult(player, port.teleportPlayerToMatchEndPoint(player));
        leaveRoomEffects.sendLeaveStatePackets(player);
        port.clearPlayerInventory(player);
        leaveFromMapAction.accept(player);
        if (!port.isWaitingPhase() && !port.hasJoinedPlayers()) {
            resetGameAction.run();
        }
        port.syncToClient();
    }

    void handleUnexpectedDisconnect(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        port.clearTransientPlayerState(playerId);
        port.markDisconnected(playerId, RECONNECT_GRACE_TICKS);
        port.syncToClient();
    }

    void handleReconnect(ServerPlayer player) {
        if (player == null) {
            return;
        }
        port.clearDisconnected(player.getUUID());
    }

    void tickDisconnectedPlayers() {
        Iterator<Map.Entry<UUID, Integer>> iterator = port.disconnectedGraceTimers().entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            int nextTicks = entry.getValue() - 1;
            if (nextTicks > 0) {
                entry.setValue(nextTicks);
                continue;
            }

            UUID playerId = entry.getKey();
            iterator.remove();
            port.clearTransientPlayerState(playerId);
            port.clearPlayerCombatStats(playerId);
            port.removePlayerFromVote(playerId);
            port.clearSpectatorPreferredTeam(playerId);
            port.removePlayerById(playerId);
            CodTdmDeferredLeaveRegistry.register(playerId);
            if (!port.isWaitingPhase() && !port.hasJoinedPlayers()) {
                resetGameAction.run();
            }
            port.syncToClient();
        }
    }

    void switchTeam(ServerPlayer player, String teamName) {
        if (!port.isWaitingPhase()) {
            player.sendSystemMessage(Component.translatable("message.codpattern.game.team_switch_locked"));
            return;
        }
        if (!port.hasTeam(teamName)) {
            player.sendSystemMessage(Component.translatable("message.codpattern.team.not_found", teamName));
            return;
        }

        Optional<String> currentTeamOpt = port.findTeamNameByPlayer(player);
        if (currentTeamOpt.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.codpattern.team.not_joined"));
            return;
        }

        String currentTeam = currentTeamOpt.get();
        if (currentTeam.equals(teamName)) {
            return;
        }
        if (port.isTeamFull(teamName)) {
            player.sendSystemMessage(Component.translatable("message.codpattern.team.full"));
            return;
        }

        int maxTeamDiff = CodTdmConfig.getConfig().getMaxTeamDiff();
        if (!port.canSwitchWithBalance(currentTeam, teamName, maxTeamDiff)) {
            player.sendSystemMessage(Component.translatable("message.codpattern.team.balance_exceeded"));
            return;
        }

        port.clearTransientPlayerState(player.getUUID());
        port.clearSpectatorPreferredTeam(player.getUUID());
        port.leaveTeam(player);
        port.joinTeam(teamName, player);
        port.syncToClient();
    }

    void setSpectatorPreferredTeam(ServerPlayer player, String teamName) {
        if (player == null || teamName == null || teamName.isBlank()) {
            return;
        }
        port.setSpectatorPreferredTeam(player.getUUID(), teamName);
    }

    Optional<String> consumeSpectatorPreferredTeam(ServerPlayer player) {
        if (player == null) {
            return Optional.empty();
        }
        UUID playerId = player.getUUID();
        Optional<String> preferredTeam = port.getSpectatorPreferredTeam(playerId);
        port.clearSpectatorPreferredTeam(playerId);
        return preferredTeam;
    }
}
