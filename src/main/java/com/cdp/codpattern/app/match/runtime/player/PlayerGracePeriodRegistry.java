package com.cdp.codpattern.app.match.runtime.player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Mutable tick-based grace periods for temporarily disconnected players. */
public final class PlayerGracePeriodRegistry {
    private final Map<UUID, Integer> remainingTicks = new HashMap<>();

    public void start(UUID playerId, int ticks) {
        if (playerId != null) {
            remainingTicks.put(playerId, Math.max(1, ticks));
        }
    }

    public void clear(UUID playerId) {
        if (playerId != null) {
            remainingTicks.remove(playerId);
        }
    }

    public Map<UUID, Integer> mutableTimers() {
        return remainingTicks;
    }

    public void clearAll() {
        remainingTicks.clear();
    }
}
