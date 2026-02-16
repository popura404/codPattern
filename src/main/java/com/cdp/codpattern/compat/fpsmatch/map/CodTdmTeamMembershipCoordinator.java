package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.config.tdm.CodTdmConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

final class CodTdmTeamMembershipCoordinator {
    private final CodTdmTeamMembershipPort port;
    private final Consumer<ServerPlayer> leaveFromMapAction;
    private final CodTdmLeaveRoomEffects leaveRoomEffects;

    CodTdmTeamMembershipCoordinator(CodTdmTeamMembershipPort port, Consumer<ServerPlayer> leaveFromMapAction) {
        this.port = port;
        this.leaveFromMapAction = leaveFromMapAction;
        this.leaveRoomEffects = new CodTdmLeaveRoomEffects(port.serverLevel(), port::mapName);
    }

    void leaveRoom(ServerPlayer player) {
        UUID playerId = player.getUUID();
        port.clearTransientPlayerState(playerId);
        port.clearPlayerCombatStats(playerId);
        port.removePlayerFromVote(playerId);

        leaveRoomEffects.handleTeleportResult(player, port.teleportPlayerToMatchEndPoint(player));
        leaveRoomEffects.sendLeaveStatePackets(player);
        port.clearPlayerInventory(player);
        leaveFromMapAction.accept(player);
        port.syncToClient();
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
        port.leaveTeam(player);
        port.joinTeam(teamName, player);
        port.syncToClient();
    }
}
