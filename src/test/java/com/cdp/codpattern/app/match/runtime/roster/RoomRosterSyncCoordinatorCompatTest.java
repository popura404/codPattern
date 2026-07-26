package com.cdp.codpattern.app.match.runtime.roster;

import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import com.cdp.codpattern.network.match.RoomRosterDelta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class RoomRosterSyncCoordinatorCompatTest {
    private static final UUID PLAYER = new UUID(0L, 1L);
    private static final UUID SECOND = new UUID(0L, 2L);

    private RoomRosterSyncCoordinatorCompatTest() {
    }

    public static void main(String[] args) {
        deltaModePreservesVersionBootstrapAndCadence();
        fullOnlyModePreservesRecipientPolicySeparation();
        System.out.println("PASS room roster sync coordinator compat");
    }

    private static void deltaModePreservesVersionBootstrapAndCadence() {
        MutableSource source = new MutableSource();
        source.recipients.add("one");
        source.authorized.add("one");
        source.ids.put("one", PLAYER);
        source.snapshot.put("kortac", List.of(player(PLAYER, false, 149)));
        RecordingPublisher publisher = new RecordingPublisher();
        AtomicLong clock = new AtomicLong(1000L);
        RoomRosterSyncCoordinator<String> coordinator = new RoomRosterSyncCoordinator<>(
                source,
                publisher,
                RoomRosterSyncCoordinator.Settings.deltaEnabled(150L, 7000L),
                clock::get);

        coordinator.synchronize(false);
        require(publisher.last().kind.equals("full") && publisher.last().version == 1,
                "first synchronization should publish full version one");

        source.snapshot.put("kortac", List.of(player(PLAYER, true, 150)));
        clock.set(1100L);
        int eventCount = publisher.events.size();
        coordinator.synchronize(false);
        require(publisher.events.size() == eventCount,
                "non-tick synchronization should retain a delta until the 150 ms threshold");
        coordinator.synchronize(true);
        Event delta = publisher.last();
        require(delta.kind.equals("delta") && delta.version == 2,
                "map-tick synchronization should flush a pending delta immediately");
        int expectedMask = RoomRosterDelta.CHANGE_READY | RoomRosterDelta.CHANGE_PING_BUCKET;
        require(delta.updates.get(0).changedMask() == expectedMask,
                "delta should retain ready and ping-bucket mask bits");

        source.recipients.add("two");
        source.authorized.add("two");
        source.ids.put("two", SECOND);
        coordinator.synchronize(true);
        Event bootstrap = publisher.last();
        require(bootstrap.kind.equals("full") && bootstrap.version == 2
                        && bootstrap.recipients.equals(List.of("two")),
                "new recipient should receive a requester-only bootstrap at the current version");

        clock.set(8000L);
        coordinator.synchronize(true);
        Event calibration = publisher.last();
        require(calibration.kind.equals("full") && calibration.version == 2
                        && Set.copyOf(calibration.recipients).equals(Set.of("one", "two")),
                "7000 ms calibration should publish a full snapshot without advancing version");

        source.snapshot.clear();
        source.snapshot.put("specgru", List.of(player(PLAYER, true, 150)));
        clock.set(8100L);
        coordinator.synchronize(true);
        require(publisher.last().kind.equals("full") && publisher.last().version == 3,
                "structural team change should publish a full snapshot and advance version");

        coordinator.requestResync("one");
        require(publisher.last().kind.equals("full") && publisher.last().version == 4
                        && Set.copyOf(publisher.last().recipients).equals(Set.of("one", "two")),
                "authorized TDM-style resync should force a full snapshot to all live recipients");
    }

    private static void fullOnlyModePreservesRecipientPolicySeparation() {
        MutableSource source = new MutableSource();
        source.recipients.add("survivor");
        source.authorized.add("survivor");
        source.ids.put("survivor", PLAYER);
        source.ids.put("outsider", SECOND);
        source.snapshot.put("survivors", List.of(player(PLAYER, false, 50)));
        RecordingPublisher publisher = new RecordingPublisher();
        AtomicInteger externalVersion = new AtomicInteger(7);
        RoomRosterSyncCoordinator<String> coordinator = new RoomRosterSyncCoordinator<>(
                source,
                publisher,
                RoomRosterSyncCoordinator.Settings.fullSnapshotOnly(
                        RoomRosterSyncCoordinator.ResyncDelivery.REQUESTER_ONLY,
                        externalVersion::get),
                () -> 1L);

        coordinator.broadcastFullSnapshot();
        require(publisher.last().kind.equals("full") && publisher.last().version == 7
                        && publisher.last().recipients.equals(List.of("survivor")),
                "full-only broadcast should target online recipients with the existing external version");

        int eventCount = publisher.events.size();
        coordinator.requestResync("outsider");
        require(publisher.events.size() == eventCount,
                "non-member requester should remain unauthorized for live roster resync");
        coordinator.requestPreview("outsider");
        require(publisher.last().kind.equals("preview")
                        && publisher.last().recipients.equals(List.of("outsider")),
                "room preview should remain requester-only without the live-membership gate");
        coordinator.requestResync("survivor");
        require(publisher.last().kind.equals("full")
                        && publisher.last().recipients.equals(List.of("survivor")),
                "authorized full-only resync should reply only to its requester");
        require(publisher.events.stream().noneMatch(event -> event.kind.equals("delta")),
                "full-only mode must never publish a roster delta");
    }

    private static PlayerInfo player(UUID id, boolean ready, int ping) {
        return new PlayerInfo(id, "alpha", ready, 0, 0, 0, true, false, ping);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class MutableSource implements RoomRosterSyncCoordinator.Source<String> {
        private final Map<String, List<PlayerInfo>> snapshot = new LinkedHashMap<>();
        private final List<String> recipients = new ArrayList<>();
        private final Set<String> authorized = new LinkedHashSet<>();
        private final Map<String, UUID> ids = new LinkedHashMap<>();

        @Override
        public String roomKey() {
            return "mode|map";
        }

        @Override
        public Map<String, List<PlayerInfo>> rosterSnapshot() {
            return snapshot;
        }

        @Override
        public Collection<String> liveRecipients() {
            return recipients;
        }

        @Override
        public UUID recipientId(String recipient) {
            return ids.get(recipient);
        }

        @Override
        public boolean canRequestResync(String requester) {
            return authorized.contains(requester);
        }
    }

    private static final class RecordingPublisher implements RoomRosterSyncCoordinator.Publisher<String> {
        private final List<Event> events = new ArrayList<>();

        @Override
        public void publishFull(
                String roomKey,
                int version,
                Map<String, List<PlayerInfo>> snapshot,
                Collection<String> recipients
        ) {
            events.add(new Event("full", version, List.copyOf(recipients), List.of()));
        }

        @Override
        public void publishDelta(
                String roomKey,
                int version,
                List<RoomRosterDelta> updates,
                Collection<String> recipients
        ) {
            events.add(new Event("delta", version, List.copyOf(recipients), List.copyOf(updates)));
        }

        @Override
        public void publishPreview(
                String roomKey,
                int version,
                Map<String, List<PlayerInfo>> snapshot,
                String requester
        ) {
            events.add(new Event("preview", version, List.of(requester), List.of()));
        }

        private Event last() {
            return events.get(events.size() - 1);
        }
    }

    private record Event(String kind, int version, List<String> recipients, List<RoomRosterDelta> updates) {
    }
}
