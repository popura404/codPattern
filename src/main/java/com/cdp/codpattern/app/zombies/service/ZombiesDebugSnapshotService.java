package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.ModePlayerValue;
import com.cdp.codpattern.app.match.model.ModeRuntimeStateSnapshot;
import com.cdp.codpattern.app.match.model.RoomSummaryMetric;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;
import com.cdp.codpattern.app.zombies.sync.ZombiesRuntimeStateKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ZombiesDebugSnapshotService {
    public ZombiesDebugSnapshot create(
            ModeRuntimeStateSnapshot runtimeState,
            List<ModeEntityOwnershipRegistry.Entry> entityEntries
    ) {
        ModeRuntimeStateSnapshot state = runtimeState == null
                ? new ModeRuntimeStateSnapshot("", "", 0, List.of(), Map.of(), List.of(), 0L)
                : runtimeState;
        List<ModeEntityOwnershipRegistry.Entry> entries = entityEntries == null ? List.of() : List.copyOf(entityEntries);
        return new ZombiesDebugSnapshot(
                state.roomKey(),
                state.phaseKey(),
                state.revision(),
                state.remainingTimeTicks(),
                metric(state, ZombiesRuntimeStateKeys.METRIC_WAVE),
                metric(state, ZombiesRuntimeStateKeys.METRIC_ZOMBIES_LEFT),
                metric(state, ZombiesRuntimeStateKeys.METRIC_ACTIVE_ZOMBIES),
                metric(state, ZombiesRuntimeStateKeys.METRIC_ALIVE_PLAYERS),
                metric(state, ZombiesRuntimeStateKeys.METRIC_MAX_PLAYERS),
                entries.size(),
                state.playerValues().size(),
                survivorStateCount(state.playerValues()));
    }

    private static int metric(ModeRuntimeStateSnapshot state, String key) {
        for (RoomSummaryMetric metric : state.metrics()) {
            if (key.equals(metric.key())) {
                return metric.value();
            }
        }
        return 0;
    }

    private static int survivorStateCount(Map<String, ModePlayerValue> playerValues) {
        if (playerValues == null || playerValues.isEmpty()) {
            return 0;
        }
        List<String> survivorIds = new ArrayList<>();
        for (String key : playerValues.keySet()) {
            if (!key.startsWith("survivor.")) {
                continue;
            }
            int start = "survivor.".length();
            int end = key.indexOf('.', start);
            if (end > start) {
                String playerId = key.substring(start, end);
                if (!survivorIds.contains(playerId)) {
                    survivorIds.add(playerId);
                }
            }
        }
        return survivorIds.size();
    }

    public record ZombiesDebugSnapshot(
            String roomKey,
            String phaseKey,
            long revision,
            int remainingTimeTicks,
            int wave,
            int zombiesLeft,
            int activeZombies,
            int alivePlayers,
            int maxPlayers,
            int entityRegistryEntries,
            int playerValueCount,
            int survivorStateCount
    ) {
        public List<String> lines() {
            return List.of(
                    "Zombies debug " + roomKey,
                    "phase=" + phaseKey + ", revision=" + revision + ", remainingTicks=" + remainingTimeTicks,
                    "wave=" + wave + ", zombiesLeft=" + zombiesLeft + ", activeZombies=" + activeZombies,
                    "alivePlayers=" + alivePlayers + "/" + maxPlayers + ", survivorStates=" + survivorStateCount,
                    "entityRegistryEntries=" + entityRegistryEntries + ", playerValues=" + playerValueCount
            );
        }
    }
}
