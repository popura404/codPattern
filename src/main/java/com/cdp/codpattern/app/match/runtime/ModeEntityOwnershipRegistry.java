package com.cdp.codpattern.app.match.runtime;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.model.RoomId;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ModeEntityOwnershipRegistry {
    private static final String ROOM_KEY_TAG = "codpattern_room_key";
    private static final ModeEntityOwnershipRegistry INSTANCE = new ModeEntityOwnershipRegistry();

    private final ConcurrentMap<UUID, Entry> entriesByEntityId = new ConcurrentHashMap<>();
    private final Set<UUID> suppressedPersistentRestores = ConcurrentHashMap.newKeySet();

    private ModeEntityOwnershipRegistry() {
    }

    public static ModeEntityOwnershipRegistry instance() {
        return INSTANCE;
    }

    public void register(RoomId roomId, Entity entity) {
        if (roomId == null || entity == null) {
            return;
        }
        Entry entry = new Entry(roomId, entity.level().dimension(), entity.getUUID());
        suppressedPersistentRestores.remove(entity.getUUID());
        entriesByEntityId.put(entity.getUUID(), entry);
        entity.getPersistentData().putString(ROOM_KEY_TAG, roomId.encode());
    }

    public Optional<Entry> unregister(Entity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        entity.getPersistentData().remove(ROOM_KEY_TAG);
        suppressedPersistentRestores.remove(entity.getUUID());
        return Optional.ofNullable(entriesByEntityId.remove(entity.getUUID()));
    }

    public Optional<Entry> unregister(UUID entityId) {
        if (entityId == null) {
            return Optional.empty();
        }
        Optional<Entry> removed = Optional.ofNullable(entriesByEntityId.remove(entityId));
        removed.ifPresent(ignored -> suppressedPersistentRestores.add(entityId));
        return removed;
    }

    public Optional<RoomId> roomIdOf(Entity entity) {
        return entryOf(entity).map(Entry::roomId);
    }

    public Optional<Entry> entryOf(Entity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        Entry entry = entriesByEntityId.get(entity.getUUID());
        if (entry != null) {
            return Optional.of(entry);
        }
        if (suppressedPersistentRestores.remove(entity.getUUID())) {
            entity.getPersistentData().remove(ROOM_KEY_TAG);
            return Optional.empty();
        }
        String roomKey = entity.getPersistentData().getString(ROOM_KEY_TAG);
        if (roomKey == null || roomKey.isBlank()) {
            return Optional.empty();
        }
        try {
            RoomId roomId = RoomId.decode(roomKey);
            Entry restored = new Entry(roomId, entity.level().dimension(), entity.getUUID());
            entriesByEntityId.put(entity.getUUID(), restored);
            return Optional.of(restored);
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public Optional<Entry> entryOf(UUID entityId) {
        if (entityId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(entriesByEntityId.get(entityId));
    }

    public List<Entry> entries() {
        return List.copyOf(entriesByEntityId.values());
    }

    public List<Entry> entitiesInRoom(RoomId roomId) {
        if (roomId == null) {
            return List.of();
        }
        return entriesByEntityId.values().stream()
                .filter(entry -> sameRoom(entry.roomId(), roomId))
                .toList();
    }

    public List<Entry> clearRoom(RoomId roomId) {
        if (roomId == null) {
            return List.of();
        }
        List<Entry> removed = entitiesInRoom(roomId);
        for (Entry entry : removed) {
            if (entriesByEntityId.remove(entry.entityId(), entry)) {
                suppressedPersistentRestores.add(entry.entityId());
            }
        }
        return removed;
    }

    public List<Entry> missingEntities(ServerLevel level) {
        if (level == null) {
            return List.of();
        }
        return entriesByEntityId.values().stream()
                .filter(entry -> entry.dimension().equals(level.dimension()))
                .filter(entry -> level.getEntity(entry.entityId()) == null)
                .toList();
    }

    public int clearMissingEntities(ServerLevel level) {
        int removed = 0;
        for (Entry entry : missingEntities(level)) {
            if (entriesByEntityId.remove(entry.entityId(), entry)) {
                removed++;
            }
        }
        return removed;
    }

    private static boolean sameRoom(RoomId left, RoomId right) {
        if (left == null || right == null) {
            return false;
        }
        return left.mapName().equals(right.mapName())
                && GameModeRegistry.canonicalize(left.gameType()).equals(GameModeRegistry.canonicalize(right.gameType()));
    }

    public record Entry(
            RoomId roomId,
            ResourceKey<Level> dimension,
            UUID entityId
    ) {
    }
}
