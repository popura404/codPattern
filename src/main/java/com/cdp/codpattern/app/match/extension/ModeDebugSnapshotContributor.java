package com.cdp.codpattern.app.match.extension;

import com.cdp.codpattern.app.match.model.ModeRuntimeStateSnapshot;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;

import java.util.List;

/** Mode-keyed debug detail projection appended after the shared runtime-state summary. */
public interface ModeDebugSnapshotContributor {
    String id();

    default int order() {
        return 0;
    }

    boolean supports(String gameType);

    List<String> lines(
            ModeRuntimeStateSnapshot snapshot,
            List<ModeEntityOwnershipRegistry.Entry> entities
    );
}
