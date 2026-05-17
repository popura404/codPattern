package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.ModePlayerValue;
import com.cdp.codpattern.app.zombies.model.ZombiesConnectionState;
import com.cdp.codpattern.app.zombies.model.ZombiesLifeState;
import com.cdp.codpattern.app.zombies.model.ZombiesPlayerRuntimeState;
import com.cdp.codpattern.app.zombies.sync.ZombiesRuntimeStateKeys;
import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ZombiesPlayerStateService {
    private final Map<UUID, ZombiesPlayerRuntimeState> statesByPlayer = new ConcurrentHashMap<>();

    public ZombiesPlayerRuntimeState getOrCreate(UUID playerId) {
        return statesByPlayer.computeIfAbsent(playerId, ZombiesPlayerRuntimeState::new);
    }

    public Optional<ZombiesPlayerRuntimeState> get(UUID playerId) {
        return Optional.ofNullable(statesByPlayer.get(playerId));
    }

    public List<ZombiesPlayerRuntimeState> states() {
        return statesByPlayer.values().stream()
                .sorted(Comparator.comparing(ZombiesPlayerRuntimeState::playerId))
                .toList();
    }

    public void registerPlayers(Collection<UUID> playerIds) {
        if (playerIds == null) {
            return;
        }
        for (UUID playerId : playerIds) {
            if (playerId != null) {
                getOrCreate(playerId);
            }
        }
    }

    public void remove(UUID playerId) {
        statesByPlayer.remove(playerId);
    }

    public void clear() {
        statesByPlayer.clear();
    }

    public void markAlive(UUID playerId) {
        getOrCreate(playerId).markAlive();
    }

    public void markDeadSpectating(UUID playerId) {
        getOrCreate(playerId).markDeadSpectating();
    }

    public void markOnline(UUID playerId) {
        getOrCreate(playerId).markOnline();
    }

    public void markOffline(UUID playerId, long currentTick) {
        getOrCreate(playerId).markOffline(currentTick);
    }

    public void markLeft(UUID playerId) {
        getOrCreate(playerId).markLeft();
    }

    public void updateLastAliveTargetPos(UUID playerId, BlockPos pos) {
        getOrCreate(playerId).updateLastAliveTargetPos(pos);
    }

    public boolean canInteract(UUID playerId) {
        return get(playerId)
                .map(ZombiesPlayerRuntimeState::canInteract)
                .orElse(false);
    }

    public boolean isAliveForFailureCheck(UUID playerId, long currentTick, long offlineGraceTicks) {
        return get(playerId)
                .map(state -> isAliveForFailureCheck(state, currentTick, offlineGraceTicks))
                .orElse(false);
    }

    public int aliveCount(long currentTick, long offlineGraceTicks) {
        int count = 0;
        for (ZombiesPlayerRuntimeState state : statesByPlayer.values()) {
            if (isAliveForFailureCheck(state, currentTick, offlineGraceTicks)) {
                count++;
            }
        }
        return count;
    }

    public boolean hasAnyAlive(long currentTick, long offlineGraceTicks) {
        return aliveCount(currentTick, offlineGraceTicks) > 0;
    }

    public List<UUID> alivePlayerIds(long currentTick, long offlineGraceTicks) {
        return statesByPlayer.values().stream()
                .filter(state -> isAliveForFailureCheck(state, currentTick, offlineGraceTicks))
                .map(ZombiesPlayerRuntimeState::playerId)
                .sorted()
                .toList();
    }

    public Map<String, ModePlayerValue> playerValues(UUID playerId) {
        return get(playerId)
                .map(ZombiesPlayerRuntimeState::toPlayerValues)
                .orElse(Map.of());
    }

    public Map<String, ModePlayerValue> survivorValues() {
        Map<String, ModePlayerValue> values = new LinkedHashMap<>();
        for (ZombiesPlayerRuntimeState state : states()) {
            String playerId = state.playerId().toString();
            Map<String, ModePlayerValue> playerValues = state.toPlayerValues();
            values.put(ZombiesRuntimeStateKeys.survivorLifeState(playerId),
                    playerValues.get(ZombiesRuntimeStateKeys.PLAYER_LIFE_STATE));
            values.put(ZombiesRuntimeStateKeys.survivorConnectionState(playerId),
                    playerValues.get(ZombiesRuntimeStateKeys.PLAYER_CONNECTION_STATE));
            values.put(ZombiesRuntimeStateKeys.survivorPoints(playerId),
                    playerValues.get(ZombiesRuntimeStateKeys.PLAYER_POINTS));
            values.put(ZombiesRuntimeStateKeys.survivorArmorLevel(playerId),
                    playerValues.get(ZombiesRuntimeStateKeys.PLAYER_ARMOR_LEVEL));
        }
        return values;
    }

    private static boolean isAliveForFailureCheck(
            ZombiesPlayerRuntimeState state,
            long currentTick,
            long offlineGraceTicks
    ) {
        ZombiesLifeState lifeState = state.lifeState();
        ZombiesConnectionState connectionState = state.connectionState();
        if (!lifeState.isAlive() || connectionState.isLeft()) {
            return false;
        }
        if (connectionState.isOnline()) {
            return true;
        }
        return state.offlineSinceTick()
                .stream()
                .anyMatch(offlineSinceTick -> currentTick - offlineSinceTick <= Math.max(0L, offlineGraceTicks));
    }
}
