package com.cdp.codpattern.client;

import com.cdp.codpattern.app.match.model.ModeRuntimeStateSnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class ClientModeRuntimeState {
    private static final Map<String, ModeRuntimeStateSnapshot> SNAPSHOTS_BY_ROOM = new HashMap<>();

    private ClientModeRuntimeState() {
    }

    public static void update(ModeRuntimeStateSnapshot snapshot) {
        if (snapshot == null || snapshot.roomKey().isBlank()) {
            return;
        }
        ModeRuntimeStateSnapshot previous = SNAPSHOTS_BY_ROOM.get(snapshot.roomKey());
        if (previous != null && snapshot.revision() < previous.revision()) {
            return;
        }
        SNAPSHOTS_BY_ROOM.put(snapshot.roomKey(), snapshot);
    }

    public static Optional<ModeRuntimeStateSnapshot> snapshot(String roomKey) {
        if (roomKey == null || roomKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(SNAPSHOTS_BY_ROOM.get(roomKey));
    }

    public static Map<String, ModeRuntimeStateSnapshot> snapshots() {
        return Map.copyOf(SNAPSHOTS_BY_ROOM);
    }

    public static void clear(String roomKey) {
        if (roomKey != null) {
            SNAPSHOTS_BY_ROOM.remove(roomKey);
        }
    }

    public static void clearAll() {
        SNAPSHOTS_BY_ROOM.clear();
    }
}
