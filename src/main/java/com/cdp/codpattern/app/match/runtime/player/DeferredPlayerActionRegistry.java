package com.cdp.codpattern.app.match.runtime.player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Player-keyed deferred work consumed once on a later lifecycle event. */
public final class DeferredPlayerActionRegistry<T> {
    private final ConcurrentMap<UUID, T> pending = new ConcurrentHashMap<>();

    public Optional<T> put(UUID playerId, T action) {
        if (playerId == null || action == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(pending.put(playerId, action));
    }

    public Optional<T> peek(UUID playerId) {
        return Optional.ofNullable(playerId == null ? null : pending.get(playerId));
    }

    public Optional<T> consume(UUID playerId) {
        return Optional.ofNullable(playerId == null ? null : pending.remove(playerId));
    }

    public boolean remove(UUID playerId) {
        return playerId != null && pending.remove(playerId) != null;
    }

    public int size() {
        return pending.size();
    }

    public void clear() {
        pending.clear();
    }
}
