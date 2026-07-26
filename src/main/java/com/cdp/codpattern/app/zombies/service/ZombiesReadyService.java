package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.runtime.ready.DefaultReadyStateService;
import com.cdp.codpattern.app.match.port.ReadyStatePort;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
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
    private final DefaultReadyStateService delegate;

    public ZombiesReadyService() {
        this(ALWAYS_WAITING_HOOKS);
    }

    public ZombiesReadyService(Hooks hooks) {
        this.hooks = Objects.requireNonNull(hooks, "hooks");
        this.delegate = new DefaultReadyStateService(
                new ConcurrentHashMap<>(),
                new DefaultReadyStateService.Policy() {
                    @Override
                    public boolean canMutate(UUID playerId) {
                        return ZombiesReadyService.this.hooks.isWaitingPhase();
                    }

                    @Override
                    public void onInitialized(UUID playerId, DefaultReadyStateService.OperationResult result) {
                        ZombiesReadyService.this.hooks.markRoomListDirty();
                    }

                    @Override
                    public void onMutation(
                            UUID playerId,
                            boolean ready,
                            DefaultReadyStateService.OperationResult result
                    ) {
                        if (result.changed()) {
                            ZombiesReadyService.this.hooks.markRoomListDirty();
                        }
                    }

                    @Override
                    public void onRemoved(UUID playerId, DefaultReadyStateService.OperationResult result) {
                        if (result.changed()) {
                            ZombiesReadyService.this.hooks.markRoomListDirty();
                        }
                    }

                    @Override
                    public void onCleared(DefaultReadyStateService.OperationResult result) {
                        ZombiesReadyService.this.hooks.markRoomListDirty();
                    }
                });
    }

    @Override
    public void initializeReadyState(ServerPlayer player) {
        if (player != null) {
            initializeReadyState(player.getUUID());
        }
    }

    public void initializeReadyState(UUID playerId) {
        delegate.initialize(playerId);
    }

    @Override
    public boolean setPlayerReady(ServerPlayer player, boolean ready) {
        return player != null && setPlayerReady(player.getUUID(), ready);
    }

    public boolean setPlayerReady(UUID playerId, boolean ready) {
        return delegate.setReady(playerId, ready).accepted();
    }

    public boolean isPlayerReady(UUID playerId) {
        return delegate.isReady(playerId);
    }

    public boolean areAllReady(Collection<UUID> playerIds) {
        return delegate.areAllReady(playerIds);
    }

    public void removePlayer(UUID playerId) {
        delegate.remove(playerId);
    }

    public Set<UUID> readyPlayers() {
        return delegate.readyPlayers();
    }

    public Set<UUID> knownPlayers() {
        return delegate.knownPlayers();
    }

    public Set<UUID> snapshotReadyPlayers(Collection<UUID> playerIds) {
        return delegate.snapshotReadyPlayers(playerIds);
    }

    public void clear() {
        delegate.clear();
    }
}
