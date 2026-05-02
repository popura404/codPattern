package com.cdp.codpattern.client;

import com.cdp.codpattern.app.match.model.ModeObjectState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

public final class ClientModeObjectState {
    private static final Map<String, Map<String, ModeObjectState>> STATES_BY_ROOM = new HashMap<>();
    private static final Map<String, Long> REVISIONS_BY_ROOM = new HashMap<>();

    private ClientModeObjectState() {
    }

    public static void replaceRoomStates(String roomKey, List<ModeObjectState> states) {
        replaceRoomStates(roomKey, states, 0L);
    }

    public static void replaceRoomStates(String roomKey, List<ModeObjectState> states, long revision) {
        if (roomKey == null || roomKey.isBlank()) {
            return;
        }
        long normalizedRevision = Math.max(0L, revision);
        long previousRevision = REVISIONS_BY_ROOM.getOrDefault(roomKey, -1L);
        if (normalizedRevision < previousRevision) {
            return;
        }
        Map<String, ModeObjectState> next = new HashMap<>();
        if (states != null) {
            for (ModeObjectState state : states) {
                if (state != null && !state.objectKey().isBlank()) {
                    next.put(state.objectKey(), state);
                }
            }
        }
        STATES_BY_ROOM.put(roomKey, next);
        REVISIONS_BY_ROOM.put(roomKey, normalizedRevision);
    }

    public static Map<String, ModeObjectState> roomStates(String roomKey) {
        if (roomKey == null || roomKey.isBlank()) {
            return Map.of();
        }
        return Map.copyOf(STATES_BY_ROOM.getOrDefault(roomKey, Map.of()));
    }

    public static OptionalLong revision(String roomKey) {
        if (roomKey == null || roomKey.isBlank()) {
            return OptionalLong.empty();
        }
        Long revision = REVISIONS_BY_ROOM.get(roomKey);
        return revision == null ? OptionalLong.empty() : OptionalLong.of(revision);
    }

    public static void clear(String roomKey) {
        if (roomKey != null) {
            STATES_BY_ROOM.remove(roomKey);
            REVISIONS_BY_ROOM.remove(roomKey);
        }
    }

    public static void clearAll() {
        STATES_BY_ROOM.clear();
        REVISIONS_BY_ROOM.clear();
    }
}
