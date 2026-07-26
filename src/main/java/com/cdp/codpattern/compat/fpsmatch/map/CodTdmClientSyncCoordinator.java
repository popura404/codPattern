package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.app.match.runtime.roster.RoomRosterSyncCoordinator;
import com.cdp.codpattern.app.tdm.model.TdmGamePhase;
import com.cdp.codpattern.app.tdm.service.PhaseStateMachine;
import com.cdp.codpattern.config.tdm.CodTdmConfig;
import com.cdp.codpattern.fpsmatch.room.CodTdmRoomManager;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import com.cdp.codpattern.network.match.CombatMarkerConfigPacket;
import com.cdp.codpattern.network.match.GamePhasePacket;
import com.cdp.codpattern.network.match.RoomPlayerDeltaPacket;
import com.cdp.codpattern.network.match.RoomPreviewRosterPacket;
import com.cdp.codpattern.network.match.RoomRosterDelta;
import com.cdp.codpattern.network.match.ScoreUpdatePacket;
import com.cdp.codpattern.network.match.TeamPlayerListPacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** TDM packet/presentation facade over the shared roster synchronization coordinator. */
final class CodTdmClientSyncCoordinator {
    private static final long ROSTER_DELTA_FLUSH_MS = 150L;
    private static final long ROSTER_FULL_SNAPSHOT_MS = 7000L;

    private final CodTdmClientSyncPort port;
    private final Supplier<TdmGamePhase> phaseSupplier;
    private final IntSupplier phaseTimerSupplier;
    private final IntSupplier gameTimeTicksSupplier;
    private final Supplier<Map<String, Integer>> teamScoresSupplier;
    private final RoomRosterSyncCoordinator<ServerPlayer> rosterCoordinator;

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
        this.rosterCoordinator = new RoomRosterSyncCoordinator<>(
                new TdmRosterSource(),
                new TdmRosterPublisher(),
                RoomRosterSyncCoordinator.Settings.deltaEnabled(
                        ROSTER_DELTA_FLUSH_MS,
                        ROSTER_FULL_SNAPSHOT_MS),
                System::currentTimeMillis);
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
        CombatMarkerConfigPacket markerConfigPacket = new CombatMarkerConfigPacket(
                config.getMarkerFocusHalfAngleDegrees(),
                config.getMarkerFocusRequiredTicks(),
                config.getMarkerBarMaxDistance(),
                config.getMarkerVisibleGraceTicks());

        for (ServerPlayer player : currentRecipients()) {
            ModNetworkChannel.sendToPlayer(phasePacket, player);
            ModNetworkChannel.sendToPlayer(scorePacket, player);
            ModNetworkChannel.sendToPlayer(markerConfigPacket, player);
        }
        rosterCoordinator.synchronize(false);
        CodTdmRoomManager.getInstance().markRoomListDirty();
    }

    void tick() {
        rosterCoordinator.synchronize(true);
    }

    void requestRosterResync(ServerPlayer player) {
        rosterCoordinator.requestResync(player);
    }

    void requestRosterPreview(ServerPlayer player) {
        rosterCoordinator.requestPreview(player);
    }

    private Collection<ServerPlayer> currentRecipients() {
        Map<UUID, ServerPlayer> recipients = new LinkedHashMap<>();
        for (ServerPlayer player : port.getJoinedPlayers()) {
            recipients.put(player.getUUID(), player);
        }
        for (ServerPlayer player : port.getSpectatorPlayers()) {
            recipients.put(player.getUUID(), player);
        }
        return recipients.values();
    }

    private final class TdmRosterSource implements RoomRosterSyncCoordinator.Source<ServerPlayer> {
        @Override
        public String roomKey() {
            return port.roomKey();
        }

        @Override
        public Map<String, List<PlayerInfo>> rosterSnapshot() {
            return port.getTeamPlayers();
        }

        @Override
        public Collection<ServerPlayer> liveRecipients() {
            return currentRecipients();
        }

        @Override
        public UUID recipientId(ServerPlayer recipient) {
            return recipient.getUUID();
        }

        @Override
        public boolean canRequestResync(ServerPlayer requester) {
            UUID requesterId = requester.getUUID();
            return currentRecipients().stream().anyMatch(player -> player.getUUID().equals(requesterId));
        }
    }

    private static final class TdmRosterPublisher implements RoomRosterSyncCoordinator.Publisher<ServerPlayer> {
        @Override
        public void publishFull(
                String roomKey,
                int version,
                Map<String, List<PlayerInfo>> snapshot,
                Collection<ServerPlayer> recipients
        ) {
            TeamPlayerListPacket packet = new TeamPlayerListPacket(roomKey, version, snapshot);
            for (ServerPlayer player : recipients) {
                ModNetworkChannel.sendToPlayer(packet, player);
            }
        }

        @Override
        public void publishDelta(
                String roomKey,
                int version,
                List<RoomRosterDelta> updates,
                Collection<ServerPlayer> recipients
        ) {
            RoomPlayerDeltaPacket packet = new RoomPlayerDeltaPacket(roomKey, version, updates);
            for (ServerPlayer player : recipients) {
                ModNetworkChannel.sendToPlayer(packet, player);
            }
        }

        @Override
        public void publishPreview(
                String roomKey,
                int version,
                Map<String, List<PlayerInfo>> snapshot,
                ServerPlayer requester
        ) {
            ModNetworkChannel.sendToPlayer(new RoomPreviewRosterPacket(roomKey, version, snapshot), requester);
        }
    }
}
