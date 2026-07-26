package com.cdp.codpattern.app.match.runtime.object;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Rejects duplicate actor/object interactions in the same logical tick. */
public final class ModeObjectInteractionDeduplicator {
    private final ConcurrentMap<InteractionKey, Long> recent = new ConcurrentHashMap<>();
    private final long retentionTicks;

    public ModeObjectInteractionDeduplicator(long retentionTicks) {
        this.retentionTicks = Math.max(0L, retentionTicks);
    }

    public boolean tryAcquire(UUID actorId, String objectId, long tick) {
        Objects.requireNonNull(actorId, "actorId");
        String normalizedObjectId = Objects.requireNonNullElse(objectId, "").trim();
        long normalizedTick = Math.max(0L, tick);
        cleanup(normalizedTick);
        return recent.putIfAbsent(new InteractionKey(actorId, normalizedObjectId, normalizedTick), normalizedTick) == null;
    }

    public void cleanup(long currentTick) {
        long cutoff = Math.max(0L, currentTick - retentionTicks);
        recent.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }

    public int size() {
        return recent.size();
    }

    private record InteractionKey(UUID actorId, String objectId, long tick) {
    }
}
