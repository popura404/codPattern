package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Server lifecycle recovery for non-persistent zombies runtime state.
 */
public final class ZombiesCrashRecoveryService {
    private static final String ROOM_KEY_TAG = "codpattern_room_key";
    private static final ZombiesCrashRecoveryService INSTANCE = new ZombiesCrashRecoveryService(
            ModeEntityOwnershipRegistry.instance(),
            ZombiesMapOccupancyService.instance());

    private final ModeEntityOwnershipRegistry ownershipRegistry;
    private final ZombiesMapOccupancyService occupancyService;

    public ZombiesCrashRecoveryService(
            ModeEntityOwnershipRegistry ownershipRegistry,
            ZombiesMapOccupancyService occupancyService
    ) {
        this.ownershipRegistry = ownershipRegistry == null ? ModeEntityOwnershipRegistry.instance() : ownershipRegistry;
        this.occupancyService = occupancyService == null ? ZombiesMapOccupancyService.instance() : occupancyService;
    }

    public static ZombiesCrashRecoveryService instance() {
        return INSTANCE;
    }

    public ServerStartupRecoverySummary recoverServerStartup(MinecraftServer server) {
        ResidualEntityCleanupSummary entitySummary = cleanupResidualTaggedEntities(server);
        occupancyService.clear();
        return new ServerStartupRecoverySummary(entitySummary, true);
    }

    public ServerStoppingRecoverySummary cleanupServerStopping(
            MinecraftServer server,
            Collection<? extends ShutdownRoom> rooms
    ) {
        int roomsReset = 0;
        if (rooms != null) {
            for (ShutdownRoom room : rooms) {
                if (room == null || !room.running()) {
                    continue;
                }
                room.cleanupForServerStopping();
                roomsReset++;
            }
        }
        ResidualEntityCleanupSummary entitySummary = cleanupResidualTaggedEntities(server);
        occupancyService.clear();
        return new ServerStoppingRecoverySummary(roomsReset, entitySummary, true);
    }

    public ResidualEntityCleanupSummary cleanupResidualTaggedEntities(MinecraftServer server) {
        if (server == null) {
            return ResidualEntityCleanupSummary.empty();
        }
        int scannedEntities = 0;
        int taggedEntities = 0;
        int zombiesTaggedEntities = 0;
        int removedEntities = 0;
        int invalidRoomTags = 0;
        Set<RoomId> releasedRooms = new LinkedHashSet<>();

        for (ServerLevel level : server.getAllLevels()) {
            List<Entity> entities = entitiesIn(level);
            for (Entity entity : entities) {
                scannedEntities++;
                if (entity == null || !entity.getPersistentData().contains(ROOM_KEY_TAG)) {
                    continue;
                }
                taggedEntities++;
                String encodedRoom = entity.getPersistentData().getString(ROOM_KEY_TAG);
                Optional<RoomId> roomId = decodeRoomId(encodedRoom);
                if (roomId.isEmpty()) {
                    entity.getPersistentData().remove(ROOM_KEY_TAG);
                    invalidRoomTags++;
                    continue;
                }
                RoomId decodedRoom = roomId.get();
                if (!BuiltInGameModes.isZombies(decodedRoom.gameType())) {
                    continue;
                }
                zombiesTaggedEntities++;
                ownershipRegistry.unregister(entity);
                entity.getPersistentData().remove(ROOM_KEY_TAG);
                entity.remove(Entity.RemovalReason.DISCARDED);
                removedEntities++;
                occupancyService.forceRelease(decodedRoom.gameType(), decodedRoom.mapName());
                releasedRooms.add(decodedRoom);
            }
        }
        return new ResidualEntityCleanupSummary(
                scannedEntities,
                taggedEntities,
                zombiesTaggedEntities,
                removedEntities,
                invalidRoomTags,
                releasedRooms.size());
    }

    public Optional<RoomId> decodeRoomId(String encodedRoom) {
        if (encodedRoom == null || encodedRoom.isBlank()) {
            return Optional.empty();
        }
        try {
            RoomId roomId = RoomId.decode(encodedRoom);
            return Optional.of(roomId);
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private List<Entity> entitiesIn(ServerLevel level) {
        if (level == null) {
            return List.of();
        }
        List<Entity> entities = new ArrayList<>();
        level.getAllEntities().forEach(entities::add);
        return entities;
    }

    public interface ShutdownRoom {
        RoomId roomId();

        boolean running();

        void cleanupForServerStopping();
    }

    public record ServerStartupRecoverySummary(
            ResidualEntityCleanupSummary entities,
            boolean occupancyCleared
    ) {
        public ServerStartupRecoverySummary {
            entities = entities == null ? ResidualEntityCleanupSummary.empty() : entities;
        }
    }

    public record ServerStoppingRecoverySummary(
            int roomsReset,
            ResidualEntityCleanupSummary entities,
            boolean occupancyCleared
    ) {
        public ServerStoppingRecoverySummary {
            roomsReset = Math.max(0, roomsReset);
            entities = entities == null ? ResidualEntityCleanupSummary.empty() : entities;
        }
    }

    public record ResidualEntityCleanupSummary(
            int scannedEntities,
            int taggedEntities,
            int zombiesTaggedEntities,
            int removedEntities,
            int invalidRoomTags,
            int releasedRooms
    ) {
        public ResidualEntityCleanupSummary {
            scannedEntities = Math.max(0, scannedEntities);
            taggedEntities = Math.max(0, taggedEntities);
            zombiesTaggedEntities = Math.max(0, zombiesTaggedEntities);
            removedEntities = Math.max(0, removedEntities);
            invalidRoomTags = Math.max(0, invalidRoomTags);
            releasedRooms = Math.max(0, releasedRooms);
        }

        public static ResidualEntityCleanupSummary empty() {
            return new ResidualEntityCleanupSummary(0, 0, 0, 0, 0, 0);
        }
    }
}
