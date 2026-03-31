package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.model.TdmGamePhase;
import com.cdp.codpattern.app.tdm.service.PhaseStateMachine;
import com.cdp.codpattern.config.tdm.CodTdmConfig;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import com.cdp.codpattern.fpsmatch.room.CodTdmRoomManager;
import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.network.tdm.CombatMarkerConfigPacket;
import com.cdp.codpattern.network.tdm.GamePhasePacket;
import com.cdp.codpattern.network.tdm.RoomPlayerDeltaPacket;
import com.cdp.codpattern.network.tdm.ScoreUpdatePacket;
import com.cdp.codpattern.network.tdm.TeamPlayerListPacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

final class CodTdmClientSyncCoordinator {
    private static final long ROSTER_DELTA_FLUSH_MS = 150L;
    private static final long ROSTER_FULL_SNAPSHOT_MS = 7000L;

    private final CodTdmClientSyncPort port;
    private final Supplier<TdmGamePhase> phaseSupplier;
    private final IntSupplier phaseTimerSupplier;
    private final IntSupplier gameTimeTicksSupplier;
    private final Supplier<Map<String, Integer>> teamScoresSupplier;

    private final Set<UUID> knownRecipients = new HashSet<>();
    private final Set<UUID> bootstrapRecipients = new HashSet<>();
    private Map<String, List<PlayerInfo>> pendingRosterSnapshot = new HashMap<>();
    private Map<UUID, PlayerRosterState> pendingRosterByPlayer = new HashMap<>();
    private Map<UUID, PlayerRosterState> lastSentRosterByPlayer = new HashMap<>();
    private final Map<UUID, RoomPlayerDeltaPacket.PlayerDelta> pendingDeltaUpdates = new LinkedHashMap<>();
    private int rosterVersion = 0;
    private boolean fullSnapshotPendingForAll = true;
    private long lastDeltaFlushAtMs = 0L;
    private long lastFullSnapshotAtMs = 0L;

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
        CombatMarkerConfigPacket markerConfigPacket = new CombatMarkerConfigPacket(
                config.getMarkerFocusHalfAngleDegrees(),
                config.getMarkerFocusRequiredTicks(),
                config.getMarkerBarMaxDistance(),
                config.getMarkerVisibleGraceTicks());

        Map<UUID, ServerPlayer> recipients = currentRecipients();
        for (ServerPlayer player : recipients.values()) {
            ModNetworkChannel.sendToPlayer(phasePacket, player);
            ModNetworkChannel.sendToPlayer(scorePacket, player);
            ModNetworkChannel.sendToPlayer(markerConfigPacket, player);
        }

        captureRosterChanges(recipients);
        flushRosterSync(recipients, false);
        CodTdmRoomManager.getInstance().markRoomListDirty();
    }

    void tick() {
        Map<UUID, ServerPlayer> recipients = currentRecipients();
        captureRosterChanges(recipients);
        flushRosterSync(recipients, true);
    }

    void requestRosterResync(ServerPlayer player) {
        if (player == null) {
            return;
        }
        Map<UUID, ServerPlayer> recipients = currentRecipients();
        if (!recipients.containsKey(player.getUUID())) {
            return;
        }
        fullSnapshotPendingForAll = true;
        pendingDeltaUpdates.clear();
        flushRosterSync(recipients, true);
    }

    private void captureRosterChanges(Map<UUID, ServerPlayer> recipients) {
        Map<String, List<PlayerInfo>> latestSnapshot = deepCopyTeamPlayers(port.getTeamPlayers());
        Map<UUID, PlayerRosterState> latestByPlayer = flatten(latestSnapshot);
        pendingRosterSnapshot = latestSnapshot;
        pendingRosterByPlayer = latestByPlayer;

        Set<UUID> currentRecipientIds = recipients.keySet();
        Set<UUID> newRecipients = new HashSet<>(currentRecipientIds);
        newRecipients.removeAll(knownRecipients);
        knownRecipients.clear();
        knownRecipients.addAll(currentRecipientIds);
        bootstrapRecipients.retainAll(currentRecipientIds);

        if (!newRecipients.isEmpty()) {
            if (pendingDeltaUpdates.isEmpty() && !fullSnapshotPendingForAll && rosterVersion > 0) {
                bootstrapRecipients.addAll(newRecipients);
            } else {
                fullSnapshotPendingForAll = true;
            }
        }

        if (rosterVersion <= 0) {
            fullSnapshotPendingForAll = true;
            return;
        }
        if (hasStructuralChange(latestByPlayer)) {
            pendingDeltaUpdates.clear();
            bootstrapRecipients.clear();
            fullSnapshotPendingForAll = true;
            return;
        }

        for (Map.Entry<UUID, PlayerRosterState> entry : latestByPlayer.entrySet()) {
            UUID playerId = entry.getKey();
            PlayerRosterState previousState = lastSentRosterByPlayer.get(playerId);
            if (previousState == null) {
                pendingDeltaUpdates.clear();
                bootstrapRecipients.clear();
                fullSnapshotPendingForAll = true;
                return;
            }

            PlayerInfo previous = previousState.player();
            PlayerInfo current = entry.getValue().player();
            int changedMask = buildChangedMask(previous, current);
            if (changedMask == 0) {
                continue;
            }

            pendingDeltaUpdates.put(playerId, new RoomPlayerDeltaPacket.PlayerDelta(
                    playerId,
                    entry.getValue().teamName(),
                    changedMask,
                    current));
        }
    }

    private void flushRosterSync(Map<UUID, ServerPlayer> recipients, boolean fromTick) {
        if (recipients.isEmpty()) {
            knownRecipients.clear();
            bootstrapRecipients.clear();
            return;
        }

        long now = System.currentTimeMillis();
        if (fullSnapshotPendingForAll) {
            sendFullSnapshotToAll(recipients.values(), true, now);
            return;
        }

        if (!pendingDeltaUpdates.isEmpty()) {
            boolean calibrationDue = lastFullSnapshotAtMs <= 0L
                    || now - lastFullSnapshotAtMs >= ROSTER_FULL_SNAPSHOT_MS;
            if (calibrationDue) {
                sendFullSnapshotToAll(recipients.values(), true, now);
                return;
            }
            if (fromTick || now - lastDeltaFlushAtMs >= ROSTER_DELTA_FLUSH_MS) {
                sendDeltaPacket(recipients.values(), now);
                return;
            }
        }

        if (!bootstrapRecipients.isEmpty()) {
            sendBootstrapSnapshots(recipients);
        }

        if (pendingDeltaUpdates.isEmpty()
                && rosterVersion > 0
                && now - lastFullSnapshotAtMs >= ROSTER_FULL_SNAPSHOT_MS) {
            sendFullSnapshotToAll(recipients.values(), false, now);
        }
    }

    private void sendBootstrapSnapshots(Map<UUID, ServerPlayer> recipients) {
        if (bootstrapRecipients.isEmpty()) {
            return;
        }
        int effectiveVersion = Math.max(1, rosterVersion);
        TeamPlayerListPacket packet = new TeamPlayerListPacket(port.roomKey(), effectiveVersion, pendingRosterSnapshot);
        for (UUID recipientId : new HashSet<>(bootstrapRecipients)) {
            ServerPlayer player = recipients.get(recipientId);
            if (player != null) {
                ModNetworkChannel.sendToPlayer(packet, player);
            }
            bootstrapRecipients.remove(recipientId);
        }
    }

    private void sendFullSnapshotToAll(Collection<ServerPlayer> recipients, boolean advanceVersion, long now) {
        int effectiveVersion = advanceVersion ? Math.max(1, rosterVersion + 1) : Math.max(1, rosterVersion);
        TeamPlayerListPacket packet = new TeamPlayerListPacket(port.roomKey(), effectiveVersion, pendingRosterSnapshot);
        for (ServerPlayer player : recipients) {
            ModNetworkChannel.sendToPlayer(packet, player);
        }
        rosterVersion = effectiveVersion;
        lastSentRosterByPlayer = new HashMap<>(pendingRosterByPlayer);
        pendingDeltaUpdates.clear();
        bootstrapRecipients.clear();
        fullSnapshotPendingForAll = false;
        lastFullSnapshotAtMs = now;
        lastDeltaFlushAtMs = now;
    }

    private void sendDeltaPacket(Collection<ServerPlayer> recipients, long now) {
        int nextRosterVersion = Math.max(1, rosterVersion + 1);
        List<RoomPlayerDeltaPacket.PlayerDelta> updates = new ArrayList<>(pendingDeltaUpdates.values());
        RoomPlayerDeltaPacket packet = new RoomPlayerDeltaPacket(port.roomKey(), nextRosterVersion, updates);
        for (ServerPlayer player : recipients) {
            ModNetworkChannel.sendToPlayer(packet, player);
        }
        rosterVersion = nextRosterVersion;
        lastSentRosterByPlayer = new HashMap<>(pendingRosterByPlayer);
        pendingDeltaUpdates.clear();
        lastDeltaFlushAtMs = now;
    }

    private Map<UUID, ServerPlayer> currentRecipients() {
        Map<UUID, ServerPlayer> recipients = new LinkedHashMap<>();
        for (ServerPlayer player : port.getJoinedPlayers()) {
            recipients.put(player.getUUID(), player);
        }
        for (ServerPlayer player : port.getSpectatorPlayers()) {
            recipients.put(player.getUUID(), player);
        }
        return recipients;
    }

    private boolean hasStructuralChange(Map<UUID, PlayerRosterState> latestByPlayer) {
        if (latestByPlayer.size() != lastSentRosterByPlayer.size()) {
            return true;
        }
        for (Map.Entry<UUID, PlayerRosterState> entry : latestByPlayer.entrySet()) {
            PlayerRosterState previous = lastSentRosterByPlayer.get(entry.getKey());
            if (previous == null) {
                return true;
            }
            if (!previous.teamName().equals(entry.getValue().teamName())) {
                return true;
            }
        }
        return false;
    }

    private int buildChangedMask(PlayerInfo previous, PlayerInfo current) {
        int mask = 0;
        if (previous.isReady() != current.isReady()) {
            mask |= RoomPlayerDeltaPacket.CHANGE_READY;
        }
        if (previous.kills() != current.kills() || previous.deaths() != current.deaths()) {
            mask |= RoomPlayerDeltaPacket.CHANGE_STATS;
        }
        if (previous.isAlive() != current.isAlive()) {
            mask |= RoomPlayerDeltaPacket.CHANGE_LIFE;
        }
        if (previous.isInvincible() != current.isInvincible()) {
            mask |= RoomPlayerDeltaPacket.CHANGE_INVINCIBLE;
        }
        if (pingBucket(previous.pingMs()) != pingBucket(current.pingMs())) {
            mask |= RoomPlayerDeltaPacket.CHANGE_PING_BUCKET;
        }
        if (previous.maxKillStreak() != current.maxKillStreak()) {
            mask |= RoomPlayerDeltaPacket.CHANGE_STREAK;
        }
        return mask;
    }

    private int pingBucket(int pingMs) {
        if (pingMs < 0) {
            return 5;
        }
        if (pingMs < 150) {
            return 0;
        }
        if (pingMs < 300) {
            return 1;
        }
        if (pingMs < 600) {
            return 2;
        }
        if (pingMs < 1000) {
            return 3;
        }
        return 4;
    }

    private Map<String, List<PlayerInfo>> deepCopyTeamPlayers(Map<String, List<PlayerInfo>> source) {
        Map<String, List<PlayerInfo>> copied = new HashMap<>();
        if (source == null) {
            return copied;
        }
        for (Map.Entry<String, List<PlayerInfo>> entry : source.entrySet()) {
            copied.put(entry.getKey(), new ArrayList<>(entry.getValue() == null ? List.of() : entry.getValue()));
        }
        return copied;
    }

    private Map<UUID, PlayerRosterState> flatten(Map<String, List<PlayerInfo>> teamPlayers) {
        Map<UUID, PlayerRosterState> flattened = new HashMap<>();
        for (Map.Entry<String, List<PlayerInfo>> entry : teamPlayers.entrySet()) {
            String teamName = entry.getKey();
            for (PlayerInfo player : entry.getValue()) {
                flattened.put(player.uuid(), new PlayerRosterState(teamName, player));
            }
        }
        return flattened;
    }

    private record PlayerRosterState(String teamName, PlayerInfo player) {
    }
}
