package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.zombies.model.ZombiesConnectionState;
import com.cdp.codpattern.app.zombies.model.ZombiesPlayerRuntimeState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ZombiesConnectionStateService {
    public static final int DEFAULT_OFFLINE_GRACE_SECONDS = 120;
    public static final int DEFAULT_TICKS_PER_SECOND = 20;
    public static final long DEFAULT_OFFLINE_GRACE_TICKS = DEFAULT_OFFLINE_GRACE_SECONDS * DEFAULT_TICKS_PER_SECOND;

    private final ZombiesPlayerStateService playerStateService;
    private final long offlineGraceTicks;

    public ZombiesConnectionStateService(ZombiesPlayerStateService playerStateService) {
        this(playerStateService, DEFAULT_OFFLINE_GRACE_TICKS);
    }

    public ZombiesConnectionStateService(ZombiesPlayerStateService playerStateService, long offlineGraceTicks) {
        this.playerStateService = playerStateService;
        this.offlineGraceTicks = Math.max(0L, offlineGraceTicks);
    }

    public long offlineGraceTicks() {
        return offlineGraceTicks;
    }

    public void markOnline(UUID playerId) {
        playerStateService.markOnline(playerId);
    }

    public void markOffline(UUID playerId, long currentTick) {
        playerStateService.markOffline(playerId, currentTick);
    }

    public void markLeft(UUID playerId) {
        playerStateService.markLeft(playerId);
    }

    public boolean isWithinOfflineGrace(UUID playerId, long currentTick) {
        return playerStateService.get(playerId)
                .filter(state -> state.connectionState() == ZombiesConnectionState.OFFLINE)
                .flatMap(state -> state.offlineSinceTick().stream().boxed().findFirst())
                .map(offlineSinceTick -> currentTick - offlineSinceTick <= offlineGraceTicks)
                .orElse(false);
    }

    public List<UUID> applyOfflineGraceTimeouts(long currentTick) {
        List<UUID> timedOut = new ArrayList<>();
        for (ZombiesPlayerRuntimeState state : playerStateService.states()) {
            if (shouldTimeout(state, currentTick)) {
                state.markDeadSpectating();
                timedOut.add(state.playerId());
            }
        }
        return timedOut;
    }

    private boolean shouldTimeout(ZombiesPlayerRuntimeState state, long currentTick) {
        if (state.connectionState() != ZombiesConnectionState.OFFLINE || !state.lifeState().isAlive()) {
            return false;
        }
        return state.offlineSinceTick()
                .stream()
                .anyMatch(offlineSinceTick -> currentTick - offlineSinceTick > offlineGraceTicks);
    }
}
