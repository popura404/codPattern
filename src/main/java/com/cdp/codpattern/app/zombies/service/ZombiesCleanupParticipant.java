package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.RoomId;

import java.util.Objects;

/**
 * Idempotent cleanup step; participants run by order and failure should stop later dependent cleanup.
 */
public interface ZombiesCleanupParticipant {
    default int order() {
        return 0;
    }

    ZombiesServiceResult<Void> cleanup(ZombiesCleanupContext context);

    record ZombiesCleanupContext(
            RoomId roomId,
            String reason,
            long cleanupRevision
    ) {
        public ZombiesCleanupContext {
            Objects.requireNonNull(roomId, "roomId");
            reason = Objects.requireNonNullElse(reason, "").trim();
            cleanupRevision = Math.max(0L, cleanupRevision);
        }
    }
}
