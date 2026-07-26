package com.cdp.codpattern.app.match.runtime.ready;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Policy-driven ready-state store shared by room modes.
 *
 * <p>An accepted write and a changed stored value are deliberately separate outcomes. Mode
 * facades may publish after every accepted write while mutation listeners only react to actual
 * value changes.</p>
 */
public final class DefaultReadyStateService {
    public interface Policy {
        boolean canMutate(UUID playerId);

        default void onInitialized(UUID playerId, OperationResult result) {
        }

        default void onMutation(UUID playerId, boolean ready, OperationResult result) {
        }

        default void onRemoved(UUID playerId, OperationResult result) {
        }

        default void onCleared(OperationResult result) {
        }
    }

    public record OperationResult(boolean accepted, boolean changed) {
        public static OperationResult rejected() {
            return new OperationResult(false, false);
        }
    }

    private final Map<UUID, Boolean> readyStates;
    private final Policy policy;

    public DefaultReadyStateService(Map<UUID, Boolean> readyStates, Policy policy) {
        this.readyStates = Objects.requireNonNull(readyStates, "readyStates");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public synchronized OperationResult initialize(UUID playerId) {
        if (playerId == null) {
            return OperationResult.rejected();
        }
        boolean changed = readyStates.getOrDefault(playerId, false);
        readyStates.put(playerId, false);
        OperationResult result = new OperationResult(true, changed);
        policy.onInitialized(playerId, result);
        return result;
    }

    public synchronized OperationResult setReady(UUID playerId, boolean ready) {
        if (playerId == null || !policy.canMutate(playerId)) {
            return OperationResult.rejected();
        }
        boolean changed = readyStates.getOrDefault(playerId, false) != ready;
        readyStates.put(playerId, ready);
        OperationResult result = new OperationResult(true, changed);
        policy.onMutation(playerId, ready, result);
        return result;
    }

    public synchronized boolean isReady(UUID playerId) {
        return playerId != null && readyStates.getOrDefault(playerId, false);
    }

    public synchronized boolean areAllReady(Collection<UUID> playerIds) {
        if (playerIds == null || playerIds.isEmpty()) {
            return false;
        }
        for (UUID playerId : playerIds) {
            if (!isReady(playerId)) {
                return false;
            }
        }
        return true;
    }

    public synchronized OperationResult remove(UUID playerId) {
        if (playerId == null) {
            return OperationResult.rejected();
        }
        boolean changed = readyStates.remove(playerId) != null;
        OperationResult result = new OperationResult(true, changed);
        policy.onRemoved(playerId, result);
        return result;
    }

    public synchronized OperationResult clear() {
        boolean changed = !readyStates.isEmpty();
        readyStates.clear();
        OperationResult result = new OperationResult(true, changed);
        policy.onCleared(result);
        return result;
    }

    public synchronized Set<UUID> readyPlayers() {
        Set<UUID> ready = new LinkedHashSet<>();
        readyStates.forEach((playerId, value) -> {
            if (Boolean.TRUE.equals(value)) {
                ready.add(playerId);
            }
        });
        return Set.copyOf(ready);
    }

    public synchronized Set<UUID> knownPlayers() {
        return Set.copyOf(readyStates.keySet());
    }

    public synchronized Set<UUID> snapshotReadyPlayers(Collection<UUID> playerIds) {
        Set<UUID> snapshot = new LinkedHashSet<>();
        if (playerIds != null) {
            for (UUID playerId : playerIds) {
                if (isReady(playerId)) {
                    snapshot.add(playerId);
                }
            }
        }
        return Set.copyOf(snapshot);
    }
}
