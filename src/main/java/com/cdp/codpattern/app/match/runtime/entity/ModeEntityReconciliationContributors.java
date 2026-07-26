package com.cdp.codpattern.app.match.runtime.entity;

import com.cdp.codpattern.app.match.extension.ModeEntityReconciliationContributor;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;

/** Combined-distribution holder installed by the composition shim. */
public final class ModeEntityReconciliationContributors {
    private static final ModeEntityReconciliationRouter ROUTER = new ModeEntityReconciliationRouter();

    private ModeEntityReconciliationContributors() {
    }

    public static void register(ModeEntityReconciliationContributor contributor) {
        ROUTER.register(contributor);
    }

    public static boolean onMissingEntity(ModeEntityOwnershipRegistry.Entry entry) {
        return ROUTER.onMissingEntity(entry);
    }
}
