package com.cdp.codpattern.app.match.runtime.player;

import com.cdp.codpattern.app.match.model.RoomId;

import java.util.Objects;
import java.util.Optional;

/** Neutral player recovery marker; persistence keys and payload codecs remain mode-owned. */
public record ModePlayerSessionMarker<T>(
        RoomId roomId,
        String state,
        Optional<T> recoveryTarget
) {
    public ModePlayerSessionMarker {
        Objects.requireNonNull(roomId, "roomId");
        state = Objects.requireNonNullElse(state, "").trim();
        if (state.isEmpty()) {
            throw new IllegalArgumentException("state must not be blank");
        }
        recoveryTarget = recoveryTarget == null ? Optional.empty() : recoveryTarget;
    }

    public boolean hasState(String expectedState) {
        return state.equals(Objects.requireNonNullElse(expectedState, "").trim());
    }
}
