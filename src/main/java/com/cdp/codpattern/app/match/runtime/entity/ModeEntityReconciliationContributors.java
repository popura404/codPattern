package com.cdp.codpattern.app.match.runtime.entity;

import com.cdp.codpattern.app.match.extension.ModeEntityReconciliationContributor;
import com.cdp.codpattern.app.match.ModeModules;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;

/** Read-only reconciliation route over the frozen mode catalog. */
public final class ModeEntityReconciliationContributors {
    private ModeEntityReconciliationContributors() {
    }

    public static boolean onMissingEntity(ModeEntityOwnershipRegistry.Entry entry) {
        if (entry == null || entry.roomId() == null) {
            return false;
        }
        boolean handled = false;
        for (ModeEntityReconciliationContributor contributor
                : ModeModules.catalog().entityReconciliationContributors()) {
            if (contributor.supports(entry.roomId())) {
                contributor.onMissingEntity(entry);
                handled = true;
            }
        }
        return handled;
    }
}
