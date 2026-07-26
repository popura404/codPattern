package com.cdp.codpattern.app.match.runtime.debug;

import com.cdp.codpattern.app.match.extension.ModeDebugSnapshotContributor;
import com.cdp.codpattern.app.match.model.ModeRuntimeStateSnapshot;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Mode-keyed debug detail routing with deterministic contributor order. */
public final class ModeDebugSnapshotContributors {
    private static final List<ModeDebugSnapshotContributor> CONTRIBUTORS = new ArrayList<>();

    private ModeDebugSnapshotContributors() {
    }

    public static synchronized void register(ModeDebugSnapshotContributor contributor) {
        Objects.requireNonNull(contributor, "contributor");
        if (CONTRIBUTORS.stream().anyMatch(existing -> existing.id().equals(contributor.id()))) {
            throw new IllegalStateException("Duplicate debug snapshot contributor: " + contributor.id());
        }
        CONTRIBUTORS.add(contributor);
        CONTRIBUTORS.sort(Comparator.comparingInt(ModeDebugSnapshotContributor::order)
                .thenComparing(ModeDebugSnapshotContributor::id));
    }

    public static synchronized List<String> lines(
            String gameType,
            ModeRuntimeStateSnapshot snapshot,
            List<ModeEntityOwnershipRegistry.Entry> entities
    ) {
        List<String> result = new ArrayList<>();
        for (ModeDebugSnapshotContributor contributor : CONTRIBUTORS) {
            if (contributor.supports(gameType)) {
                List<String> lines = contributor.lines(snapshot, entities == null ? List.of() : entities);
                if (lines != null) {
                    lines.stream().filter(Objects::nonNull).forEach(result::add);
                }
            }
        }
        return List.copyOf(result);
    }
}
