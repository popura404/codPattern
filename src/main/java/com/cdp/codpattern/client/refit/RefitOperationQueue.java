package com.cdp.codpattern.client.refit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A session's commands are sent in order, with at most one awaiting a server result. */
public final class RefitOperationQueue<T> {
    private final Deque<T> pending = new ArrayDeque<>();
    private UUID sessionId;
    private T inFlight;
    private boolean closing;

    public boolean begin(UUID sessionId) {
        if (this.sessionId != null) {
            return false;
        }
        this.sessionId = Objects.requireNonNull(sessionId);
        return true;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public boolean matches(UUID sessionId) {
        return this.sessionId != null && this.sessionId.equals(sessionId);
    }

    public boolean isClosing() {
        return closing;
    }

    public boolean enqueue(T operation) {
        if (sessionId == null || closing) {
            return false;
        }
        pending.addLast(Objects.requireNonNull(operation));
        return true;
    }

    /** Appends final local edits and save after all operations already accepted from the UI. */
    public boolean close(List<T> finalOperations) {
        if (sessionId == null || closing) {
            return false;
        }
        List<T> finalCopy = List.copyOf(finalOperations);
        closing = true;
        pending.addAll(finalCopy);
        return true;
    }

    /** An in-flight operation must finish before cancellation; unsent edits are discarded. */
    public boolean cancel(T cancellation) {
        if (sessionId == null) {
            return false;
        }
        Objects.requireNonNull(cancellation);
        closing = true;
        pending.clear();
        pending.addLast(cancellation);
        return true;
    }

    public T next() {
        if (sessionId == null || inFlight != null) {
            return null;
        }
        inFlight = pending.pollFirst();
        return inFlight;
    }

    public boolean acknowledge(UUID sessionId) {
        if (!matches(sessionId) || inFlight == null) {
            return false;
        }
        inFlight = null;
        return true;
    }

    public boolean finish(UUID sessionId) {
        if (!matches(sessionId)) {
            return false;
        }
        reset();
        return true;
    }

    public void reset() {
        pending.clear();
        sessionId = null;
        inFlight = null;
        closing = false;
    }
}
