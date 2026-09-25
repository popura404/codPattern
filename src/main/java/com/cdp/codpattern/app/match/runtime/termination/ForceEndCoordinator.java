package com.cdp.codpattern.app.match.runtime.termination;

import com.cdp.codpattern.app.match.model.result.ModeOperationResult;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Server-independent, resumable coordinator. Persist before non-repeatable settlement. */
public final class ForceEndCoordinator {
    public static final class State {
        public UUID generation = UUID.randomUUID();
        public UUID operationId;
        public boolean active;
        public boolean terminated;
        public boolean complete;
        public boolean stopped;
        public boolean settlementAttempted;
        public boolean settled;
        public boolean cleaned;
        public String actor = "server";
        public String reason = "ADMIN_FORCE_END";
        public Map<String, String> failures = new LinkedHashMap<>();
        public transient boolean executing;
    }

    public enum Outcome { COMPLETED, SETTLEMENT_FAILED, PENDING, IN_PROGRESS, STALE, NOTHING_TO_END }
    public record Report(Outcome outcome, UUID generation, UUID operationId, Map<String, String> failures) {
        public Report { failures = Map.copyOf(failures); }
    }

    @FunctionalInterface
    public interface SharedCleanup { void run() throws Exception; }
    @FunctionalInterface
    public interface Checkpoint { void save() throws Exception; }

    public Report execute(State state, ForceEndContext context, ModeForceEndHandler handler,
                          SharedCleanup entities, SharedCleanup players, Checkpoint checkpoint) {
        if (!state.generation.equals(context.generation())) return report(state, Outcome.STALE);
        if (state.executing) return report(state, Outcome.IN_PROGRESS);
        if (state.complete) return report(state, state.settled ? Outcome.COMPLETED : Outcome.SETTLEMENT_FAILED);
        state.executing = true;
        // This cannot be vetoed by any mode callback, or undone by a failed checkpoint.
        state.terminated = true;
        if (state.operationId == null) state.operationId = context.operationId();
        try {
            boolean durable = step(state, "persistence", checkpoint::save);
            if (!state.stopped) state.stopped = callback(state, "stop", () -> handler.stop(context));
            if (!state.settlementAttempted) {
                // Record the attempt BEFORE external effects. An uncertain attempt is never replayed.
                state.settlementAttempted = true;
                if (state.stopped && durable && step(state, "persistence", checkpoint::save)) {
                    state.settled = callback(state, "settlement", () -> handler.settle(context));
                } else {
                    state.failures.put("settlement", "Skipped: stopping or durable checkpoint incomplete");
                }
            }
            if (!state.cleaned) state.cleaned = callback(state, "cleanup", () -> handler.cleanup(context));
            boolean resources = step(state, "entities", entities::run);
            boolean recovery = step(state, "players", players::run);
            // Even a failed mode stage cannot skip either shared cleanup branch.
            state.complete = state.stopped && state.cleaned && resources && recovery;
            if (!step(state, "persistence", checkpoint::save)) state.complete = false;
            return report(state, state.complete
                    ? (state.settled ? Outcome.COMPLETED : Outcome.SETTLEMENT_FAILED) : Outcome.PENDING);
        } finally {
            state.executing = false;
        }
    }

    private boolean callback(State state, String key, Supplier<ModeOperationResult<Void>> callback) {
        return step(state, key, () -> {
            ModeOperationResult<Void> result = callback.get();
            if (result == null || !result.success()) {
                throw new IllegalStateException(result == null ? "Missing mode result" : result.code() + ": " + result.logMessage());
            }
        });
    }

    private boolean step(State state, String key, SharedCleanup action) {
        try {
            action.run();
            state.failures.remove(key);
            return true;
        } catch (Exception | LinkageError failure) {
            state.failures.put(key, failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage()));
            return false;
        }
    }

    public static Report report(State state, Outcome outcome) {
        return new Report(outcome, state.generation, state.operationId, state.failures);
    }
}
