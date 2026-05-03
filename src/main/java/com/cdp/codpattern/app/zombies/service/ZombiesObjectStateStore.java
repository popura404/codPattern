package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.ModeObjectState;
import com.cdp.codpattern.app.zombies.map.object.ZombiesBarrierData;
import com.cdp.codpattern.app.zombies.sync.ZombiesObjectStateKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ZombiesObjectStateStore {
    private static final String OBJECT_TYPE_BARRIER = "barrier";
    private static final String PAYLOAD_OBJECT_ID = "objectId";
    private static final String PAYLOAD_GROUP = "group";
    private static final String PAYLOAD_CLEARED = "cleared";

    private final Map<String, BarrierRuntimeState> barriersByObjectId = new LinkedHashMap<>();
    private long revision;

    public synchronized void resetBarriers(Collection<ZombiesBarrierData> barriers) {
        List<ZombiesBarrierData> snapshot = safeBarriers(barriers);
        Map<String, BarrierRuntimeState> next = new LinkedHashMap<>();
        for (ZombiesBarrierData barrier : snapshot) {
            String objectId = objectKey(barrier);
            next.put(objectId, new BarrierRuntimeState(barrier.group(), false, nextRevision()));
        }
        barriersByObjectId.clear();
        barriersByObjectId.putAll(next);
    }

    public synchronized ZombiesServiceResult<BarrierGroupUpdate> clearBarrierGroup(
            int group,
            Collection<ZombiesBarrierData> barriers
    ) {
        if (group < 1) {
            return ZombiesServiceResult.failure(ZombiesErrorCode.OBJECT_NOT_FOUND);
        }

        List<ZombiesBarrierData> groupBarriers = safeBarriers(barriers).stream()
                .filter(barrier -> barrier.group() == group)
                .toList();
        if (groupBarriers.isEmpty()) {
            return ZombiesServiceResult.failure(ZombiesErrorCode.OBJECT_NOT_FOUND);
        }

        boolean alreadyCleared = true;
        for (ZombiesBarrierData barrier : groupBarriers) {
            BarrierRuntimeState state = ensureBarrierState(barrier);
            if (!state.cleared()) {
                alreadyCleared = false;
                break;
            }
        }
        if (alreadyCleared) {
            return ZombiesServiceResult.failure(ZombiesErrorCode.of("barrier.already_cleared"));
        }

        long updateRevision = revision;
        List<String> objectIds = new ArrayList<>();
        for (ZombiesBarrierData barrier : groupBarriers) {
            String objectId = objectKey(barrier);
            objectIds.add(objectId);
            updateRevision = nextRevision();
            barriersByObjectId.put(objectId, new BarrierRuntimeState(group, true, updateRevision));
        }
        return ZombiesServiceResult.success(new BarrierGroupUpdate(group, List.copyOf(objectIds), updateRevision));
    }

    public synchronized boolean isBarrierCleared(ZombiesBarrierData barrier) {
        if (barrier == null) {
            return false;
        }
        return ensureBarrierState(barrier).cleared();
    }

    public synchronized List<ModeObjectState> barrierStates(Collection<ZombiesBarrierData> barriers) {
        List<ModeObjectState> states = new ArrayList<>();
        for (ZombiesBarrierData barrier : safeBarriers(barriers)) {
            String objectId = objectKey(barrier);
            BarrierRuntimeState state = ensureBarrierState(barrier);
            states.add(toModeObjectState(objectId, barrier, state));
        }
        return List.copyOf(states);
    }

    public synchronized long revision() {
        return revision;
    }

    private BarrierRuntimeState ensureBarrierState(ZombiesBarrierData barrier) {
        String objectId = objectKey(barrier);
        BarrierRuntimeState state = barriersByObjectId.get(objectId);
        if (state == null || state.group() != barrier.group()) {
            state = new BarrierRuntimeState(barrier.group(), false, nextRevision());
            barriersByObjectId.put(objectId, state);
        }
        return state;
    }

    private ModeObjectState toModeObjectState(
            String objectId,
            ZombiesBarrierData barrier,
            BarrierRuntimeState state
    ) {
        CompoundTag payload = new CompoundTag();
        payload.putString(PAYLOAD_OBJECT_ID, objectId);
        payload.putString(ZombiesObjectStateKeys.PAYLOAD_TYPE, OBJECT_TYPE_BARRIER);
        payload.putInt(PAYLOAD_GROUP, barrier.group());
        payload.putInt(ZombiesObjectStateKeys.PAYLOAD_COST, Math.max(0, barrier.cost()));
        payload.putBoolean(PAYLOAD_CLEARED, state.cleared());
        payload.putBoolean(ZombiesObjectStateKeys.PAYLOAD_ENABLED, !state.cleared());
        return new ModeObjectState(
                objectId,
                ZombiesObjectStateKeys.STATUS,
                barrier.interactionPos(),
                payload,
                state.revision());
    }

    private long nextRevision() {
        revision = revision == Long.MAX_VALUE ? 1L : revision + 1L;
        return revision;
    }

    private static List<ZombiesBarrierData> safeBarriers(Collection<ZombiesBarrierData> barriers) {
        if (barriers == null || barriers.isEmpty()) {
            return List.of();
        }
        return barriers.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    static String objectKey(ZombiesBarrierData barrier) {
        String objectId = barrier == null ? "" : Objects.requireNonNullElse(barrier.objectId(), "").trim();
        if (!objectId.isBlank()) {
            return objectId;
        }
        BlockPos pos = barrier == null || barrier.interactionPos() == null ? BlockPos.ZERO : barrier.interactionPos();
        int group = barrier == null ? 0 : barrier.group();
        return "barrier:" + group + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private record BarrierRuntimeState(
            int group,
            boolean cleared,
            long revision
    ) {
    }

    public record BarrierGroupUpdate(
            int group,
            List<String> objectIds,
            long revision
    ) {
        public BarrierGroupUpdate {
            objectIds = objectIds == null ? List.of() : List.copyOf(objectIds);
            revision = Math.max(0L, revision);
        }
    }
}
