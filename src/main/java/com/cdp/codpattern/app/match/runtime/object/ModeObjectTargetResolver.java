package com.cdp.codpattern.app.match.runtime.object;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/** Exact and nearest-within-range target selection using caller-owned positions and rules. */
public final class ModeObjectTargetResolver {
    private ModeObjectTargetResolver() {
    }

    public static <T, P> Optional<T> exact(
            Collection<T> candidates,
            Function<T, P> position,
            P expected
    ) {
        if (candidates == null || position == null) {
            return Optional.empty();
        }
        return candidates.stream()
                .filter(Objects::nonNull)
                .filter(candidate -> Objects.equals(expected, position.apply(candidate)))
                .findFirst();
    }

    public static <T> Optional<T> nearestWithin(
            Collection<T> candidates,
            Predicate<T> eligible,
            ToDoubleFunction<T> distanceSquared,
            double maxDistanceSquared
    ) {
        if (candidates == null || distanceSquared == null || maxDistanceSquared < 0.0D) {
            return Optional.empty();
        }
        Predicate<T> safeEligible = eligible == null ? candidate -> true : eligible;
        return candidates.stream()
                .filter(Objects::nonNull)
                .filter(safeEligible)
                .filter(candidate -> within(distanceSquared.applyAsDouble(candidate), maxDistanceSquared))
                .min((left, right) -> Double.compare(
                        distanceSquared.applyAsDouble(left),
                        distanceSquared.applyAsDouble(right)));
    }

    public static boolean within(double distanceSquared, double maxDistanceSquared) {
        return Double.isFinite(distanceSquared)
                && Double.isFinite(maxDistanceSquared)
                && distanceSquared <= maxDistanceSquared;
    }
}
