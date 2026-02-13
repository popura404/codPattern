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
            player.sendSystemMessage(Component.literal("§c队伍不存在: " + teamName));
            return;
        }

        Optional<String> currentTeamOpt = port.findTeamNameByPlayer(player);
        if (currentTeamOpt.isEmpty()) {
            player.sendSystemMessage(Component.literal("§c当前未加入可切换的队伍"));
            return;
        }

        String currentTeam = currentTeamOpt.get();
        if (currentTeam.equals(teamName)) {
            return;
        }
        if (port.isTeamFull(teamName)) {
            player.sendSystemMessage(Component.literal("§c目标队伍已满"));
            return;
        }

        int maxTeamDiff = CodTdmConfig.getConfig().getMaxTeamDiff();
        if (!port.canSwitchWithBalance(currentTeam, teamName, maxTeamDiff)) {
            player.sendSystemMessage(Component.literal("§c切换后将超出队伍人数差限制"));
            return;
        }

        port.clearTransientPlayerState(player.getUUID());
        port.leaveTeam(player);
        port.joinTeam(teamName, player);
        port.syncToClient();
    }
}
