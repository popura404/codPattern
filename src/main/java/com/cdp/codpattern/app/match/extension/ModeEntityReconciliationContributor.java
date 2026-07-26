package com.cdp.codpattern.app.match.extension;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;

/** Mode-owned side effects applied when a tracked entity disappears outside a live room adapter. */
public interface ModeEntityReconciliationContributor {
    String id();

    default int order() {
        return 0;
    }

    boolean supports(RoomId roomId);

    void onMissingEntity(ModeEntityOwnershipRegistry.Entry entry);
}
