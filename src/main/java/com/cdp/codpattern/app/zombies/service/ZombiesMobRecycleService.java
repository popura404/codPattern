package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;
import com.cdp.codpattern.app.zombies.runtime.ZombiesWaveRuntimeState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class ZombiesMobRecycleService {
    static final int SCAN_INTERVAL_TICKS = 80;
    static final int NO_TARGET_RECYCLE_TICKS = 20 * 20;
    static final int STUCK_RECYCLE_TICKS = 16 * 20;
    static final double MIN_MOVED_DISTANCE = 0.5D;
    static final double STUCK_MIN_TARGET_DISTANCE = 8.0D;
    static final int MAX_REQUEUE_RECYCLES_PER_ENTITY = 2;

    private final ModeEntityOwnershipRegistry ownershipRegistry;
    private final ZombiesMobLifecycleService lifecycleService;
    private final Supplier<List<ServerPlayer>> targetSupplier;
    private final Map<UUID, MonitorState> monitorStates = new HashMap<>();
    private int currentWave;

    public ZombiesMobRecycleService(
            ModeEntityOwnershipRegistry ownershipRegistry,
            ZombiesMobLifecycleService lifecycleService,
            Supplier<List<ServerPlayer>> targetSupplier
    ) {
        this.ownershipRegistry = Objects.requireNonNull(ownershipRegistry, "ownershipRegistry");
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "lifecycleService");
        this.targetSupplier = targetSupplier == null ? List::of : targetSupplier;
    }

    public RecycleSummary tick(
            RoomId roomId,
            ServerLevel level,
            ZombiesWaveRuntimeState waveState,
            long roomTick
    ) {
        if (roomId == null || level == null || waveState == null || roomTick % SCAN_INTERVAL_TICKS != 0L) {
            return RecycleSummary.empty();
        }
        if (currentWave != waveState.currentWave()) {
            currentWave = waveState.currentWave();
            monitorStates.clear();
        }
        int requeued = 0;
        int discarded = 0;
        Set<UUID> activeIds = waveState.activeZombieEntityIdsSnapshot();
        monitorStates.keySet().removeIf(entityId -> !activeIds.contains(entityId));
        for (UUID entityId : activeIds) {
            Entity entity = level.getEntity(entityId);
            if (!(entity instanceof Mob mob) || mob.isRemoved() || !mob.isAlive()) {
                monitorStates.remove(entityId);
                continue;
            }
            if (!isOwnedByRoom(mob, roomId)) {
                monitorStates.remove(entityId);
                continue;
            }
            RecycleDecision decision = evaluate(mob, roomTick);
            if (!decision.recycle()) {
                continue;
            }
            String mobId = mob.getPersistentData().getString(ZombiesMobSpawnService.WAVE_MOB_ID_TAG);
            int nextRecycleCount = mob.getPersistentData().getInt(ZombiesMobSpawnService.WAVE_RECYCLE_COUNT_TAG) + 1;
            boolean requeue = nextRecycleCount <= MAX_REQUEUE_RECYCLES_PER_ENTITY;
            if (recycle(roomId, mob, waveState, mobId, nextRecycleCount, requeue)) {
                if (requeue) {
                    requeued++;
                } else {
                    discarded++;
                }
            }
        }
        return new RecycleSummary(requeued, discarded);
    }

    public void reset() {
        monitorStates.clear();
        currentWave = 0;
    }

    private RecycleDecision evaluate(Mob mob, long roomTick) {
        MonitorState state = monitorStates.computeIfAbsent(mob.getUUID(), ignored -> MonitorState.initial(mob, roomTick));
        Vec3 currentPos = mob.position();
        double movedDistance = currentPos.distanceTo(state.lastPos);
        if (movedDistance >= MIN_MOVED_DISTANCE) {
            state.lastMovedTick = roomTick;
            state.lastPos = currentPos;
        }

        LivingEntity target = validTarget(mob, mob.getTarget()) ? mob.getTarget() : null;
        if (target != null) {
            state.lastTargetTick = roomTick;
        }
        boolean noTargetTimedOut = target == null && roomTick - state.lastTargetTick >= NO_TARGET_RECYCLE_TICKS;
        boolean stuckTimedOut = target != null
                && mob.distanceTo(target) > STUCK_MIN_TARGET_DISTANCE
                && roomTick - state.lastMovedTick >= STUCK_RECYCLE_TICKS;
        if (!noTargetTimedOut && !stuckTimedOut) {
            return RecycleDecision.keep();
        }
        return new RecycleDecision(true);
    }

    private boolean recycle(
            RoomId roomId,
            Mob mob,
            ZombiesWaveRuntimeState waveState,
            String mobId,
            int nextRecycleCount,
            boolean requeue
    ) {
        ZombiesMobLifecycleService.LifecycleResult lifecycle =
                lifecycleService.onRecycledForRetry(roomId, mob, waveState);
        if (!lifecycle.unregistered()) {
            return false;
        }
        monitorStates.remove(mob.getUUID());
        if (requeue && mobId != null && !mobId.isBlank()) {
            waveState.requeueBudget(mobId, nextRecycleCount);
        }
        mob.discard();
        return true;
    }

    private boolean isOwnedByRoom(Mob mob, RoomId roomId) {
        return ownershipRegistry.entryOf(mob)
                .map(ModeEntityOwnershipRegistry.Entry::roomId)
                .map(entryRoom -> sameRoom(entryRoom, roomId))
                .orElse(false);
    }

    private boolean validTarget(Mob mob, LivingEntity target) {
        if (!(target instanceof ServerPlayer player) || !target.isAlive() || target.isRemoved() || player.isSpectator()) {
            return false;
        }
        if (!player.level().dimension().equals(mob.level().dimension())) {
            return false;
        }
        return safeTargets().stream()
                .filter(Objects::nonNull)
                .anyMatch(candidate -> candidate.getUUID().equals(player.getUUID()));
    }

    private List<ServerPlayer> safeTargets() {
        try {
            List<ServerPlayer> targets = targetSupplier.get();
            return targets == null ? List.of() : targets;
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private static boolean sameRoom(RoomId left, RoomId right) {
        return left != null
                && right != null
                && left.gameType().equalsIgnoreCase(right.gameType())
                && left.mapName().equals(right.mapName());
    }

    private static final class MonitorState {
        private Vec3 lastPos;
        private long lastMovedTick;
        private long lastTargetTick;

        private MonitorState(Vec3 lastPos, long lastMovedTick, long lastTargetTick) {
            this.lastPos = lastPos;
            this.lastMovedTick = lastMovedTick;
            this.lastTargetTick = lastTargetTick;
        }

        private static MonitorState initial(Mob mob, long roomTick) {
            return new MonitorState(mob.position(), roomTick, roomTick);
        }
    }

    private record RecycleDecision(boolean recycle) {
        private static RecycleDecision keep() {
            return new RecycleDecision(false);
        }
    }

    public record RecycleSummary(int requeued, int discarded) {
        public static RecycleSummary empty() {
            return new RecycleSummary(0, 0);
        }
    }
}
