package com.cdp.codpattern.app.match.runtime.roster;

import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import com.cdp.codpattern.network.match.RoomRosterDelta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/**
 * Shared roster synchronization state machine. Roster construction, recipients, authorization,
 * time, cadence, and packet publication are injected so mode-specific projections stay separate.
 */
public final class RoomRosterSyncCoordinator<R> {
    public enum DeliveryMode {
        FULL_AND_DELTA,
        FULL_SNAPSHOT_ONLY
    }

    public enum ResyncDelivery {
        ALL_LIVE_RECIPIENTS,
        REQUESTER_ONLY
    }

    public interface Source<R> {
        String roomKey();

        Map<String, List<PlayerInfo>> rosterSnapshot();

        Collection<R> liveRecipients();

        UUID recipientId(R recipient);

        boolean canRequestResync(R requester);
    }

    public interface Publisher<R> {
        void publishFull(
                String roomKey,
                int version,
                Map<String, List<PlayerInfo>> snapshot,
                Collection<R> recipients
        );

        void publishDelta(
                String roomKey,
                int version,
                List<RoomRosterDelta> updates,
                Collection<R> recipients
        );

        void publishPreview(
                String roomKey,
                int version,
                Map<String, List<PlayerInfo>> snapshot,
                R requester
        );
    }

    public record Settings(
            DeliveryMode deliveryMode,
            ResyncDelivery resyncDelivery,
            long deltaFlushMs,
            long fullCalibrationMs,
            IntSupplier externalVersionSupplier
    ) {
        public Settings {
            deliveryMode = Objects.requireNonNull(deliveryMode, "deliveryMode");
            resyncDelivery = Objects.requireNonNull(resyncDelivery, "resyncDelivery");
            if (deltaFlushMs < 0L || fullCalibrationMs < 0L) {
                throw new IllegalArgumentException("roster cadence values cannot be negative");
            }
            if (deliveryMode == DeliveryMode.FULL_AND_DELTA && externalVersionSupplier != null) {
                throw new IllegalArgumentException("delta mode requires coordinator-owned versions");
            }
        }

        public static Settings deltaEnabled(long deltaFlushMs, long fullCalibrationMs) {
            return new Settings(
                    DeliveryMode.FULL_AND_DELTA,
                    ResyncDelivery.ALL_LIVE_RECIPIENTS,
                    deltaFlushMs,
                    fullCalibrationMs,
                    null);
        }

        public static Settings fullSnapshotOnly(
                ResyncDelivery resyncDelivery,
                IntSupplier externalVersionSupplier
        ) {
            return new Settings(
                    DeliveryMode.FULL_SNAPSHOT_ONLY,
                    resyncDelivery,
                    0L,
                    0L,
                    Objects.requireNonNull(externalVersionSupplier, "externalVersionSupplier"));
        }
    }

    private final Source<R> source;
    private final Publisher<R> publisher;
    private final Settings settings;
    private final LongSupplier clock;

    private final Set<UUID> knownRecipients = new HashSet<>();
    private final Set<UUID> bootstrapRecipients = new HashSet<>();
    private Map<String, List<PlayerInfo>> pendingRosterSnapshot = new HashMap<>();
    private Map<UUID, PlayerRosterState> pendingRosterByPlayer = new HashMap<>();
    private Map<UUID, PlayerRosterState> lastSentRosterByPlayer = new HashMap<>();
    private final Map<UUID, RoomRosterDelta> pendingDeltaUpdates = new LinkedHashMap<>();
    private int rosterVersion;
    private boolean fullSnapshotPendingForAll = true;
    private long lastDeltaFlushAtMs;
    private long lastFullSnapshotAtMs;

    public RoomRosterSyncCoordinator(
            Source<R> source,
            Publisher<R> publisher,
            Settings settings,
            LongSupplier clock
    ) {
        this.source = Objects.requireNonNull(source, "source");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized void synchronize(boolean fromTick) {
        Map<UUID, R> recipients = currentRecipients();
        captureRosterChanges(recipients);
        if (settings.deliveryMode() == DeliveryMode.FULL_SNAPSHOT_ONLY) {
            publishFullSnapshot(recipients.values(), false, clock.getAsLong());
            return;
        }
        flushRosterSync(recipients, fromTick);
    }

    public synchronized void broadcastFullSnapshot() {
        Map<UUID, R> recipients = currentRecipients();
        pendingRosterSnapshot = deepCopyTeamPlayers(source.rosterSnapshot());
        pendingRosterByPlayer = flatten(pendingRosterSnapshot);
        publishFullSnapshot(recipients.values(), false, clock.getAsLong());
    }

    public synchronized void requestResync(R requester) {
        if (requester == null || !source.canRequestResync(requester)) {
            return;
        }
        if (settings.deliveryMode() == DeliveryMode.FULL_SNAPSHOT_ONLY) {
            pendingRosterSnapshot = deepCopyTeamPlayers(source.rosterSnapshot());
            pendingRosterByPlayer = flatten(pendingRosterSnapshot);
            Collection<R> recipients = settings.resyncDelivery() == ResyncDelivery.REQUESTER_ONLY
                    ? List.of(requester)
                    : currentRecipients().values();
            publishFullSnapshot(recipients, false, clock.getAsLong());
            return;
        }

        Map<UUID, R> recipients = currentRecipients();
        fullSnapshotPendingForAll = true;
        pendingDeltaUpdates.clear();
        flushRosterSync(recipients, true);
    }

    public synchronized void requestPreview(R requester) {
        if (requester == null) {
            return;
        }
        Map<String, List<PlayerInfo>> snapshot = deepCopyTeamPlayers(source.rosterSnapshot());
        publisher.publishPreview(source.roomKey(), effectiveVersion(), snapshot, requester);
    }

    public synchronized int version() {
        return effectiveVersion();
    }

    private void captureRosterChanges(Map<UUID, R> recipients) {
        Map<String, List<PlayerInfo>> latestSnapshot = deepCopyTeamPlayers(source.rosterSnapshot());
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
            PlayerInfo current = entry.getValue().player();
            int changedMask = buildChangedMask(previousState.player(), current);
            if (changedMask != 0) {
                pendingDeltaUpdates.put(playerId, new RoomRosterDelta(
                        playerId,
                        entry.getValue().teamName(),
                        changedMask,
                        current));
            }
        }
    }

    private void flushRosterSync(Map<UUID, R> recipients, boolean fromTick) {
        if (recipients.isEmpty()) {
            knownRecipients.clear();
            bootstrapRecipients.clear();
            return;
        }

        long now = clock.getAsLong();
        if (fullSnapshotPendingForAll) {
            publishFullSnapshot(recipients.values(), true, now);
            return;
        }
        if (!pendingDeltaUpdates.isEmpty()) {
            boolean calibrationDue = lastFullSnapshotAtMs <= 0L
                    || now - lastFullSnapshotAtMs >= settings.fullCalibrationMs();
            if (calibrationDue) {
                publishFullSnapshot(recipients.values(), true, now);
                return;
            }
            if (fromTick || now - lastDeltaFlushAtMs >= settings.deltaFlushMs()) {
                publishDelta(recipients.values(), now);
                return;
            }
        }
        if (!bootstrapRecipients.isEmpty()) {
            publishBootstrapSnapshots(recipients);
        }
        if (pendingDeltaUpdates.isEmpty()
                && rosterVersion > 0
                && now - lastFullSnapshotAtMs >= settings.fullCalibrationMs()) {
            publishFullSnapshot(recipients.values(), false, now);
        }
    }

    private void publishBootstrapSnapshots(Map<UUID, R> recipients) {
        int effectiveVersion = effectiveVersion();
        for (UUID recipientId : new HashSet<>(bootstrapRecipients)) {
            R recipient = recipients.get(recipientId);
            if (recipient != null) {
                publisher.publishFull(
                        source.roomKey(),
                        effectiveVersion,
                        pendingRosterSnapshot,
                        List.of(recipient));
            }
            bootstrapRecipients.remove(recipientId);
        }
    }

    private void publishFullSnapshot(Collection<R> recipients, boolean advanceVersion, long now) {
        int version = advanceVersion ? advanceVersion() : effectiveVersion();
        publisher.publishFull(source.roomKey(), version, pendingRosterSnapshot, recipients);
        if (settings.deliveryMode() == DeliveryMode.FULL_AND_DELTA) {
            rosterVersion = version;
            lastSentRosterByPlayer = new HashMap<>(pendingRosterByPlayer);
            pendingDeltaUpdates.clear();
            bootstrapRecipients.clear();
            fullSnapshotPendingForAll = false;
            lastFullSnapshotAtMs = now;
            lastDeltaFlushAtMs = now;
        }
    }

    private void publishDelta(Collection<R> recipients, long now) {
        int nextVersion = advanceVersion();
        publisher.publishDelta(
                source.roomKey(),
                nextVersion,
                new ArrayList<>(pendingDeltaUpdates.values()),
                recipients);
        rosterVersion = nextVersion;
        lastSentRosterByPlayer = new HashMap<>(pendingRosterByPlayer);
        pendingDeltaUpdates.clear();
        lastDeltaFlushAtMs = now;
    }

    private Map<UUID, R> currentRecipients() {
        Map<UUID, R> recipients = new LinkedHashMap<>();
        Collection<R> liveRecipients = source.liveRecipients();
        if (liveRecipients == null) {
            return recipients;
        }
        for (R recipient : liveRecipients) {
            if (recipient == null) {
                continue;
            }
            UUID recipientId = source.recipientId(recipient);
            if (recipientId != null) {
                recipients.put(recipientId, recipient);
            }
        }
        return recipients;
    }

    private boolean hasStructuralChange(Map<UUID, PlayerRosterState> latestByPlayer) {
        if (latestByPlayer.size() != lastSentRosterByPlayer.size()) {
            return true;
        }
        for (Map.Entry<UUID, PlayerRosterState> entry : latestByPlayer.entrySet()) {
            PlayerRosterState previous = lastSentRosterByPlayer.get(entry.getKey());
            if (previous == null || !previous.teamName().equals(entry.getValue().teamName())) {
                return true;
            }
        }
        return false;
    }

    private static int buildChangedMask(PlayerInfo previous, PlayerInfo current) {
        int mask = 0;
        if (previous.isReady() != current.isReady()) {
            mask |= RoomRosterDelta.CHANGE_READY;
        }
        if (previous.kills() != current.kills() || previous.deaths() != current.deaths()) {
            mask |= RoomRosterDelta.CHANGE_STATS;
        }
        if (previous.isAlive() != current.isAlive()) {
            mask |= RoomRosterDelta.CHANGE_LIFE;
        }
        if (previous.isInvincible() != current.isInvincible()) {
            mask |= RoomRosterDelta.CHANGE_INVINCIBLE;
        }
        if (pingBucket(previous.pingMs()) != pingBucket(current.pingMs())) {
            mask |= RoomRosterDelta.CHANGE_PING_BUCKET;
        }
        if (previous.maxKillStreak() != current.maxKillStreak()) {
            mask |= RoomRosterDelta.CHANGE_STREAK;
        }
        return mask;
    }

    private static int pingBucket(int pingMs) {
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

    private static Map<String, List<PlayerInfo>> deepCopyTeamPlayers(Map<String, List<PlayerInfo>> source) {
        Map<String, List<PlayerInfo>> copied = new HashMap<>();
        if (source == null) {
            return copied;
        }
        for (Map.Entry<String, List<PlayerInfo>> entry : source.entrySet()) {
            copied.put(entry.getKey(), new ArrayList<>(entry.getValue() == null ? List.of() : entry.getValue()));
        }
        return copied;
    }

    private static Map<UUID, PlayerRosterState> flatten(Map<String, List<PlayerInfo>> teamPlayers) {
        Map<UUID, PlayerRosterState> flattened = new HashMap<>();
        for (Map.Entry<String, List<PlayerInfo>> entry : teamPlayers.entrySet()) {
            for (PlayerInfo player : entry.getValue()) {
                flattened.put(player.uuid(), new PlayerRosterState(entry.getKey(), player));
            }
        }
        return flattened;
    }

    private int effectiveVersion() {
        if (settings.externalVersionSupplier() != null) {
            return Math.max(1, settings.externalVersionSupplier().getAsInt());
        }
        return Math.max(1, rosterVersion);
    }

    private int advanceVersion() {
        rosterVersion = Math.max(1, rosterVersion + 1);
        return rosterVersion;
    }

    private record PlayerRosterState(String teamName, PlayerInfo player) {
    }
}
