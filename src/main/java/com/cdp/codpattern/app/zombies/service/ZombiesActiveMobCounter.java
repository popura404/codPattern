package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

public final class ZombiesActiveMobCounter {
    private static final ZombiesActiveMobCounter INSTANCE = new ZombiesActiveMobCounter();

    private final ConcurrentMap<String, Set<UUID>> activeByRoom = new ConcurrentHashMap<>();

    public static ZombiesActiveMobCounter instance() {
        return INSTANCE;
    }

    public void register(RoomId roomId, UUID entityId) {
        if (roomId == null || entityId == null) {
            return;
        }
        unregister(entityId);
        activeByRoom
                .computeIfAbsent(roomKey(roomId), ignored -> ConcurrentHashMap.newKeySet())
                .add(entityId);
    }

    public boolean unregister(RoomId roomId, UUID entityId) {
        if (entityId == null) {
            return false;
        }
        if (roomId == null) {
            return unregister(entityId);
        }
        return removeFromRoom(roomKey(roomId), entityId);
    }

    public boolean unregister(UUID entityId) {
        if (entityId == null) {
            return false;
        }
        boolean removed = false;
        for (String roomKey : Set.copyOf(activeByRoom.keySet())) {
            removed |= removeFromRoom(roomKey, entityId);
        }
        return removed;
    }

    public int roomCount(RoomId roomId) {
        if (roomId == null) {
            return 0;
        }
        return activeByRoom.getOrDefault(roomKey(roomId), Set.of()).size();
    }

    public int totalCount() {
        return activeByRoom.values().stream()
                .filter(Objects::nonNull)
                .mapToInt(Set::size)
                .sum();
    }

    public void clearRoom(RoomId roomId) {
        if (roomId != null) {
            activeByRoom.remove(roomKey(roomId));
        }
    }

    public void clear() {
        activeByRoom.clear();
    }

    public Map<String, Integer> roomCountsSnapshot() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        activeByRoom.keySet().stream()
                .sorted()
                .forEach(roomKey -> counts.put(roomKey, activeByRoom.getOrDefault(roomKey, Set.of()).size()));
        return Map.copyOf(counts);
    }

    public ReconcileSummary reconcile(
            Collection<ModeEntityOwnershipRegistry.Entry> entries,
            Function<ResourceKey<Level>, ServerLevel> levelResolver
    ) {
        ConcurrentMap<String, Set<UUID>> rebuilt = new ConcurrentHashMap<>();
        Collection<ModeEntityOwnershipRegistry.Entry> safeEntries = entries == null ? Set.of() : entries;
        int retainedEntries = 0;
        int missingEntities = 0;
        int skippedEntries = 0;

        for (ModeEntityOwnershipRegistry.Entry entry : safeEntries) {
            if (entry == null || entry.roomId() == null || entry.entityId() == null
                    || !BuiltInGameModes.isZombies(entry.roomId().gameType())) {
                skippedEntries++;
                continue;
            }
            ServerLevel level = levelResolver == null ? null : levelResolver.apply(entry.dimension());
            if (levelResolver != null && (level == null || level.getEntity(entry.entityId()) == null)) {
                missingEntities++;
                continue;
            }
            rebuilt
                    .computeIfAbsent(roomKey(entry.roomId()), ignored -> ConcurrentHashMap.newKeySet())
                    .add(entry.entityId());
            retainedEntries++;
        }

        activeByRoom.clear();
        activeByRoom.putAll(rebuilt);
        return new ReconcileSummary(retainedEntries, missingEntities, skippedEntries, totalCount());
    }

    private boolean removeFromRoom(String roomKey, UUID entityId) {
        Set<UUID> entityIds = activeByRoom.get(roomKey);
        if (entityIds == null) {
            return false;
        }
        boolean removed = entityIds.remove(entityId);
        if (entityIds.isEmpty()) {
            activeByRoom.remove(roomKey, entityIds);
        }
        return removed;
    }

    private static String roomKey(RoomId roomId) {
        return GameModeRegistry.canonicalize(roomId.gameType()) + "|" + roomId.mapName();
    }

    public record ReconcileSummary(
            int retainedEntries,
            int missingEntities,
            int skippedEntries,
            int totalActive
    ) {
    }
}
