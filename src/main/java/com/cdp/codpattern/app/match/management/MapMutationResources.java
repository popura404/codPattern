package com.cdp.codpattern.app.match.management;

import java.util.Objects;
import java.util.function.Consumer;

/** Constructor resource hook; the journal is updated before a team is acquired. */
public final class MapMutationResources {
    private record Tracking(Consumer<String> recorder, java.util.Set<String> acquired) { }
    private static final ThreadLocal<Tracking> CURRENT = new ThreadLocal<>();

    private MapMutationResources() { }

    public static AutoCloseable track(Consumer<String> beforeCreate) {
        if (CURRENT.get() != null) throw new IllegalStateException("Nested map construction");
        CURRENT.set(new Tracking(Objects.requireNonNull(beforeCreate), new java.util.HashSet<>()));
        return () -> CURRENT.remove();
    }

    public static void beforeCreateScoreboardTeam(String name) {
        Tracking tracking = CURRENT.get();
        if (tracking != null) {
            tracking.recorder().accept(name);
            tracking.acquired().add(name);
        }
    }

    public static void reusedScoreboardTeam(String name) {
        Tracking tracking = CURRENT.get();
        if (tracking != null && !tracking.acquired().contains(name))
            throw new IllegalStateException("Scoreboard team already exists: " + name);
    }
}
