package com.cdp.codpattern.app.match.runtime.debug;

import com.cdp.codpattern.app.match.extension.ModeDebugSnapshotContributor;
import com.cdp.codpattern.app.match.ModeModules;
import com.cdp.codpattern.app.match.model.ModeRuntimeStateSnapshot;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Read-only debug-detail route over the frozen mode catalog. */
public final class ModeDebugSnapshotContributors {
    private ModeDebugSnapshotContributors() {
    }

    public static List<String> lines(
            String gameType,
            ModeRuntimeStateSnapshot snapshot,
            List<ModeEntityOwnershipRegistry.Entry> entities
    ) {
        List<String> result = new ArrayList<>();
        for (ModeDebugSnapshotContributor contributor : ModeModules.catalog().debugSnapshotContributors()) {
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
