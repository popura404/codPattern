package com.cdp.codpattern.app.match.runtime.lifecycle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Runs ordered cleanup participants and a success-only finalizer. */
public final class CleanupCoordinator<C, F, S> {
    private final List<Participant<C, F>> participants;
    private final FailurePolicy<C, F> failurePolicy;
    private final Finalizer<C, S> finalizer;

    public CleanupCoordinator(
            Collection<? extends Participant<C, F>> participants,
            FailurePolicy<C, F> failurePolicy,
            Finalizer<C, S> finalizer
    ) {
        List<Participant<C, F>> ordered = new ArrayList<>();
        if (participants != null) {
            participants.stream().filter(Objects::nonNull).forEach(ordered::add);
        }
        ordered.sort(Comparator.comparingInt(Participant::order));
        this.participants = List.copyOf(ordered);
        this.failurePolicy = failurePolicy == null ? FailurePolicy.noop() : failurePolicy;
        this.finalizer = Objects.requireNonNull(finalizer, "finalizer");
    }

    public Result<F, S> execute(C context) {
        int completedParticipants = 0;
        for (Participant<C, F> participant : participants) {
            ParticipantResult<F> result = participant.cleanup(context);
            if (result != null && !result.success()) {
                F failure = result.failure().orElse(null);
                failurePolicy.onFailure(context, participant.name(), failure, completedParticipants);
                return Result.failed(participant.name(), failure, completedParticipants);
            }
            completedParticipants++;
        }
        return Result.completed(finalizer.finish(context), completedParticipants);
    }

    public interface Participant<C, F> {
        default String name() {
            return getClass().getName();
        }

        default int order() {
            return 0;
        }

        ParticipantResult<F> cleanup(C context);
    }

    @FunctionalInterface
    public interface FailurePolicy<C, F> {
        void onFailure(C context, String participantName, F failure, int completedParticipants);

        static <C, F> FailurePolicy<C, F> noop() {
            return (context, participantName, failure, completedParticipants) -> { };
        }
    }

    @FunctionalInterface
    public interface Finalizer<C, S> {
        S finish(C context);
    }

    public record ParticipantResult<F>(boolean success, Optional<F> failure) {
        public ParticipantResult {
            failure = failure == null ? Optional.empty() : failure;
            if (success && failure.isPresent()) {
                throw new IllegalArgumentException("successful participant result cannot contain a failure");
            }
        }

        public static <F> ParticipantResult<F> completed() {
            return new ParticipantResult<>(true, Optional.empty());
        }

        public static <F> ParticipantResult<F> failed(F failure) {
            return new ParticipantResult<>(false, Optional.ofNullable(failure));
        }
    }

    public record Result<F, S>(
            boolean success,
            int completedParticipants,
            Optional<String> failedParticipant,
            Optional<F> failure,
            Optional<S> summary
    ) {
        public Result {
            completedParticipants = Math.max(0, completedParticipants);
            failedParticipant = failedParticipant == null ? Optional.empty() : failedParticipant;
            failure = failure == null ? Optional.empty() : failure;
            summary = summary == null ? Optional.empty() : summary;
        }

        private static <F, S> Result<F, S> failed(String participantName, F failure, int completed) {
            return new Result<>(false, completed,
                    Optional.ofNullable(participantName), Optional.ofNullable(failure), Optional.empty());
        }

        private static <F, S> Result<F, S> completed(S summary, int completed) {
            return new Result<>(true, completed, Optional.empty(), Optional.empty(), Optional.ofNullable(summary));
        }
    }
}
