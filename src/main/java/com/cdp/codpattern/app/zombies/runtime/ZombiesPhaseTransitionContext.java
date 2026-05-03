package com.cdp.codpattern.app.zombies.runtime;

import com.cdp.codpattern.app.match.model.RoomId;

import java.util.Objects;

/**
 * Immutable phase hook context; phase names are stable zombies lifecycle keys.
 */
public record ZombiesPhaseTransitionContext(
        RoomId roomId,
        String previousPhase,
        String currentPhase,
        String nextPhase,
        long roomTick
) {
    public ZombiesPhaseTransitionContext {
        Objects.requireNonNull(roomId, "roomId");
        previousPhase = clean(previousPhase);
        currentPhase = clean(currentPhase);
        nextPhase = clean(nextPhase);
        roomTick = Math.max(0L, roomTick);
    }

    private static String clean(String value) {
        return Objects.requireNonNullElse(value, "").trim();
    }
}
