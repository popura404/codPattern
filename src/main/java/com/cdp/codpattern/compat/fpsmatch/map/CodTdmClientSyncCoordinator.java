package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.model.TdmGamePhase;
import com.cdp.codpattern.config.tdm.CodTdmConfig;
import com.cdp.codpattern.app.tdm.service.PhaseStateMachine;
import com.cdp.codpattern.fpsmatch.room.CodTdmRoomManager;
import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.network.tdm.GamePhasePacket;
import com.cdp.codpattern.network.tdm.ScoreUpdatePacket;
import com.cdp.codpattern.network.tdm.TeamPlayerListPacket;
import com.cdp.codpattern.network.tdm.CombatMarkerConfigPacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

final class CodTdmClientSyncCoordinator {
    private final CodTdmClientSyncPort port;
    private final Supplier<TdmGamePhase> phaseSupplier;
    private final IntSupplier phaseTimerSupplier;
    private final IntSupplier gameTimeTicksSupplier;
    private final Supplier<Map<String, Integer>> teamScoresSupplier;

    CodTdmClientSyncCoordinator(
            CodTdmClientSyncPort port,
            Supplier<TdmGamePhase> phaseSupplier,
            IntSupplier phaseTimerSupplier,
            IntSupplier gameTimeTicksSupplier,
            Supplier<Map<String, Integer>> teamScoresSupplier
    ) {
        this.port = port;
        this.phaseSupplier = phaseSupplier;
        this.phaseTimerSupplier = phaseTimerSupplier;
        this.gameTimeTicksSupplier = gameTimeTicksSupplier;
        this.teamScoresSupplier = teamScoresSupplier;
    }

    void syncToClient() {
        TdmGamePhase phase = phaseSupplier.get();
        int phaseTimer = phaseTimerSupplier.getAsInt();
        int gameTimeTicks = gameTimeTicksSupplier.getAsInt();
        Map<String, Integer> teamScores = teamScoresSupplier.get();
        CodTdmConfig config = CodTdmConfig.getConfig();
        int remainingTime = PhaseStateMachine.getRemainingTimeTicks(phase, phaseTimer, gameTimeTicks, config);

        GamePhasePacket phasePacket = new GamePhasePacket(phase.name(), remainingTime);
        ScoreUpdatePacket scorePacket = new ScoreUpdatePacket(teamScores, gameTimeTicks);
        TeamPlayerListPacket playerListPacket = new TeamPlayerListPacket(port.mapName(), port.getTeamPlayers());
        CombatMarkerConfigPacket markerConfigPacket = new CombatMarkerConfigPacket(
                config.isEnemyMarkerHealthBar(),
                config.getMarkerFocusHalfAngleDegrees(),
                config.getMarkerFocusRequiredTicks(),
                config.getMarkerBarMaxDistance(),
                config.getMarkerVisibleGraceTicks());

        Map<UUID, ServerPlayer> recipients = new LinkedHashMap<>();
        for (ServerPlayer player : port.getJoinedPlayers()) {
            recipients.put(player.getUUID(), player);
        }
        for (ServerPlayer player : port.getSpectatorPlayers()) {
            recipients.put(player.getUUID(), player);
        }

        for (ServerPlayer player : recipients.values()) {
            ModNetworkChannel.sendToPlayer(phasePacket, player);
            ModNetworkChannel.sendToPlayer(scorePacket, player);
            ModNetworkChannel.sendToPlayer(playerListPacket, player);
            ModNetworkChannel.sendToPlayer(markerConfigPacket, player);
        }

        CodTdmRoomManager.getInstance().markRoomListDirty();
    }
}
