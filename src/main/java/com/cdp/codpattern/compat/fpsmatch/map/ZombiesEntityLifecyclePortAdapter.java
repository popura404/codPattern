package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.match.model.EntityLifecycleContext;
import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.port.ModeEntityLifecyclePort;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;
import com.cdp.codpattern.app.zombies.service.ZombiesCleanupService;
import net.minecraft.world.entity.Entity;

import java.util.Objects;

public class ZombiesEntityLifecyclePortAdapter implements ModeEntityLifecyclePort {
    private final RoomId roomId;
    private final String modeDisplayNameKey;
    private final ModeEntityOwnershipRegistry ownershipRegistry;
    private final ZombiesCleanupService cleanupService;
    private final ZombiesCleanupService.LevelResolver levelResolver;
    private final EntityRemovalHook removalHook;

    public ZombiesEntityLifecyclePortAdapter(
            RoomId roomId,
            String modeDisplayNameKey,
            ModeEntityOwnershipRegistry ownershipRegistry,
            ZombiesCleanupService cleanupService,
            ZombiesCleanupService.LevelResolver levelResolver,
            EntityRemovalHook removalHook
    ) {
        this.roomId = Objects.requireNonNull(roomId, "roomId");
        this.modeDisplayNameKey = modeDisplayNameKey == null || modeDisplayNameKey.isBlank()
                ? GameModeRegistry.getOrDefault(roomId.gameType()).displayNameKey()
                : modeDisplayNameKey;
        this.ownershipRegistry = ownershipRegistry == null ? ModeEntityOwnershipRegistry.instance() : ownershipRegistry;
        this.cleanupService = Objects.requireNonNull(cleanupService, "cleanupService");
        this.levelResolver = levelResolver;
        this.removalHook = removalHook == null ? entity -> { } : removalHook;
    }

    @Override
    public RoomId roomId() {
        return roomId;
    }

    @Override
    public String gameType() {
        return GameModeRegistry.canonicalize(roomId.gameType());
    }

    @Override
    public String mapName() {
        return roomId.mapName();
    }

    @Override
    public String modeDisplayNameKey() {
        return modeDisplayNameKey;
    }

    @Override
    public void onRoomEntityRemoved(Entity entity, EntityLifecycleContext context) {
        if (entity == null) {
            return;
        }
        ownershipRegistry.unregister(entity);
        removalHook.onEntityRemoved(entity);
    }

    @Override
    public void onRoomEntitiesCleared(RoomId roomId) {
        if (roomId != null && sameRoom(roomId)) {
            cleanupService.cleanupEntities(roomId, levelResolver);
        }
    }

    private boolean sameRoom(RoomId other) {
        return GameModeRegistry.canonicalize(other.gameType()).equals(gameType()) && other.mapName().equals(mapName());
    }

    @FunctionalInterface
    public interface EntityRemovalHook {
        void onEntityRemoved(Entity entity);
    }
}
