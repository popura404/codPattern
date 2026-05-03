package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Idempotent cleanup orchestrator for zombies rounds. It only depends on public
 * services and callbacks; map-private reset details stay in integration hooks.
 */
public class ZombiesCleanupService {
    private final ModeEntityOwnershipRegistry ownershipRegistry;
    private final ZombiesMapOccupancyService occupancyService;
    private final Hooks hooks;
    private final List<ZombiesCleanupParticipant> participants;
    private long cleanupRevision;

    public ZombiesCleanupService(
            ModeEntityOwnershipRegistry ownershipRegistry,
            ZombiesMapOccupancyService occupancyService,
            Hooks hooks,
            Collection<ZombiesCleanupParticipant> participants
    ) {
        this.ownershipRegistry = ownershipRegistry == null
                ? ModeEntityOwnershipRegistry.instance()
                : ownershipRegistry;
        this.occupancyService = occupancyService == null
                ? ZombiesMapOccupancyService.instance()
                : occupancyService;
        this.hooks = hooks == null ? Hooks.noop() : hooks;
        this.participants = new ArrayList<>(participants == null ? List.of() : participants);
        this.participants.sort(Comparator.comparingInt(ZombiesCleanupParticipant::order));
    }

    public ZombiesServiceResult<CleanupSummary> cleanup(RoomId roomId, String reason, LevelResolver levelResolver) {
        Objects.requireNonNull(roomId, "roomId");
        long revision = ++cleanupRevision;
        ZombiesCleanupParticipant.ZombiesCleanupContext context =
                new ZombiesCleanupParticipant.ZombiesCleanupContext(roomId, reason, revision);

        hooks.beforeCleanup(context);
        EntityCleanupSummary entitySummary = cleanupEntities(roomId, levelResolver);
        ZombiesServiceResult<Void> participantResult = runParticipants(context);
        if (!participantResult.success()) {
            return ZombiesServiceResult.failure(
                    participantResult.code(),
                    participantResult.params(),
                    participantResult.logMessage());
        }

        hooks.clearObjectRuntime(context);
        hooks.clearPlayerRuntime(context);
        hooks.clearReadyState(context);
        hooks.clearStartVote(context);
        hooks.clearLifecycleRuntime(context);
        hooks.clearHudState(context);
        boolean occupancyReleased = occupancyService.release(roomId);
        hooks.afterOccupancyReleased(context, occupancyReleased);
        hooks.afterCleanup(context);

        return ZombiesServiceResult.success(new CleanupSummary(revision, entitySummary, occupancyReleased));
    }

    public EntityCleanupSummary cleanupEntities(RoomId roomId, LevelResolver levelResolver) {
        Objects.requireNonNull(roomId, "roomId");
        List<ModeEntityOwnershipRegistry.Entry> entries = ownershipRegistry.clearRoom(roomId);
        int removedEntities = 0;
        int missingEntities = 0;
        for (ModeEntityOwnershipRegistry.Entry entry : entries) {
            ServerLevel level = levelResolver == null ? null : levelResolver.level(entry.dimension());
            Entity entity = level == null ? null : level.getEntity(entry.entityId());
            if (entity == null) {
                missingEntities++;
                continue;
            }
            hooks.onEntityCleanup(entity);
            entity.getPersistentData().remove("codpattern_room_key");
            entity.remove(Entity.RemovalReason.DISCARDED);
            removedEntities++;
        }
        return new EntityCleanupSummary(entries.size(), removedEntities, missingEntities);
    }

    private ZombiesServiceResult<Void> runParticipants(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
        for (ZombiesCleanupParticipant participant : participants) {
            if (participant == null) {
                continue;
            }
            ZombiesServiceResult<Void> result = participant.cleanup(context);
            if (result != null && !result.success()) {
                return result;
            }
        }
        return ZombiesServiceResult.ok();
    }

    public interface Hooks {
        default void beforeCleanup(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
        }

        default void clearObjectRuntime(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
        }

        default void clearPlayerRuntime(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
        }

        default void clearReadyState(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
        }

        default void clearStartVote(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
        }

        default void clearLifecycleRuntime(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
        }

        default void clearHudState(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
        }

        default void onEntityCleanup(Entity entity) {
        }

        default void afterOccupancyReleased(ZombiesCleanupParticipant.ZombiesCleanupContext context, boolean released) {
        }

        default void afterCleanup(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
        }

        static Hooks noop() {
            return new Hooks() {
            };
        }
    }

    @FunctionalInterface
    public interface LevelResolver {
        ServerLevel level(net.minecraft.resources.ResourceKey<Level> dimension);
    }

    public record CleanupSummary(
            long cleanupRevision,
            EntityCleanupSummary entities,
            boolean occupancyReleased
    ) {
    }

    public record EntityCleanupSummary(
            int registeredEntries,
            int removedEntities,
            int missingEntities
    ) {
    }
}
