package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.port.ReadyStatePort;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal ready-state store for zombies waiting rooms.
 */
public final class ZombiesReadyService implements ReadyStatePort {
    public interface Hooks {
        boolean isWaitingPhase();

        default void markRoomListDirty() {
        }
    }

    private static final Hooks ALWAYS_WAITING_HOOKS = () -> true;

    private final Hooks hooks;
    private final Set<UUID> readyPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> knownPlayers = ConcurrentHashMap.newKeySet();

    public ZombiesReadyService() {
        this(ALWAYS_WAITING_HOOKS);
    }

    public ZombiesReadyService(Hooks hooks) {
        this.hooks = Objects.requireNonNull(hooks, "hooks");
    }

    @Override
    public void initializeReadyState(ServerPlayer player) {
        if (player != null) {
            initializeReadyState(player.getUUID());
        }
    }

    public void initializeReadyState(UUID playerId) {
        if (playerId == null) {
            return;
        }
        knownPlayers.add(playerId);
        readyPlayers.remove(playerId);
        hooks.markRoomListDirty();
    }

    @Override
    public boolean setPlayerReady(ServerPlayer player, boolean ready) {
        return player != null && setPlayerReady(player.getUUID(), ready);
    }

    public boolean setPlayerReady(UUID playerId, boolean ready) {
        if (playerId == null || !hooks.isWaitingPhase()) {
            return false;
        }
        knownPlayers.add(playerId);
        boolean changed = ready ? readyPlayers.add(playerId) : readyPlayers.remove(playerId);
        if (changed) {
            hooks.markRoomListDirty();
        }
        return true;
    }

    public boolean isPlayerReady(UUID playerId) {
        return playerId != null && readyPlayers.contains(playerId);
    }

    public boolean areAllReady(Collection<UUID> playerIds) {
        if (playerIds == null || playerIds.isEmpty()) {
            return false;
        }
        for (UUID playerId : playerIds) {
            if (!isPlayerReady(playerId)) {
                return false;
            }
        }
        return true;
    }

    public void removePlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        boolean changed = knownPlayers.remove(playerId);
        changed |= readyPlayers.remove(playerId);
        if (changed) {
            hooks.markRoomListDirty();
        }
    }

    public Set<UUID> readyPlayers() {
        return Set.copyOf(readyPlayers);
    }

    public Set<UUID> knownPlayers() {
        return Set.copyOf(knownPlayers);
    }

    public Set<UUID> snapshotReadyPlayers(Collection<UUID> playerIds) {
        Set<UUID> snapshot = new LinkedHashSet<>();
        if (playerIds == null) {
            return snapshot;
        }
        for (UUID playerId : playerIds) {
            if (isPlayerReady(playerId)) {
                snapshot.add(playerId);
            }
        }
        return Set.copyOf(snapshot);
    }

    public void clear() {
        knownPlayers.clear();
        readyPlayers.clear();
        hooks.markRoomListDirty();
    }
}
